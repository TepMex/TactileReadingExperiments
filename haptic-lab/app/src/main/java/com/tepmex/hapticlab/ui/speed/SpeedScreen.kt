package com.hapticlab.ui.speed

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hapticlab.core.log.ExperimentSession
import com.hapticlab.core.log.LogEvent
import com.hapticlab.core.log.SessionRecorder
import com.hapticlab.core.spatial.alternatingPattern
import com.hapticlab.core.spatial.calibratedMmPerPx
import com.hapticlab.core.spatial.patternSignature
import com.hapticlab.haptics.FingerFrame
import com.hapticlab.haptics.FingerSession
import com.hapticlab.haptics.LocatedSample
import com.hapticlab.ui.AppGraph
import com.hapticlab.ui.activityContext
import com.hapticlab.ui.components.FingerTrack
import com.hapticlab.ui.components.IntSlider
import com.hapticlab.ui.components.KeepScreenOn
import com.hapticlab.ui.components.LevelBanner
import com.hapticlab.ui.components.Metric
import com.hapticlab.ui.components.StopButton
import com.hapticlab.ui.graph
import com.hapticlab.ui.labViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.Locale

class SpeedViewModel(private val graph: AppGraph) : ViewModel() {
    val session = FingerSession(graph.engine, SessionRecorder())
    var widthMm by mutableIntStateOf(4)
    var count by mutableIntStateOf(12)
    var period by mutableIntStateOf(2)
    var onInPeriod by mutableIntStateOf(1)
    var amplitude by mutableIntStateOf(220)
    var blind by mutableStateOf(false)
    var frame by mutableStateOf(idle())
    var touching by mutableStateOf(false)
    var askRating by mutableStateOf(false)
    var status by mutableStateOf("Проведите палец от одного края дорожки до другого")
    var history by mutableStateOf(graph.sessions.list().filter { it.type == "speed" })
    private var ratedThisPass = false
    private var lastUi = 0L

    val zones: List<Boolean>
        get() = alternatingPattern(count, period, onInPeriod.coerceIn(0, period))

    fun sync() {
        session.configure(debounceMm = 0f, amplitude = amplitude, touchBudgetMs = 30_000L)
        session.recorder.verboseMotion = graph.verboseLogging
    }

    fun down(sample: LocatedSample) {
        sync()
        touching = true
        ratedThisPass = false
        askRating = false
        publish(session.down(sample), true)
    }

    fun move(samples: List<LocatedSample>) {
        if (!touching) return
        var latest = frame
        samples.forEach { latest = session.move(it) }
        publish(latest, false)
        if (latest.passDone && !ratedThisPass) askRating = true
    }

    fun up() {
        if (!touching) return
        touching = false
        publish(session.up(SystemClock.elapsedRealtime(), SystemClock.elapsedRealtimeNanos()), true)
        graph.engine.stop()
    }

    fun tick() {
        if (!touching) return
        publish(session.tick(SystemClock.elapsedRealtime(), SystemClock.elapsedRealtimeNanos()), false)
    }

    fun stopNow() {
        touching = false
        askRating = false
        graph.engine.stop()
        session.machine.forceOff()
    }

    fun rate(score: Int) {
        ratedThisPass = true
        askRating = false
        val events = session.recorder.snapshot() + LogEvent(
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
            kind = SessionRecorder.KIND_RATING,
            detail = "quality=$score",
        )
        viewModelScope.launch {
            val inventory = withContext(Dispatchers.Default) { graph.refreshInventory() }
            val record = ExperimentSession(
                id = "speed_${System.currentTimeMillis()}",
                type = "speed",
                wallClockIso = Instant.now().toString(),
                startedElapsedRealtimeNanos = events.firstOrNull()?.elapsedRealtimeNanos ?: 0L,
                device = inventory.snapshot,
                parameters = mapOf(
                    "segment_mm" to widthMm.toString(),
                    "count" to count.toString(),
                    "period" to period.toString(),
                    "on_in_period" to onInPeriod.toString(),
                    "amplitude" to amplitude.toString(),
                    "pattern" to patternSignature(zones),
                    "calibration_scale" to graph.calibrationScale.toString(),
                ),
                events = events,
                summary = mapOf(
                    "rating" to score.toString(),
                    "mean_speed_mm_s" to frame.averageMmPerSec.toString(),
                    "max_speed_mm_s" to frame.maxMmPerSec.toString(),
                    "instant_speed_mm_s" to frame.speedMmPerSec.toString(),
                    "crossings" to frame.rawCrossings.toString(),
                    "transitions" to frame.transitions.toString(),
                    "duration_ms" to (frame.durationMs?.toString() ?: ""),
                    "requested_switch_hz" to (frame.switchHz?.toString() ?: ""),
                ),
            )
            withContext(Dispatchers.IO) { graph.sessions.save(record) }
            history = graph.sessions.list().filter { it.type == "speed" }
            status = "Оценка $score сохранена"
        }
    }

    private fun publish(next: FingerFrame, force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (force || now - lastUi >= 50L || next.passDone != frame.passDone) {
            frame = next
            lastUi = now
        }
    }
}

@Composable
fun SpeedScreen(onBack: () -> Unit) {
    val graph = graph()
    val vm = labViewModel { SpeedViewModel(it) }
    val view = LocalView.current
    val mmPerPx = calibratedMmPerPx(view.resources.displayMetrics.xdpi, graph.calibrationScale)
    KeepScreenOn()
    DisposableEffect(Unit) { onDispose { vm.stopNow() } }
    LaunchedEffect(vm.touching) {
        while (vm.touching) {
            delay(200)
            vm.tick()
        }
    }
    if (vm.askRating) {
        AlertDialog(
            onDismissRequest = { vm.askRating = false },
            title = { Text("Качество различения") },
            text = { Text("Насколько раздельно ощущались участки? 1 — слились, 5 — ясно различимы. Это ответ человека, не измерение мотора.") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..5).forEach { score ->
                        Button(onClick = { vm.rate(score) }) { Text(score.toString()) }
                    }
                }
            },
            dismissButton = { Button(onClick = { vm.askRating = false }) { Text("Позже") } },
        )
    }
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onBack) { Text("Назад") }
                StopButton(onClick = vm::stopNow)
            }
            Text("Скоростной тест", style = MaterialTheme.typography.headlineSmall)
            LevelBanner()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("Мгновенная", "${vm.frame.speedMmPerSec.toInt()} мм/с")
                Metric("Средняя", "${vm.frame.averageMmPerSec.toInt()} мм/с")
                Metric("Максимум", "${vm.frame.maxMmPerSec.toInt()} мм/с")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("Границы", vm.frame.rawCrossings.toString())
                Metric("Время", vm.frame.durationMs?.let { "$it мс" } ?: "—")
                Metric("Команды, Гц", vm.frame.switchHz?.let { String.format(Locale.US, "%.2f", it) } ?: "—")
            }
            Text(vm.status, style = MaterialTheme.typography.bodySmall)
            FingerTrack(
                zones = vm.zones,
                zoneWidthMm = vm.widthMm.toFloat(),
                mmPerPx = mmPerPx,
                blind = vm.blind,
                activeZone = if (vm.blind) null else vm.frame.zone,
                editMode = false,
                onDown = vm::down,
                onMoves = vm::move,
                onUp = vm::up,
                modifier = Modifier.weight(1.4f).fillMaxWidth().padding(vertical = 8.dp),
            )
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                IntSlider("Ширина участка, мм", vm.widthMm, 1..10) { vm.widthMm = it }
                IntSlider("Число участков", vm.count, 4..30) { vm.count = it }
                IntSlider("Период", vm.period, 2..8) {
                    vm.period = it
                    if (vm.onInPeriod > it) vm.onInPeriod = it
                }
                IntSlider("Включённых в периоде", vm.onInPeriod, 1..vm.period) { vm.onInPeriod = it }
                IntSlider("Амплитуда", vm.amplitude, 1..255) { vm.amplitude = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Скрыть рисунок", modifier = Modifier.weight(1f))
                    Switch(checked = vm.blind, onCheckedChange = { vm.blind = it })
                }
                Text("Частота ниже — это частота запрошенных переключений за проход, не частота мотора.")
                vm.history.take(5).forEach { item ->
                    Text(
                        "${item.wallClockIso.take(19)}  оценка ${item.summary["rating"] ?: "—"}  " +
                            "средняя ${item.summary["mean_speed_mm_s"]?.toFloatOrNull()?.toInt() ?: "—"} мм/с",
                    )
                }
            }
        }
    }
}

private fun idle() = FingerFrame(null, false, 0f, 0f, 0f, 0, 0, false, null, null, null, false)
