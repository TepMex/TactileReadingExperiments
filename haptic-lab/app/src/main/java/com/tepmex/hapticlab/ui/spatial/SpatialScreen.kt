package com.tepmex.hapticlab.ui.spatial

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.tepmex.hapticlab.core.log.ExperimentSession
import com.tepmex.hapticlab.core.log.SessionRecorder
import com.tepmex.hapticlab.core.spatial.PatternPreset
import com.tepmex.hapticlab.core.spatial.calibratedMmPerPx
import com.tepmex.hapticlab.core.spatial.patternSignature
import com.tepmex.hapticlab.core.spatial.presetPattern
import com.tepmex.hapticlab.data.NamedPattern
import com.tepmex.hapticlab.data.shareFile
import com.tepmex.hapticlab.haptics.FingerFrame
import com.tepmex.hapticlab.haptics.FingerSession
import com.tepmex.hapticlab.haptics.LocatedSample
import com.tepmex.hapticlab.ui.AppGraph
import com.tepmex.hapticlab.ui.activityContext
import com.tepmex.hapticlab.ui.components.FingerTrack
import com.tepmex.hapticlab.ui.components.FloatSlider
import com.tepmex.hapticlab.ui.components.IntSlider
import com.tepmex.hapticlab.ui.components.KeepScreenOn
import com.tepmex.hapticlab.ui.components.LevelBanner
import com.tepmex.hapticlab.ui.components.Metric
import com.tepmex.hapticlab.ui.components.StopButton
import com.tepmex.hapticlab.ui.graph
import com.tepmex.hapticlab.ui.labViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

class SpatialViewModel(private val graph: AppGraph) : ViewModel() {
    val session = FingerSession(graph.engine, SessionRecorder())
    var zoneCount by mutableIntStateOf(10)
    var zoneWidthMm by mutableStateOf(8f)
    var debounceMm by mutableStateOf(0.5f)
    var amplitude by mutableIntStateOf(220)
    var budgetSec by mutableIntStateOf(60)
    var blind by mutableStateOf(false)
    var editMode by mutableStateOf(false)
    var zones by mutableStateOf(presetPattern(PatternPreset.THREE_PULSES, 10))
    var frame by mutableStateOf(idleFrame())
    var status by mutableStateOf("Проведите пальцем по дорожке")
    var touching by mutableStateOf(false)
    var savedPatterns by mutableStateOf(graph.preferences.loadPatterns())
    private var lastUiMs = 0L

    init {
        sync()
    }

    fun sync() {
        session.configure(if (editMode) 0f else debounceMm, amplitude, budgetSec * 1000L)
        session.recorder.verboseMotion = graph.verboseLogging
    }

    fun setCount(count: Int) {
        zoneCount = count
        zones = List(count) { index -> zones.getOrNull(index) == true }
    }

    fun applyPreset(preset: PatternPreset) {
        zones = presetPattern(preset, zoneCount)
    }

    fun toggle(index: Int) {
        zones = zones.mapIndexed { i, on -> if (i == index) !on else on }
    }

    fun down(sample: LocatedSample) {
        if (editMode) return
        sync()
        touching = true
        publish(session.down(sample), force = true)
    }

    fun move(samples: List<LocatedSample>) {
        if (editMode || !touching) return
        var latest = frame
        samples.forEach { latest = session.move(it) }
        publish(latest, force = false)
    }

    fun up() {
        if (!touching) return
        touching = false
        val frame = session.up(SystemClock.elapsedRealtime(), SystemClock.elapsedRealtimeNanos())
        publish(frame, force = true)
        graph.engine.stop()
    }

    fun tick() {
        if (!touching) return
        publish(session.tick(SystemClock.elapsedRealtime(), SystemClock.elapsedRealtimeNanos()), force = false)
    }

    fun stopNow() {
        touching = false
        graph.engine.stop()
        session.machine.forceOff()
        status = "Остановка запрошена"
    }

    fun savePattern() {
        val named = NamedPattern("рисунок ${savedPatterns.size + 1}", zones)
        savedPatterns = savedPatterns + named
        graph.preferences.savePatterns(savedPatterns)
        status = "Рисунок сохранён"
    }

    fun loadPattern(pattern: NamedPattern) {
        zones = pattern.zones
        zoneCount = pattern.zones.size.coerceIn(3, 20)
    }

    fun saveSession() {
        viewModelScope.launch {
            val events = session.recorder.snapshot()
            val inventory = withContext(Dispatchers.Default) { graph.refreshInventory() }
            val sessionRecord = ExperimentSession(
                id = "spatial_${System.currentTimeMillis()}",
                type = "spatial",
                wallClockIso = Instant.now().toString(),
                startedElapsedRealtimeNanos = events.firstOrNull()?.elapsedRealtimeNanos ?: SystemClock.elapsedRealtimeNanos(),
                device = inventory.snapshot,
                parameters = mapOf(
                    "zones" to zoneCount.toString(),
                    "zone_width_mm" to zoneWidthMm.toString(),
                    "debounce_mm" to debounceMm.toString(),
                    "amplitude" to amplitude.toString(),
                    "pattern" to patternSignature(zones),
                    "calibration_scale" to graph.calibrationScale.toString(),
                    "verbose" to graph.verboseLogging.toString(),
                    "dropped_motion" to session.recorder.droppedMotionCount().toString(),
                ),
                events = events,
                summary = mapOf(
                    "crossings" to frame.rawCrossings.toString(),
                    "transitions" to frame.transitions.toString(),
                    "max_speed_mm_s" to frame.maxMmPerSec.toString(),
                ),
            )
            withContext(Dispatchers.IO) { graph.sessions.save(sessionRecord) }
            status = "Сессия ${sessionRecord.id} сохранена. Событий: ${events.size}."
            lastRecord = sessionRecord
        }
    }

    var lastRecord by mutableStateOf<ExperimentSession?>(null)

    private fun publish(next: FingerFrame, force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        val changed = next.zone != frame.zone || next.requestedOn != frame.requestedOn || next.budgetExhausted != frame.budgetExhausted
        if (force || changed || now - lastUiMs >= 50L) {
            frame = next
            lastUiMs = now
            status = when {
                next.budgetExhausted -> "Достигнут предел непрерывной вибрации. Оторвите палец."
                next.message != null -> next.message
                else -> "Внутри зоны ON мотор не перезапускается. Удержание — конечная команда около 2 с, затем одна команда обновления."
            }
        }
    }
}

private fun idleFrame() = FingerFrame(
    zone = null,
    requestedOn = false,
    speedMmPerSec = 0f,
    averageMmPerSec = 0f,
    maxMmPerSec = 0f,
    rawCrossings = 0,
    transitions = 0,
    passDone = false,
    durationMs = null,
    switchHz = null,
    message = null,
    budgetExhausted = false,
)

@Composable
fun SpatialScreen(onBack: () -> Unit) {
    val graph = graph()
    val vm = labViewModel { SpatialViewModel(it) }
    val view = LocalView.current
    val mmPerPx = calibratedMmPerPx(view.resources.displayMetrics.xdpi, graph.calibrationScale)
    val context = activityContext()
    KeepScreenOn()
    DisposableEffect(Unit) {
        onDispose { vm.stopNow() }
    }
    LaunchedEffect(vm.touching) {
        while (vm.touching) {
            delay(200)
            vm.tick()
        }
    }
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onBack) { Text("Назад") }
                StopButton(onClick = vm::stopNow)
                Button(onClick = { vm.editMode = !vm.editMode }) {
                    Text(if (vm.editMode) "Чтение" else "Правка")
                }
            }
            Text("Пространственные рисунки", style = MaterialTheme.typography.headlineSmall)
            LevelBanner()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("Скорость", speedText(vm.frame.speedMmPerSec))
                Metric("Макс.", speedText(vm.frame.maxMmPerSec))
                if (!vm.blind) {
                    Metric("Зона", vm.frame.zone?.plus(1)?.toString() ?: "—")
                    Metric("Команда", if (vm.frame.requestedOn) "ON" else "OFF")
                }
            }
            if (!vm.blind) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Metric("Границы после фильтра", vm.frame.rawCrossings.toString())
                    Metric("Переходы команд", vm.frame.transitions.toString())
                }
            }
            Text(vm.status, style = MaterialTheme.typography.bodySmall)
            FingerTrack(
                zones = vm.zones,
                zoneWidthMm = vm.zoneWidthMm,
                mmPerPx = mmPerPx,
                blind = vm.blind,
                activeZone = if (vm.blind) null else vm.frame.zone,
                editMode = vm.editMode,
                onTapZone = vm::toggle,
                onDown = vm::down,
                onMoves = vm::move,
                onUp = vm::up,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp),
            )
            Column(
                modifier = Modifier
                    .weight(0.9f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IntSlider("Зоны", vm.zoneCount, 3..20) { vm.setCount(it) }
                FloatSlider("Ширина зоны", vm.zoneWidthMm, 2f..20f, suffix = " мм") {
                    vm.zoneWidthMm = it
                }
                FloatSlider("Фильтр границы", vm.debounceMm, 0f..3f, suffix = " мм") {
                    vm.debounceMm = it
                    vm.sync()
                }
                Text(if (vm.debounceMm <= 0.01f) "Фильтрация выключена" else "Палец должен войти в новую зону на заданную глубину")
                IntSlider("Амплитуда", vm.amplitude, 1..255) { vm.amplitude = it }
                IntSlider("Предел удержания, с", vm.budgetSec, 5..120) { vm.budgetSec = it }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Без визуальных подсказок", modifier = Modifier.weight(1f))
                    Switch(checked = vm.blind, onCheckedChange = { vm.blind = it })
                }
                Text("Рисунок ${patternSignature(vm.zones)}", style = MaterialTheme.typography.bodyMedium)
                PatternPreset.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { preset ->
                            Button(onClick = { vm.applyPreset(preset) }, modifier = Modifier.weight(1f)) {
                                Text(preset.title)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::savePattern) { Text("Сохранить рисунок") }
                    Button(onClick = vm::saveSession) { Text("Сохранить проход") }
                }
                vm.savedPatterns.takeLast(6).forEach { pattern ->
                    Button(onClick = { vm.loadPattern(pattern) }, modifier = Modifier.fillMaxWidth()) {
                        Text("${pattern.name}: ${patternSignature(pattern.zones)}")
                    }
                }
                vm.lastRecord?.let { record ->
                    Button(
                        onClick = {
                            val file = graph.sessions.export(record).second
                            shareFile(context, file, "application/json")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Экспорт JSON") }
                    Button(
                        onClick = {
                            val file = graph.sessions.export(record).first
                            shareFile(context, file, "text/csv")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Экспорт CSV") }
                }
            }
        }
    }
}

private fun speedText(value: Float): String = "${value.toInt()} мм/с"
