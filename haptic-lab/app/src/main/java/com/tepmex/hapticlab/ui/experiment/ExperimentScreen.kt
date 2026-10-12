package com.tepmex.hapticlab.ui.experiment

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tepmex.hapticlab.core.export.percentText
import com.tepmex.hapticlab.core.log.ExperimentSession
import com.tepmex.hapticlab.core.log.LogEvent
import com.tepmex.hapticlab.core.log.SessionRecorder
import com.tepmex.hapticlab.core.psych.AdaptiveStaircase
import com.tepmex.hapticlab.core.psych.STANDARD_GAPS_MS
import com.tepmex.hapticlab.core.psych.TrialAnswer
import com.tepmex.hapticlab.core.psych.TrialSpec
import com.tepmex.hapticlab.core.psych.buildConstantStimuliSchedule
import com.tepmex.hapticlab.core.psych.shouldInsertControl
import com.tepmex.hapticlab.core.psych.summarize
import com.tepmex.hapticlab.core.psych.summaryFields
import com.tepmex.hapticlab.haptics.DiscriminationKind
import com.tepmex.hapticlab.ui.AppGraph
import com.tepmex.hapticlab.ui.activityContext
import com.tepmex.hapticlab.ui.components.ChoiceRow
import com.tepmex.hapticlab.ui.components.IntSlider
import com.tepmex.hapticlab.ui.components.KeepScreenOn
import com.tepmex.hapticlab.ui.components.LevelBanner
import com.tepmex.hapticlab.ui.components.StopButton
import com.tepmex.hapticlab.ui.graph
import com.tepmex.hapticlab.ui.labViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import kotlin.random.Random

class ExperimentViewModel(private val graph: AppGraph) : ViewModel() {
    var onMs by mutableIntStateOf(20)
    var amplitude by mutableIntStateOf(200)
    var repetitions by mutableIntStateOf(4)
    var procedure by mutableStateOf("Постоянные")
    var stimulus by mutableStateOf("Waveform")
    var running by mutableStateOf(false)
    var canAnswer by mutableStateOf(false)
    var phase by mutableStateOf("Нажмите «Начать». Параметры текущей пробы скрыты.")
    var progress by mutableStateOf("")
    var summaryText by mutableStateOf<List<String>>(emptyList())
    var history by mutableStateOf(graph.sessions.list().filter { it.type == "psychophysics" })
    var lastRecord by mutableStateOf<ExperimentSession?>(null)
    private var generation = 0
    private var queue = ArrayDeque<TrialSpec>()
    private var staircase: AdaptiveStaircase? = null
    private var random = Random(1)
    private var seed = 1L
    private val answers = ArrayList<TrialAnswer>()
    private val recorder = SessionRecorder(verboseMotion = false)
    private var current: TrialSpec? = null
    private var adaptiveTrials = 0

    fun start() {
        generation += 1
        answers.clear()
        recorder.clear()
        seed = System.nanoTime()
        random = Random(seed)
        running = true
        canAnswer = false
        summaryText = emptyList()
        graph.refreshInventory()
        if (procedure == "Адаптивный") {
            queue.clear()
            staircase = AdaptiveStaircase(STANDARD_GAPS_MS)
            adaptiveTrials = 0
            progress = "Адаптивный подбор"
        } else {
            staircase = null
            queue = ArrayDeque(buildConstantStimuliSchedule(repetitions = repetitions, random = random))
            progress = "Проба 0 из ${queue.size}"
        }
        presentNext(generation)
    }

    fun stop() {
        generation += 1
        running = false
        canAnswer = false
        graph.engine.stop()
        phase = "Остановлено"
    }

    fun answer(two: Boolean) {
        if (!canAnswer) return
        val spec = current ?: return
        canAnswer = false
        val trialAnswer = TrialAnswer(spec, respondedTwo = two)
        answers += trialAnswer
        recorder.log(
            LogEvent(
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
                kind = SessionRecorder.KIND_ANSWER,
                detail = "gap=${spec.gapMs ?: "control"};response=${if (two) "two" else "one"};correct=${trialAnswer.correct}",
            ),
        )
        if (!spec.isControl) staircase?.onSignalAnswer(trialAnswer.correct)
        val gen = generation
        viewModelScope.launch {
            phase = "Пауза"
            delay(700)
            if (gen == generation) presentNext(gen)
        }
    }

    private fun presentNext(gen: Int) {
        val spec = nextSpec()
        if (spec == null) {
            finish()
            return
        }
        current = spec
        val kind = when (stimulus) {
            "Примитив" -> DiscriminationKind.PRIMITIVE
            "Конверт" -> DiscriminationKind.ENVELOPE
            else -> DiscriminationKind.WAVEFORM
        }
        recorder.log(
            LogEvent(
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
                kind = SessionRecorder.KIND_TRIAL,
                detail = "index=${spec.index};gap=${spec.gapMs ?: "control"};kind=$stimulus",
            ),
        )
        val result = graph.engine.playDiscrimination(
            kind = kind,
            onMs = onMs.toLong(),
            gapMs = spec.gapMs?.toLong(),
            amplitude = amplitude,
            inventory = graph.inventory,
        )
        recorder.log(
            LogEvent(
                elapsedRealtimeNanos = result.commandedAtElapsedRealtimeNanos,
                kind = SessionRecorder.KIND_VIBRATION_COMMAND,
                requestedVibrationOn = result.accepted,
                detail = result.message,
            ),
        )
        if (!result.accepted) {
            graph.engine.stop()
            running = false
            canAnswer = false
            if (answers.isEmpty()) {
                phase = result.message
            } else {
                val failure = result.message
                finish()
                phase = "$failure Частичная сессия сохранена."
            }
            return
        }
        phase = "Слушайте"
        progress = if (staircase != null) {
            "Проба ${answers.size + 1}, разворотов ${staircase?.reversals?.size ?: 0}"
        } else {
            "Проба ${answers.size + 1} из ${answers.size + queue.size + 1}"
        }
        viewModelScope.launch {
            delay(result.commandedDurationMs + 250L)
            if (gen == generation && running) {
                phase = "Один импульс или два?"
                canAnswer = true
            }
        }
    }

    private fun nextSpec(): TrialSpec? {
        val stair = staircase
        if (stair != null) {
            if (stair.reversals.size >= 8 || adaptiveTrials >= 48) return null
            adaptiveTrials += 1
            val control = shouldInsertControl(random)
            return TrialSpec(
                index = answers.size,
                gapMs = if (control) null else stair.currentGapMs,
                isControl = control,
            )
        }
        return queue.removeFirstOrNull()
    }

    private fun finish() {
        running = false
        canAnswer = false
        graph.engine.stop()
        val summary = summarize(answers)
        val threshold = staircase?.thresholdMs()
        summaryText = summary.rows.map { row ->
            val estimate = row.estimate
            val interval = if (estimate == null) "" else {
                "  ДИ ${percentText(estimate.lower95)}–${percentText(estimate.upper95)}"
            }
            "${row.label}: ${row.correct}/${row.trials} (${percentText(estimate?.proportion)})$interval"
        } + "Ложные «Два» в контроле: ${summary.controlFalseTwos}/${summary.controlTrials} (${percentText(summary.falseTwoRate?.proportion)})" +
            (threshold?.let { "Порог лестницы, среднее разворотов: ${"%.1f".format(it)} мс. Это поведенческая оценка." } ?: "")
        phase = "Сессия завершена"
        val record = ExperimentSession(
            id = "psych_${System.currentTimeMillis()}",
            type = "psychophysics",
            wallClockIso = Instant.now().toString(),
            startedElapsedRealtimeNanos = recorder.snapshot().firstOrNull()?.elapsedRealtimeNanos ?: SystemClock.elapsedRealtimeNanos(),
            device = graph.inventory.snapshot,
            parameters = mapOf(
                "procedure" to procedure,
                "stimulus" to stimulus,
                "on_ms" to onMs.toString(),
                "amplitude" to amplitude.toString(),
                "repetitions" to repetitions.toString(),
                "seed" to seed.toString(),
                "gaps" to STANDARD_GAPS_MS.joinToString(","),
            ),
            events = recorder.snapshot(),
            summary = summaryFields(summary, threshold),
        )
        viewModelScope.launch {
            withContext(Dispatchers.IO) { graph.sessions.save(record) }
            lastRecord = record
            history = withContext(Dispatchers.IO) {
                graph.sessions.list().filter { it.type == "psychophysics" }
            }
        }
    }
}

@Composable
fun ExperimentScreen(onBack: () -> Unit) {
    val graph = graph()
    val vm = labViewModel { ExperimentViewModel(it) }
    val context = activityContext()
    KeepScreenOn()
    DisposableEffect(Unit) { onDispose { vm.stop() } }
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onBack) { Text("Назад") }
                StopButton(onClick = vm::stop)
            }
            Text("Психофизический эксперимент", style = MaterialTheme.typography.headlineSmall)
            LevelBanner()
            Text(vm.phase, style = MaterialTheme.typography.titleMedium)
            Text(vm.progress)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { vm.answer(false) },
                    enabled = vm.canAnswer,
                    modifier = Modifier.weight(1f).heightIn(min = 72.dp),
                ) { Text("Один", style = MaterialTheme.typography.titleMedium) }
                Button(
                    onClick = { vm.answer(true) },
                    enabled = vm.canAnswer,
                    modifier = Modifier.weight(1f).heightIn(min = 72.dp),
                ) { Text("Два", style = MaterialTheme.typography.titleMedium) }
            }
            Button(
                onClick = vm::start,
                enabled = !vm.running,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text("Начать") }
            Text("Настройки действуют на следующую пробу. Интервал текущей пробы не показывается.", style = MaterialTheme.typography.bodySmall)
            ChoiceRow(listOf("Постоянные", "Адаптивный"), vm.procedure) { if (!vm.running) vm.procedure = it }
            ChoiceRow(listOf("Waveform", "Примитив", "Конверт"), vm.stimulus) { if (!vm.running) vm.stimulus = it }
            IntSlider("Длительность импульса, мс", vm.onMs, 5..200, enabled = !vm.running) { vm.onMs = it }
            IntSlider("Амплитуда", vm.amplitude, 1..255) { vm.amplitude = it }
            IntSlider("Повторы каждого интервала", vm.repetitions, 1..12, enabled = !vm.running) { vm.repetitions = it }
            Text("Интервалы постоянной процедуры: ${STANDARD_GAPS_MS.joinToString(", ")} мс. Столько же контрольных проб с одним импульсом. Адаптивная лестница: 2 верных подряд уменьшают интервал, ошибка увеличивает. Порог — среднее последних разворотов.")
            if (vm.summaryText.isNotEmpty()) {
                Text("Итог ответов", style = MaterialTheme.typography.titleMedium)
                vm.summaryText.forEach { Text(it) }
                vm.lastRecord?.let { record ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val file = graph.sessions.export(record).first
                            com.tepmex.hapticlab.data.shareFile(context, file, "text/csv")
                        }) { Text("CSV") }
                        Button(onClick = {
                            val file = graph.sessions.export(record).second
                            com.tepmex.hapticlab.data.shareFile(context, file, "application/json")
                        }) { Text("JSON") }
                    }
                }
            }
            Text("Сравнение сессий", style = MaterialTheme.typography.titleMedium)
            vm.history.take(4).forEach { session ->
                Text(session.wallClockIso.take(19) + "  " + session.parameters["stimulus"].orEmpty(), style = MaterialTheme.typography.labelLarge)
                STANDARD_GAPS_MS.forEach { gap ->
                    val proportion = session.summary["gap_${gap}_proportion"]?.toDoubleOrNull()
                    Text("$gap мс: ${percentText(proportion)}")
                }
                Text("Ложные «Два»: ${percentText(session.summary["false_two_rate"]?.toDoubleOrNull())}")
                session.summary["adaptive_threshold_ms"]?.let { Text("Порог лестницы: $it мс") }
            }
        }
    }
}
