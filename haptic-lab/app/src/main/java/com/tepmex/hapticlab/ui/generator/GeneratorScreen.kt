package com.tepmex.hapticlab.ui.generator

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tepmex.hapticlab.core.signal.SignalRequestException
import com.tepmex.hapticlab.core.signal.WaveformSpec
import com.tepmex.hapticlab.core.signal.buildPulseTrain
import com.tepmex.hapticlab.ui.components.ChoiceRow
import com.tepmex.hapticlab.ui.components.CommandDiagram
import com.tepmex.hapticlab.ui.components.IntSlider
import com.tepmex.hapticlab.ui.components.KeepScreenOn
import com.tepmex.hapticlab.ui.components.LevelBanner
import com.tepmex.hapticlab.ui.components.StopButton
import com.tepmex.hapticlab.ui.graph

private val presets = listOf(
    "20/20" to (20 to 20),
    "20/10" to (20 to 10),
    "10/10" to (10 to 10),
    "50/20" to (50 to 20),
    "100/50" to (100 to 50),
)

@Composable
fun GeneratorScreen(onBack: () -> Unit) {
    val graph = graph()
    val inventory = graph.inventory
    var onMs by remember { mutableIntStateOf(20) }
    var offMs by remember { mutableIntStateOf(20) }
    var pulses by remember { mutableIntStateOf(4) }
    var amplitude by remember { mutableIntStateOf(180) }
    var repetitions by remember { mutableIntStateOf(1) }
    var mode by remember { mutableStateOf("Waveform") }
    var effectName by remember { mutableStateOf(inventory.effects.firstOrNull()?.name ?: "CLICK") }
    var primitiveName by remember { mutableStateOf(inventory.primitives.firstOrNull()?.name ?: "CLICK") }
    var message by remember { mutableStateOf("Готово") }
    val waveform = remember(onMs, offMs, pulses, amplitude, repetitions) {
        try {
            buildPulseTrain(onMs.toLong(), offMs.toLong(), pulses, amplitude, repetitions)
        } catch (error: SignalRequestException) {
            null
        }
    }
    KeepScreenOn()
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
                StopButton(onClick = {
                    graph.engine.stop()
                    message = "Остановка запрошена"
                })
            }
            Text("Генератор вибраций", style = MaterialTheme.typography.headlineSmall)
            LevelBanner()
            Text(message)
            ChoiceRow(
                options = listOf("OneShot", "Waveform", "Эффект", "Примитив", "Конверт", "Частота"),
                selected = mode,
                onSelect = { mode = it },
            )
            IntSlider("Включение, мс", onMs, 5..500) { onMs = it }
            IntSlider("Пауза, мс", offMs, 5..500) { offMs = it }
            IntSlider("Импульсы", pulses, 1..20) { pulses = it }
            IntSlider("Амплитуда", amplitude, 1..255) { amplitude = it }
            IntSlider("Повторения", repetitions, 1..20) { repetitions = it }
            if (!inventory.hasAmplitudeControl) {
                Text("Устройство не сообщает управление амплитудой. Ненулевые уровни уйдут как амплитуда по умолчанию.")
            }
            Text("Готовые наборы", style = MaterialTheme.typography.titleMedium)
            presets.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { (title, pair) ->
                        Button(
                            onClick = {
                                onMs = pair.first
                                offMs = pair.second
                                mode = "Waveform"
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text(title) }
                    }
                }
            }
            if (mode == "Эффект") {
                ChoiceRow(inventory.effects.map { it.name }, effectName) { effectName = it }
                Text(inventory.effects.firstOrNull { it.name == effectName }?.support.orEmpty())
            }
            if (mode == "Примитив") {
                ChoiceRow(inventory.primitives.map { it.name }, primitiveName) { primitiveName = it }
                Text(inventory.primitives.firstOrNull { it.name == primitiveName }?.support.orEmpty())
            }
            CommandDiagram(if (mode == "OneShot") oneShotDiagram(onMs, amplitude) else waveform)
            Button(
                onClick = {
                    message = play(
                        mode = mode,
                        onMs = onMs,
                        offMs = offMs,
                        pulses = pulses,
                        amplitude = amplitude,
                        repetitions = repetitions,
                        effectName = effectName,
                        primitiveName = primitiveName,
                        waveform = waveform,
                        graph = graph,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Пуск") }
        }
    }
}

private fun oneShotDiagram(onMs: Int, amplitude: Int) = WaveformSpec(
    timingsMs = listOf(onMs.toLong()),
    amplitudes = listOf(amplitude),
    totalMs = onMs.toLong(),
    truncated = false,
)

private fun play(
    mode: String,
    onMs: Int,
    offMs: Int,
    pulses: Int,
    amplitude: Int,
    repetitions: Int,
    effectName: String,
    primitiveName: String,
    waveform: WaveformSpec?,
    graph: com.tepmex.hapticlab.ui.AppGraph,
): String {
    graph.refreshInventory()
    val inventory = graph.inventory
    val result = when (mode) {
        "OneShot" -> graph.engine.playOneShot(onMs.toLong(), amplitude)
        "Waveform" -> waveform?.let { graph.engine.playWaveform(it) }
            ?: return "Сигнал не собран"
        "Эффект" -> {
            val effect = inventory.effects.firstOrNull { it.name == effectName }
                ?: return "Эффект не выбран"
            graph.engine.playPredefined(effect.id, effect.name)
        }
        "Примитив" -> {
            val primitive = inventory.primitives.firstOrNull { it.name == primitiveName }
                ?: return "Примитив не выбран"
            graph.engine.playPrimitiveTrain(primitive.id, primitive.name, pulses * repetitions, offMs, amplitude)
        }
        "Конверт" -> graph.engine.playBasicEnvelope(onMs.toLong(), offMs.toLong(), pulses * repetitions, amplitude, inventory)
        "Частота" -> graph.engine.playWaveformEnvelope(onMs.toLong(), offMs.toLong(), amplitude, inventory)
        else -> return "Неизвестный режим"
    }
    return result.message
}
