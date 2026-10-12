package com.tepmex.hapticlab.core.log

import kotlinx.serialization.Serializable

@Serializable
data class LogEvent(
    val elapsedRealtimeNanos: Long,
    val motionEventElapsedRealtimeNanos: Long? = null,
    val kind: String,
    val xPx: Float? = null,
    val yPx: Float? = null,
    val xMm: Float? = null,
    val speedMmPerSec: Float? = null,
    val zoneIndex: Int? = null,
    val requestedVibrationOn: Boolean? = null,
    val detail: String? = null,
)

@Serializable
data class DeviceSnapshot(
    val manufacturer: String,
    val model: String,
    val androidRelease: String,
    val sdkInt: Int,
    val sdkIntFull: Int?,
    val vibratorCount: Int,
    val hasVibrator: Boolean,
    val hasAmplitudeControl: Boolean,
    val resonantFrequencyHz: Float?,
    val qFactor: Float?,
    val freeDecayT10Ms: Double?,
    val envelopeSupported: Boolean?,
    val notes: List<String> = emptyList(),
)

@Serializable
data class ExperimentSession(
    val id: String,
    val type: String,
    val wallClockIso: String,
    val startedElapsedRealtimeNanos: Long,
    val device: DeviceSnapshot,
    val parameters: Map<String, String>,
    val events: List<LogEvent>,
    val summary: Map<String, String> = emptyMap(),
)

class SessionRecorder(
    var verboseMotion: Boolean = false,
    private val minMotionIntervalNanos: Long = 50_000_000L,
    private val maxEvents: Int = MAX_EVENTS,
) {
    private val events = ArrayList<LogEvent>()
    private var lastMotionElapsed: Long? = null
    private var droppedMotion: Int = 0

    fun snapshot(): List<LogEvent> = events.toList()

    fun log(event: LogEvent) {
        if (events.size >= maxEvents) return
        events += event
    }

    fun logMotion(event: LogEvent) {
        if (!verboseMotion) {
            droppedMotion += 1
            return
        }
        val previous = lastMotionElapsed
        if (previous != null && event.elapsedRealtimeNanos - previous < minMotionIntervalNanos) {
            droppedMotion += 1
            return
        }
        lastMotionElapsed = event.elapsedRealtimeNanos
        log(event)
    }

    fun droppedMotionCount(): Int = droppedMotion

    fun clear() {
        events.clear()
        lastMotionElapsed = null
        droppedMotion = 0
    }

    companion object {
        const val MAX_EVENTS = 20_000
        const val KIND_MOTION = "motion"
        const val KIND_ZONE = "zone"
        const val KIND_VIBRATION_COMMAND = "vibration_command"
        const val KIND_ANSWER = "answer"
        const val KIND_TRIAL = "trial"
        const val KIND_NOTE = "note"
        const val KIND_RATING = "rating"
    }
}
