package com.tepmex.vibealphabet.ui.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tepmex.vibealphabet.core.Pattern
import com.tepmex.vibealphabet.core.Segment
import com.tepmex.vibealphabet.core.addSegment
import com.tepmex.vibealphabet.core.canAddSegment
import com.tepmex.vibealphabet.core.maxSegmentDuration
import com.tepmex.vibealphabet.core.removeSegment
import com.tepmex.vibealphabet.core.setSegmentDuration
import com.tepmex.vibealphabet.core.splitSegment
import com.tepmex.vibealphabet.core.toggleSegment
import kotlin.math.roundToInt

@Composable
fun SignalEditor(
    pattern: Pattern,
    edited: Boolean,
    onPattern: (Pattern) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var durationIndex by remember { mutableStateOf<Int?>(null) }
    val openIndex = durationIndex?.takeIf { it in pattern.segments.indices }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Участки в окне ${pattern.totalMs} мс", style = MaterialTheme.typography.titleMedium)
        PatternTimeline(pattern) { index -> onPattern(toggleSegment(pattern, index)) }
        Text(
            "Нажатие на полосу переключает вибрацию и тишину. Длительность задаётся в миллисекундах, сумма всегда равна окну. Одинаковые участки подряд сливаются в один.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        pattern.segments.forEachIndexed { index, segment ->
            SegmentCard(
                segment = segment,
                index = index,
                pattern = pattern,
                onPattern = onPattern,
                onEditDuration = { durationIndex = index },
            )
        }
        EditorButton(
            label = "Добавить участок",
            enabled = canAddSegment(pattern),
            onClick = { onPattern(addSegment(pattern)) },
        )
        if (edited) {
            EditorButton(label = "Вернуть эталон", enabled = true, onClick = onReset)
        }
    }
    if (openIndex != null) {
        DurationDialog(
            segment = pattern.segments[openIndex],
            index = openIndex,
            count = pattern.segments.size,
            maxMs = maxSegmentDuration(pattern),
            onDismiss = { durationIndex = null },
            onConfirm = { duration ->
                onPattern(setSegmentDuration(pattern, openIndex, duration))
                durationIndex = null
            },
        )
    }
}

@Composable
private fun PatternTimeline(pattern: Pattern, onToggle: (Int) -> Unit) {
    val onColor = MaterialTheme.colorScheme.primary
    val offColor = MaterialTheme.colorScheme.background
    val silence = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .semantics {
                contentDescription = "Участки сигнала. Нажатие переключает вибрацию и тишину."
            },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        pattern.segments.forEachIndexed { index, segment ->
            val name = if (segment.on) "Вибрация" else "Тишина"
            Box(
                Modifier
                    .weight(segment.durationMs.toFloat())
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (segment.on) onColor else offColor)
                    .clickable { onToggle(index) }
                    .semantics { contentDescription = "$name, ${segment.durationMs} мс" },
                contentAlignment = Alignment.Center,
            ) {
                if (!segment.on) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(silence),
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentCard(
    segment: Segment,
    index: Int,
    pattern: Pattern,
    onPattern: (Pattern) -> Unit,
    onEditDuration: () -> Unit,
) {
    val several = pattern.segments.size > 1
    val maxMs = maxSegmentDuration(pattern)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PolarityButton(segment.on) { onPattern(toggleSegment(pattern, index)) }
                DurationLabel(
                    durationMs = segment.durationMs,
                    editable = several,
                    onClick = onEditDuration,
                )
            }
            if (several && maxMs > 1) {
                val steps = (maxMs - 2).coerceAtLeast(0)
                Slider(
                    value = segment.durationMs.toFloat().coerceIn(1f, maxMs.toFloat()),
                    onValueChange = { raw ->
                        onPattern(setSegmentDuration(pattern, index, raw.roundToInt()))
                    },
                    valueRange = 1f..maxMs.toFloat(),
                    steps = steps,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StepButton("−10", segment.durationMs > 1) {
                        onPattern(setSegmentDuration(pattern, index, segment.durationMs - 10))
                    }
                    StepButton("−1", segment.durationMs > 1) {
                        onPattern(setSegmentDuration(pattern, index, segment.durationMs - 1))
                    }
                    StepButton("+1", segment.durationMs < maxMs) {
                        onPattern(setSegmentDuration(pattern, index, segment.durationMs + 1))
                    }
                    StepButton("+10", segment.durationMs < maxMs) {
                        onPattern(setSegmentDuration(pattern, index, segment.durationMs + 10))
                    }
                }
            } else if (!several) {
                Text(
                    "Это всё окно. Добавьте участок, чтобы рядом появился другой промежуток.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "Каждый участок уже 1 мс. Чтобы удлинить один, уберите другой.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = { onPattern(splitSegment(pattern, index)) },
                    enabled = segment.durationMs >= 3,
                ) {
                    Text("Разделить")
                }
                TextButton(
                    onClick = { onPattern(removeSegment(pattern, index)) },
                    enabled = several,
                ) {
                    Text("Убрать")
                }
            }
        }
    }
}

@Composable
private fun PolarityButton(on: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
        contentColor = if (on) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    ) {
        Text(
            if (on) "Вибрация" else "Тишина",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun DurationLabel(durationMs: Int, editable: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.End,
        modifier = if (editable) {
            Modifier.clickable(onClick = onClick)
        } else {
            Modifier
        },
    ) {
        Text(
            "$durationMs мс",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
        )
        if (editable) {
            Text(
                "задать",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.background,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun EditorButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            label,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DurationDialog(
    segment: Segment,
    index: Int,
    count: Int,
    maxMs: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var draft by remember { mutableStateOf(segment.durationMs.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    fun confirm() {
        val value = draft.toIntOrNull()
        if (value == null || value < 1 || value > maxMs) {
            error = "Введите число от 1 до $maxMs."
            return
        }
        onConfirm(value)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Участок ${index + 1} из $count") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "От 1 до $maxMs мс. Соседние участки отдадут или примут разницу, сумма останется прежней.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = draft,
                    onValueChange = { raw ->
                        draft = raw.filter(Char::isDigit).take(4)
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = error != null,
                    label = { Text("Миллисекунды") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                )
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = ::confirm) { Text("Готово") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
