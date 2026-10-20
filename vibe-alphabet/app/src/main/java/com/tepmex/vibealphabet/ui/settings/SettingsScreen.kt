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
import com.tepmex.vibealphabet.core.SignalId
import com.tepmex.vibealphabet.core.allowedWindowMillis
import com.tepmex.vibealphabet.core.formatSegments
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
            "Окно — длина команды, включая тишину. На обучении у каждого сигнала свои промежутки вибрации и тишины; в сумме они равны окну. Если окно изменить, промежутки масштабируются и снова заполняют его целиком. Символ — это один или несколько таких сигналов подряд, каждый в своём окне. Включённый участок всегда на полной силе. Команда не равна ощущению: мотору нужно время разогнаться и остановиться.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("Команда при $window мс", style = MaterialTheme.typography.titleMedium)
        SignalId.entries.forEach { signal ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${signal.title}. ${signal.patternName} — ${signal.sensation}" +
                        if (graph.isEdited(signal)) " · изменён" else "",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    formatSegments(graph.waveform(signal)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!graph.player.hasVibrator) {
            Text(
                "Вибромотор не найден.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
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

