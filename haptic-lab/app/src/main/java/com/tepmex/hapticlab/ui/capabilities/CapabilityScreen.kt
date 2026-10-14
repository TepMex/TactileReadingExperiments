package com.tepmex.hapticlab.ui.capabilities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tepmex.hapticlab.core.haptics.CapabilityIds
import com.tepmex.hapticlab.core.haptics.CapabilityProbe
import com.tepmex.hapticlab.core.haptics.ProbeItem
import com.tepmex.hapticlab.core.haptics.ProbeOutcome
import com.tepmex.hapticlab.core.haptics.titleRu
import com.tepmex.hapticlab.core.signal.amplitudeContrastProbe
import com.tepmex.hapticlab.core.signal.timedPulseProbe
import com.tepmex.hapticlab.haptics.HapticInventory
import com.tepmex.hapticlab.ui.AppGraph
import com.tepmex.hapticlab.ui.components.CommandDiagram
import com.tepmex.hapticlab.ui.components.KeepScreenOn
import com.tepmex.hapticlab.ui.components.LevelBanner
import com.tepmex.hapticlab.ui.components.StopButton
import com.tepmex.hapticlab.ui.graph

@Composable
fun CapabilityScreen(onBack: () -> Unit) {
    val graph = graph()
    var feedback by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(Unit) {
        graph.refreshInventory()
    }
    val inventory = graph.inventory
    val device = inventory.snapshot
    KeepScreenOn()
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onBack) { Text("Назад") }
                Button(onClick = {
                    graph.refreshInventory()
                    feedback = null
                }) { Text("Обновить") }
                StopButton(onClick = {
                    graph.engine.stop()
                    feedback = null
                })
            }
            Text("Проверка эффектов", style = MaterialTheme.typography.headlineSmall)
            Text(
                "${device.manufacturer} ${device.model} · Android ${device.androidRelease} · SDK ${device.sdkInt}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LevelBanner()
            Text(
                "Шесть возможностей ритмической читалки. Статус — ответ платформы. «Проверить» проигрывает короткий образец, чтобы сверить ответ с ощущением.",
            )
            if (inventory.capabilityReport.isEmpty()) {
                Text("Опрос вибромотора не собрал отчёт.")
                inventory.rows.forEach { row ->
                    Column {
                        Text(row.title, style = MaterialTheme.typography.labelLarge)
                        Text(row.value)
                        row.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            } else {
                inventory.capabilityReport.forEach { probe ->
                    CapabilityCard(
                        probe = probe,
                        feedback = feedback?.takeIf { it.first == probe.id }?.second,
                        onPlay = { item ->
                            feedback = probe.id to playProbe(graph, inventory, probe.id, item)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CapabilityCard(
    probe: CapabilityProbe,
    feedback: String?,
    onPlay: (String?) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(probe.title, style = MaterialTheme.typography.titleMedium)
            Text(
                probe.outcome.titleRu(),
                style = MaterialTheme.typography.labelLarge,
                color = outcomeColor(probe.outcome),
            )
            Text(
                "Как проверить: ${probe.method}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(probe.summary)
            if (probe.id == CapabilityIds.TIMED_PULSES) {
                Text(
                    "Диаграмма ниже — серия 40/40 мс. Одиночный импульс на ней не показан.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CommandDiagram(timedPulseProbe())
            }
            if (probe.id == CapabilityIds.AMPLITUDE) {
                CommandDiagram(amplitudeContrastProbe())
            }
            if (probe.items.isNotEmpty()) {
                probe.items.forEach { item ->
                    ProbeItemRow(item = item, playable = probe.id != CapabilityIds.DURATIONS, onPlay = onPlay)
                }
            } else {
                ProbeActions(probe.id, onPlay)
            }
            feedback?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(
                "Для читалки: ${probe.readerValue}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProbeItemRow(item: ProbeItem, playable: Boolean, onPlay: (String?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.labelLarge)
            Text(item.reading, color = outcomeColor(item.outcome))
        }
        if (playable) {
            Button(
                onClick = { onPlay(item.name) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Проверить") }
        }
    }
}

@Composable
private fun ProbeActions(id: String, onPlay: (String?) -> Unit) {
    when (id) {
        CapabilityIds.TIMED_PULSES -> {
            ActionButton("Импульс 40 мс") { onPlay("oneshot") }
            ActionButton("Серия 40/40 мс") { onPlay("waveform") }
        }
        CapabilityIds.AMPLITUDE -> ActionButton("Слабый, затем сильный") { onPlay(null) }
        CapabilityIds.ENVELOPES -> {
            ActionButton("Огибающая интенсивности") { onPlay("intensity") }
            ActionButton("Огибающая частоты") { onPlay("frequency") }
        }
    }
}

@Composable
private fun ActionButton(title: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
        Text(title, textAlign = TextAlign.Center)
    }
}

@Composable
private fun outcomeColor(outcome: ProbeOutcome): Color = when (outcome) {
    ProbeOutcome.SUPPORTED -> MaterialTheme.colorScheme.secondary
    ProbeOutcome.PARTIAL -> MaterialTheme.colorScheme.primary
    ProbeOutcome.UNSUPPORTED, ProbeOutcome.NO_VIBRATOR -> MaterialTheme.colorScheme.error
    ProbeOutcome.UNKNOWN, ProbeOutcome.API_UNAVAILABLE -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun playProbe(graph: AppGraph, inventory: HapticInventory, id: String, item: String?): String {
    val engine = graph.engine
    val result = when (id) {
        CapabilityIds.TIMED_PULSES -> when (item) {
            "waveform" -> engine.playWaveform(timedPulseProbe()).let { result ->
                result.copy(message = result.message + " Три включения по 40 мс и две паузы по 40 мс.")
            }
            else -> engine.playOneShot(40L, 180).let { result ->
                result.copy(message = result.message + " Один импульс 40 мс.")
            }
        }
        CapabilityIds.AMPLITUDE -> engine.playWaveform(amplitudeContrastProbe()).let { result ->
            val note = if (inventory.hasAmplitudeControl) {
                " Сначала слабый импульс, после паузы сильный."
            } else {
                " Оба импульса — одинаковое «включено»."
            }
            result.copy(message = result.message + note)
        }
        CapabilityIds.EFFECTS -> {
            val effect = inventory.effects.firstOrNull { it.name == item }
                ?: return "Эффект не найден в опросе"
            engine.playPredefined(effect.id, effect.name)
        }
        CapabilityIds.PRIMITIVES -> {
            val primitive = inventory.primitives.firstOrNull { it.name == item }
                ?: return "Примитив не найден в опросе"
            engine.playPrimitiveTrain(primitive.id, primitive.name, pulses = 1, gapMs = 0, amplitude = 200)
        }
        CapabilityIds.ENVELOPES -> when (item) {
            "frequency" -> engine.playWaveformEnvelope(80L, 80L, 200, inventory)
            else -> engine.playBasicEnvelope(80L, 80L, pulses = 2, amplitude = 200, inventory = inventory)
        }
        else -> return "Для этой проверки образец не проигрывается"
    }
    return result.message
}
