package com.hapticlab.ui.diagnostics

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.hapticlab.core.spatial.calibratedMmPerPx
import com.hapticlab.core.spatial.scaleFromMeasuredWidth
import com.hapticlab.core.spatial.systemMmPerPx
import com.hapticlab.ui.components.KeepScreenOn
import com.hapticlab.ui.components.LevelBanner
import com.hapticlab.ui.components.StopButton
import com.hapticlab.ui.graph

@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val graph = graph()
    val view = LocalView.current
    val xdpi = view.resources.displayMetrics.xdpi
    val widthPx = view.resources.displayMetrics.widthPixels
    val systemMm = widthPx * systemMmPerPx(xdpi)
    var measured by remember { mutableStateOf(String.format(java.util.Locale.US, "%.1f", systemMm * graph.calibrationScale)) }
    var message by remember { mutableStateOf<String?>(null) }
    val inventory = graph.inventory
    KeepScreenOn()
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onBack) { Text("Назад") }
                Button(onClick = { graph.refreshInventory() }) { Text("Обновить") }
                StopButton(onClick = { graph.engine.stop() })
            }
            Text("Диагностика вибромотора", style = MaterialTheme.typography.headlineSmall)
            LevelBanner()
            Button(
                onClick = {
                    val result = graph.engine.playOneShot(40, 180)
                    message = result.message
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Проверочный импульс 40 мс") }
            message?.let { Text(it) }
            inventory.rows.forEach { row ->
                Column {
                    Text(row.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(row.value, style = MaterialTheme.typography.bodyLarge)
                    row.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            Text("Калибровка миллиметров", style = MaterialTheme.typography.titleMedium)
            Text(
                "Системный xdpi ${String.format(java.util.Locale.US, "%.1f", xdpi)}, ширина окна $widthPx px ≈ ${String.format(java.util.Locale.US, "%.1f", systemMm)} мм. Системный dpi может быть неточным.",
            )
            Text("Текущий масштаб: ${String.format(java.util.Locale.US, "%.3f", graph.calibrationScale)}")
            Text("мм/пиксель: ${String.format(java.util.Locale.US, "%.4f", calibratedMmPerPx(xdpi, graph.calibrationScale))}")
            OutlinedTextField(
                value = measured,
                onValueChange = { measured = it },
                label = { Text("Измеренная ширина экрана, мм") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val value = measured.replace(',', '.').toFloatOrNull()
                    val scale = value?.let { scaleFromMeasuredWidth(systemMm, it) }
                    if (scale == null) {
                        message = "Введите положительную ширину в миллиметрах"
                    } else {
                        graph.updateCalibrationScale(scale)
                        message = "Масштаб обновлён: ${String.format(java.util.Locale.US, "%.3f", scale)}"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Применить калибровку") }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Подробный журнал движения", modifier = Modifier.weight(1f))
                Switch(checked = graph.verboseLogging, onCheckedChange = graph::updateVerboseLogging)
            }
            Text("Без подробного журнала сохраняются переходы зон, команды вибрации и ответы. Координаты каждого события движения пишутся не чаще 20 раз в секунду и только если журнал включён.")
        }
    }
}
