package com.tepmex.hapticlab.core.metrics

import kotlin.math.abs

class SpeedTracker {
    var instantaneousMmPerSec: Float = 0f
        private set
    var maxMmPerSec: Float = 0f
        private set
    private var sum: Double = 0.0
    private var count: Int = 0
    private var lastXMm: Float? = null
    private var lastTimeNs: Long? = null

    val averageMmPerSec: Float
        get() = if (count == 0) 0f else (sum / count).toFloat()

    val sampleCount: Int get() = count

    /**
     * Возвращает мгновенную скорость или null, если интервал слишком мал
     * либо скачок физически неправдоподобен для пальца.
     */
    fun sample(xMm: Float, timeNs: Long): Float? {
        val previousX = lastXMm
        val previousT = lastTimeNs
        lastXMm = xMm
        lastTimeNs = timeNs
        if (previousX == null || previousT == null) return null
        val dtSec = (timeNs - previousT) / 1_000_000_000.0
        if (dtSec < 0.002) return null
        val speed = abs(xMm - previousX) / dtSec
        if (!speed.isFinite() || speed > 5_000.0) return null
        instantaneousMmPerSec = speed.toFloat()
        if (instantaneousMmPerSec > maxMmPerSec) maxMmPerSec = instantaneousMmPerSec
        sum += speed
        count += 1
        return instantaneousMmPerSec
    }

    fun reset() {
        instantaneousMmPerSec = 0f
        maxMmPerSec = 0f
        sum = 0.0
        count = 0
        lastXMm = null
        lastTimeNs = null
    }
}

class BoundaryCounter {
    var rawCrossings: Int = 0
        private set
    var requestedTransitions: Int = 0
        private set
    private var lastZone: Int? = null
    private var startZone: Int? = null
    var passStartNs: Long? = null
        private set
    var passEndNs: Long? = null
        private set

    fun onZone(zone: Int?, timeNs: Long, zoneCount: Int) {
        if (zone == null || zoneCount <= 1) return
        val previous = lastZone
        if (previous != null && previous != zone) {
            rawCrossings += abs(zone - previous)
        }
        if (passStartNs == null && (zone == 0 || zone == zoneCount - 1)) {
            passStartNs = timeNs
            startZone = zone
        }
        val origin = startZone
        if (passStartNs != null && passEndNs == null && origin != null && previous != null) {
            val reachedOtherEnd = (origin == 0 && zone == zoneCount - 1 && previous < zone) ||
                (origin == zoneCount - 1 && zone == 0 && previous > zone)
            if (reachedOtherEnd) passEndNs = timeNs
        }
        lastZone = zone
    }

    fun onRequestedTransition() {
        requestedTransitions += 1
    }

    fun durationMillis(): Long? {
        val start = passStartNs ?: return null
        val end = passEndNs ?: return null
        return (end - start) / 1_000_000L
    }

    /** Запрошенные переключения в секунду за завершённый проход. Не частота мотора. */
    fun requestedSwitchHz(): Double? {
        val millis = durationMillis() ?: return null
        if (millis <= 0L) return null
        return requestedTransitions / (millis / 1_000.0)
    }

    fun reset() {
        rawCrossings = 0
        requestedTransitions = 0
        lastZone = null
        startZone = null
        passStartNs = null
        passEndNs = null
    }
}
