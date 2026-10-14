package com.tepmex.vibealphabet.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tepmex.vibealphabet.core.Waveform

@Composable
fun WaveformStrip(
    waveform: Waveform,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onColor: Color,
    offColor: Color,
) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(40.dp),
    ) {
        val total = waveform.totalMs.coerceAtLeast(1L).toFloat()
        val gap = 2.dp.toPx()
        var x = 0f
        waveform.timingsMs.forEachIndexed { index, duration ->
            val width = duration.toFloat() / total * size.width
            val amplitude = waveform.amplitudes[index]
            val barWidth = (width - gap).coerceAtLeast(1f)
            if (amplitude > 0) {
                val fraction = amplitude / 255f
                val barHeight = (0.28f + 0.68f * fraction) * size.height
                drawRoundRect(
                    color = onColor.copy(alpha = if (active) 1f else 0.55f + 0.45f * fraction),
                    topLeft = Offset(x + gap / 2f, (size.height - barHeight) / 2f),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
            } else {
                val thickness = 2.dp.toPx()
                drawRoundRect(
                    color = offColor,
                    topLeft = Offset(x + gap / 2f, size.height / 2f - thickness / 2f),
                    size = Size(barWidth, thickness),
                    cornerRadius = CornerRadius(thickness),
                )
            }
            x += width
        }
    }
}
