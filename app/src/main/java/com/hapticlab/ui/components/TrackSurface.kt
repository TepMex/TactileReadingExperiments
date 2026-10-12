package com.hapticlab.ui.components

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import com.hapticlab.core.spatial.mmToPx
import com.hapticlab.core.time.mapUptimeMillisToElapsedRealtimeNanos
import com.hapticlab.haptics.LocatedSample
import kotlin.math.roundToInt

@Composable
fun FingerTrack(
    zones: List<Boolean>,
    zoneWidthMm: Float,
    mmPerPx: Float,
    blind: Boolean,
    activeZone: Int?,
    editMode: Boolean,
    markers: List<Int> = emptyList(),
    showMarkers: Boolean = false,
    onTapZone: (Int) -> Unit = {},
    onDown: (LocatedSample) -> Unit = {},
    onMoves: (List<LocatedSample>) -> Unit = {},
    onUp: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewportPx = with(density) { maxWidth.toPx() }
        val zoneWidthPx = mmToPx(zoneWidthMm, mmPerPx).coerceAtLeast(8f)
        val maxPan = (zones.size * zoneWidthPx - viewportPx).coerceAtLeast(0f)
        var panPx by remember(zones.size, zoneWidthMm, mmPerPx) { mutableFloatStateOf(0f) }
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds(),
            ) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(-panPx.roundToInt(), 0) }
                        .fillMaxHeight(),
                ) {
                    ZoneTrack(
                        zones = zones,
                        zoneWidthPx = zoneWidthPx,
                        blind = blind,
                        activeZone = activeZone,
                        editMode = editMode,
                        markers = markers,
                        showMarkers = showMarkers,
                        onTapZone = onTapZone,
                        onDown = { point -> onDown(point.toSample(zoneWidthMm, mmPerPx, zones)) },
                        onMove = { points -> onMoves(points.map { it.toSample(zoneWidthMm, mmPerPx, zones) }) },
                        onUp = onUp,
                    )
                }
            }
            if (maxPan > 1f) {
                FloatSlider(
                    label = "Сдвиг дорожки",
                    value = (panPx * mmPerPx).coerceIn(0f, maxPan * mmPerPx),
                    range = 0f..(maxPan * mmPerPx),
                    suffix = " мм",
                ) { panPx = it / mmPerPx }
            }
        }
    }
}

private fun TrackPoint.toSample(zoneWidthMm: Float, mmPerPx: Float, zones: List<Boolean>): LocatedSample {
    val nowUptime = SystemClock.uptimeMillis()
    val nowElapsed = SystemClock.elapsedRealtimeNanos()
    val eventElapsed = mapUptimeMillisToElapsedRealtimeNanos(uptimeMillis, nowUptime, nowElapsed)
    return LocatedSample(
        timeMs = eventElapsed / 1_000_000L,
        elapsedRealtimeNanos = eventElapsed,
        motionEventElapsedRealtimeNanos = eventElapsed,
        xPx = xPx,
        yPx = yPx,
        xMm = xPx * mmPerPx,
        zoneWidthMm = zoneWidthMm,
        zones = zones,
    )
}
