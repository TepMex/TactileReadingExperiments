package com.tepmex.vibealphabet.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tepmex.vibealphabet.core.MAX_WINDOW_MS
import com.tepmex.vibealphabet.core.MIN_WINDOW_MS
import com.tepmex.vibealphabet.core.STRONG_AMPLITUDE
import com.tepmex.vibealphabet.core.Shape
import com.tepmex.vibealphabet.core.SignalId
import com.tepmex.vibealphabet.core.WEAK_AMPLITUDE
import com.tepmex.vibealphabet.core.allowedWindowMillis
import com.tepmex.vibealphabet.core.shapeTitle
import com.tepmex.vibealphabet.core.waveformFor
import com.tepmex.vibealphabet.ui.LocalGraph
import kotlin.math.abs

@Composable
fun SettingsScreen() {
    val graph = LocalGraph.current
    val window = graph.windowMs
    val windows = remember { allowedWindowMillis }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineSmall)
        Text("Длительность окна", style = MaterialTheme.typography.titleMedium)
        Text(
            "$window мс",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Slider(
            value = window.toFloat(),
            onValueChange = { raw ->
                val nearest = windows.minBy { abs(it - raw) }
                graph.setWindow(nearest)
            },
            valueRange = MIN_WINDOW_MS.toFloat()..MAX_WINDOW_MS.toFloat(),
            steps = windows.size - 2,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StepButton("−50 мс", enabled = window > MIN_WINDOW_MS) {
                graph.setWindow(window - 50)
            }
            StepButton("+50 мс", enabled = window < MAX_WINDOW_MS) {
                graph.setWindow(window + 50)
            }
        }
        Text(
            "Окно — длина команды, включая тишину. Короткий удар, протяжённая вибрация и двойной удар делят одно окно. Участки масштабируются от эталона 100 мс. Команда не равна ощущению: мотору нужно время разогнаться и остановиться.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("Команда при $window мс", style = MaterialTheme.typography.titleMedium)
        Shape.entries.forEach { shape ->
            val sample = SignalId.entries.first { it.shape == shape }
            val timings = waveformFor(sample, window).timingsMs.joinToString(" + ") { "$it" }
            Text(
                "${shapeTitle(shape)}: $timings мс",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Text(
            "Слабая сила $WEAK_AMPLITUDE, сильная $STRONG_AMPLITUDE. Тишина — амплитуда 0. Это стартовые значения, не подтверждённый алфавит.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AmplitudeNote()
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun AmplitudeNote() {
    val player = LocalGraph.current.player
    val text = when {
        !player.hasVibrator -> "Вибромотор не найден."
        player.hasAmplitudeControl -> "Управление амплитудой есть: слабый и сильный различаются силой."
        else -> "Управления амплитудой нет. Ненулевые участки станут максимальными, и шесть сигналов сожмутся до трёх."
    }
    val color = if (player.hasVibrator && player.hasAmplitudeControl) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.error
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
}
