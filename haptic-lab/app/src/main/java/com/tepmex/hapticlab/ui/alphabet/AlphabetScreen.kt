package com.tepmex.hapticlab.ui.alphabet

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
import androidx.compose.material3.OutlinedTextField
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
import com.tepmex.hapticlab.core.alphabet.Glyph
import com.tepmex.hapticlab.core.alphabet.TactileAlphabet
import com.tepmex.hapticlab.core.alphabet.demoAlphabet
import com.tepmex.hapticlab.core.alphabet.renderText
import com.tepmex.hapticlab.core.alphabet.resizeAlphabet
import com.tepmex.hapticlab.core.log.SessionRecorder
import com.tepmex.hapticlab.core.spatial.calibratedMmPerPx
import com.tepmex.hapticlab.haptics.FingerFrame
import com.tepmex.hapticlab.haptics.FingerSession
import com.tepmex.hapticlab.haptics.LocatedSample
import com.tepmex.hapticlab.ui.AppGraph
import com.tepmex.hapticlab.ui.components.FingerTrack
import com.tepmex.hapticlab.ui.components.FloatSlider
import com.tepmex.hapticlab.ui.components.IntSlider
import com.tepmex.hapticlab.ui.components.KeepScreenOn
import com.tepmex.hapticlab.ui.components.LevelBanner
import com.tepmex.hapticlab.ui.components.Metric
import com.tepmex.hapticlab.ui.components.StopButton
import com.tepmex.hapticlab.ui.graph
import com.tepmex.hapticlab.ui.labViewModel
import kotlinx.coroutines.delay

class AlphabetViewModel(private val graph: AppGraph) : ViewModel() {
    val session = FingerSession(graph.engine, SessionRecorder())
    var alphabet by mutableStateOf(graph.preferences.loadAlphabet())
    var selected by mutableStateOf(alphabet.glyphs.firstOrNull()?.symbol ?: "А")
    var text by mutableStateOf("АБВ")
    var gapZones by mutableIntStateOf(2)
    var zoneWidthMm by mutableStateOf(6f)
    var amplitude by mutableIntStateOf(220)
    var blind by mutableStateOf(false)
    var guides by mutableStateOf(true)
    var editMode by mutableStateOf(true)
    var frame by mutableStateOf(idle())
    var touching by mutableStateOf(false)
    var status by mutableStateOf("Демонстрационные знаки, не полный алфавит")
    private var lastUi = 0L

    val rendered get() = renderText(text.take(24), alphabet, gapZones)

    fun setZonesPerSymbol(count: Int) {
        alphabet = resizeAlphabet(alphabet, count)
        graph.preferences.saveAlphabet(alphabet)
    }

    fun toggleSelected(index: Int) {
        val glyph = alphabet.glyphs.firstOrNull { it.symbol == selected } ?: return
        if (index !in glyph.zones.indices) return
        val updated = glyph.copy(zones = glyph.zones.mapIndexed { i, on -> if (i == index) !on else on })
        alphabet = alphabet.copy(glyphs = alphabet.glyphs.map { if (it.symbol == selected) updated else it })
        graph.preferences.saveAlphabet(alphabet)
    }

    fun addSymbol(raw: String) {
        val symbol = raw.trim().take(1)
        if (symbol.isEmpty() || alphabet.glyphs.any { it.symbol == symbol }) return
        alphabet = alphabet.copy(
            glyphs = alphabet.glyphs + Glyph(symbol, List(alphabet.zonesPerSymbol) { false }),
        )
        selected = symbol
        graph.preferences.saveAlphabet(alphabet)
    }

    fun resetDemo() {
        alphabet = demoAlphabet(alphabet.zonesPerSymbol)
        selected = "А"
        graph.preferences.saveAlphabet(alphabet)
    }

    fun down(sample: LocatedSample) {
        if (editMode) return
        session.configure(0.4f, amplitude, 60_000L)
        session.recorder.verboseMotion = graph.verboseLogging
        touching = true
        publish(session.down(sample), true)
    }

    fun move(samples: List<LocatedSample>) {
        if (editMode || !touching) return
        var latest = frame
        samples.forEach { latest = session.move(it) }
        publish(latest, false)
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
        graph.engine.stop()
        session.machine.forceOff()
    }

    private fun publish(next: FingerFrame, force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (force || now - lastUi >= 50L || next.zone != frame.zone) {
            frame = next
            lastUi = now
        }
    }
}

@Composable
fun AlphabetScreen(onBack: () -> Unit) {
    val graph = graph()
    val vm = labViewModel { AlphabetViewModel(it) }
    val view = LocalView.current
    val mmPerPx = calibratedMmPerPx(view.resources.displayMetrics.xdpi, graph.calibrationScale)
    var newSymbol by androidx.compose.runtime.remember { mutableStateOf("") }
    val line = vm.rendered
    val editingGlyph = vm.editMode
    val shownZones = if (editingGlyph) {
        vm.alphabet.glyphs.firstOrNull { it.symbol == vm.selected }?.zones ?: emptyList()
    } else {
        line.zones
    }
    KeepScreenOn()
    DisposableEffect(Unit) { onDispose { vm.stopNow() } }
    LaunchedEffect(vm.touching) {
        while (vm.touching) {
            delay(200)
            vm.tick()
        }
    }
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onBack) { Text("Назад") }
                StopButton(onClick = vm::stopNow)
                Button(onClick = { vm.editMode = !vm.editMode }) {
                    Text(if (vm.editMode) "Чтение" else "Знак")
                }
            }
            Text("Пробная тактильная азбука", style = MaterialTheme.typography.headlineSmall)
            LevelBanner()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("Скорость", "${vm.frame.speedMmPerSec.toInt()} мм/с")
                if (!vm.blind) Metric("Команда", if (vm.frame.requestedOn) "ON" else "OFF")
            }
            Text(vm.status, style = MaterialTheme.typography.bodySmall)
            if (line.unknown.isNotEmpty() && !vm.editMode) {
                Text("Нет рисунка для: ${line.unknown.joinToString("")}")
            }
            FingerTrack(
                zones = shownZones.ifEmpty { listOf(false, false, false) },
                zoneWidthMm = vm.zoneWidthMm,
                mmPerPx = mmPerPx,
                blind = vm.blind && !vm.editMode,
                activeZone = if (vm.blind) null else vm.frame.zone,
                editMode = vm.editMode,
                markers = line.symbolStarts,
                showMarkers = vm.guides && !vm.editMode,
                onTapZone = vm::toggleSelected,
                onDown = vm::down,
                onMoves = vm::move,
                onUp = vm::up,
                modifier = Modifier.weight(1.3f).fillMaxWidth().padding(vertical = 8.dp),
            )
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Промежуток между знаками — место, где можно проверить физическую направляющую. Приложение механический бортик не создаёт. Движение назад читает ту же строку.")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    vm.alphabet.glyphs.forEach { glyph ->
                        Button(onClick = { vm.selected = glyph.symbol }) { Text(glyph.symbol) }
                    }
                }
                IntSlider("Зон на знак", vm.alphabet.zonesPerSymbol, 3..12) { vm.setZonesPerSymbol(it) }
                IntSlider("Промежуток, зон", vm.gapZones, 0..6) { vm.gapZones = it }
                FloatSlider("Ширина зоны", vm.zoneWidthMm, 2f..12f, suffix = " мм") { vm.zoneWidthMm = it }
                IntSlider("Амплитуда", vm.amplitude, 1..255) { vm.amplitude = it }
                OutlinedTextField(
                    value = vm.text,
                    onValueChange = { vm.text = it.take(24) },
                    label = { Text("Короткий текст") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Скрыть рисунок при чтении", modifier = Modifier.weight(1f))
                    Switch(checked = vm.blind, onCheckedChange = { vm.blind = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Границы знаков", modifier = Modifier.weight(1f))
                    Switch(checked = vm.guides, onCheckedChange = { vm.guides = it })
                }
                OutlinedTextField(
                    value = newSymbol,
                    onValueChange = { newSymbol = it.take(1) },
                    label = { Text("Новый знак") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.addSymbol(newSymbol); newSymbol = "" }) { Text("Добавить") }
                    Button(onClick = vm::resetDemo) { Text("Демонстрация") }
                }
            }
        }
    }
}

private fun idle() = FingerFrame(null, false, 0f, 0f, 0f, 0, 0, false, null, null, null, false)
