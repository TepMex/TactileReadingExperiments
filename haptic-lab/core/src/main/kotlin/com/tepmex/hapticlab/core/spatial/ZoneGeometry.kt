package com.tepmex.hapticlab.core.spatial

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

/** Индекс зоны или null, если точка вне дорожки. */
fun zoneIndexAt(xMm: Float, zoneWidthMm: Float, zoneCount: Int): Int? {
    if (zoneCount <= 0 || zoneWidthMm <= 0f || !xMm.isFinite()) return null
    val trackMm = zoneCount * zoneWidthMm
    if (xMm < 0f || xMm >= trackMm) return null
    return floor(xMm / zoneWidthMm).toInt().coerceIn(0, zoneCount - 1)
}

/**
 * Насколько палец вошёл в [newZone] со стороны [previousZone].
 * Если зоны перескочены, отсчёт идёт от ближней к предыдущей зоне границы.
 */
fun penetrationFromPreviousMm(
    xMm: Float,
    zoneWidthMm: Float,
    newZone: Int,
    previousZone: Int,
): Float {
    if (zoneWidthMm <= 0f || newZone == previousZone) return 0f
    val raw = if (newZone > previousZone) {
        xMm - newZone * zoneWidthMm
    } else {
        (newZone + 1) * zoneWidthMm - xMm
    }
    return raw.coerceAtLeast(0f)
}

/** Расстояние до ближайшей границы внутри зоны. Нужно при входе снаружи дорожки. */
fun distanceToNearestEdgeMm(xMm: Float, zoneWidthMm: Float, zone: Int): Float {
    if (zoneWidthMm <= 0f) return 0f
    val start = zone * zoneWidthMm
    val end = start + zoneWidthMm
    return min(xMm - start, end - xMm).coerceAtLeast(0f)
}

fun penetrationForTransition(
    xMm: Float,
    zoneWidthMm: Float,
    zone: Int,
    previousLatched: Int?,
): Float {
    return if (previousLatched == null || previousLatched == zone) {
        distanceToNearestEdgeMm(xMm, zoneWidthMm, zone)
    } else {
        penetrationFromPreviousMm(xMm, zoneWidthMm, zone, previousLatched)
    }
}

fun boundaryCrossings(fromZone: Int, toZone: Int): Int = abs(toZone - fromZone)
