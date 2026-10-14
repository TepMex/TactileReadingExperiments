package com.tepmex.hapticlab.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tepmex.hapticlab.core.signal.WaveformSpec
import kotlin.math.abs
import kotlin.math.roundToInt

private val OnColor = Color(0xFFE39B2B)
private val OffColor = Color(0xFF2C3138)
private val BlindColor = Color(0xFF2A2A2A)
private val Groove = Color(0x66F3F1E8)

data class TrackPoint(val xPx: Float, val yPx: Float, val uptimeMillis: Long)

@Composable
fun LevelBanner() {
    Text(
        "Уровень А: фиксируются моменты команд и ввода. Это не физический отклик мотора и не момент ощущения.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }
}

@Composable
fun IntSlider(
    label: String,
    value: Int,
    range: IntRange,
    enabled: Boolean = true,
    onChange: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("$label: $value", style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt().coerceIn(range.first, range.last)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            enabled = enabled,
        )
    }
}

@Composable
fun FloatSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    decimals: Int = 1,
    suffix: String = "",
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "$label: ${String.format(java.util.Locale.US, "%.${decimals}f", value)}$suffix",
            style = MaterialTheme.typography.bodyLarge,
        )
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = { onChange(it.coerceIn(range.start, range.endInclusive)) },
            valueRange = range,
        )
    }
}

@Composable
fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun StopButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier.heightIn(min = 56.dp)) {
        Text("Стоп")
    }
}

@Composable
fun CommandDiagram(spec: WaveformSpec?) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Запрошенный управляющий сигнал, не измеренная вибрация",
            style = MaterialTheme.typography.labelMedium,
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .padding(top = 8.dp)
                .background(Color(0xFF171910), RoundedCornerShape(8.dp)),
        ) {
            val waveform = spec ?: return@Canvas
            val total = waveform.totalMs.coerceAtLeast(1L).toFloat()
            var x = 0f
            waveform.timingsMs.forEachIndexed { index, timing ->
                val width = size.width * (timing / total)
                val amplitude = waveform.amplitudes[index]
                val level = if (amplitude <= 0) 0f else amplitude.coerceIn(1, 255) / 255f
                val barHeight = if (amplitude == 0) 6f else size.height * (0.18f + 0.62f * level)
                drawRect(
                    color = if (amplitude == 0) Color(0xFF6E7468) else OnColor,
                    topLeft = Offset(x, size.height - barHeight),
                    size = Size(width.coerceAtLeast(1f), barHeight),
                )
                x += width
            }
        }
    }
}

@Composable
fun ZoneTrack(
    zones: List<Boolean>,
    zoneWidthPx: Float,
    blind: Boolean,
    activeZone: Int?,
    editMode: Boolean,
    markers: List<Int> = emptyList(),
    showMarkers: Boolean = false,
    onTapZone: (Int) -> Unit,
    onDown: (TrackPoint) -> Unit,
    onMove: (List<TrackPoint>) -> Unit,
    onUp: () -> Unit,
) {
    val density = LocalDensity.current
    val widthDp = with(density) { (zones.size * zoneWidthPx).coerceAtLeast(1f).toDp() }
    Canvas(
        modifier = Modifier
            .width(widthDp)
            .fillMaxHeight()
            .border(1.dp, Color(0xFF8A8F86), RoundedCornerShape(12.dp))
            .pointerInput(editMode, zones, zoneWidthPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pointerId = down.id
                    if (editMode) {
                        var moved = false
                        var last = down
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: continue
                            if (abs(change.position.x - down.position.x) > 18f) moved = true
                            change.consume()
                            if (!change.pressed) {
                                last = change
                                break
                            }
                        }
                        if (!moved && zoneWidthPx > 0f) {
                            val index = (last.position.x / zoneWidthPx).toInt()
                            if (index in zones.indices) onTapZone(index)
                        }
                    } else {
                        onDown(TrackPoint(down.position.x, down.position.y, down.uptimeMillis))
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: continue
                            val points = buildList {
                                change.historical.forEach { sample ->
                                    add(TrackPoint(sample.position.x, sample.position.y, sample.uptimeMillis))
                                }
                                add(TrackPoint(change.position.x, change.position.y, change.uptimeMillis))
                            }
                            if (points.isNotEmpty()) onMove(points)
                            change.consume()
                            if (!change.pressed) {
                                onUp()
                                break
                            }
                        }
                    }
                }
            },
    ) {
        if (zones.isEmpty() || zoneWidthPx <= 0f) return@Canvas
        zones.forEachIndexed { index, on ->
            val left = index * zoneWidthPx
            val color = when {
                blind -> BlindColor
                on -> OnColor
                else -> OffColor
            }
            drawRect(
                color = color,
                topLeft = Offset(left, 0f),
                size = Size(zoneWidthPx, size.height),
            )
            if (!blind && index == activeZone) {
                drawRect(
                    color = Color.White,
                    topLeft = Offset(left + 1f, 1f),
                    size = Size((zoneWidthPx - 2f).coerceAtLeast(1f), size.height - 2f),
                    style = Stroke(width = 4f),
                )
            }
            if (showMarkers && !blind && index in markers) {
                drawLine(
                    color = Color(0xFFB7C4A8),
                    start = Offset(left, 0f),
                    end = Offset(left, size.height),
                    strokeWidth = 3f,
                )
            }
        }
        drawLine(
            color = Groove,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 2f,
        )
    }
}

@Composable
fun ChoiceRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { option ->
                    Button(
                        onClick = { onSelect(option) },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        enabled = option != selected,
                    ) {
                        Text(option, textAlign = TextAlign.Center)
                    }
                }
                if (row.size == 1) {
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun TrackViewport(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF10120E), RoundedCornerShape(16.dp))
            .padding(8.dp),
    ) {
        content()
    }
}
