package com.tepmex.hapticlab.haptics

import android.os.SystemClock
import com.tepmex.hapticlab.core.log.LogEvent
import com.tepmex.hapticlab.core.log.SessionRecorder
import com.tepmex.hapticlab.core.metrics.BoundaryCounter
import com.tepmex.hapticlab.core.metrics.SpeedTracker
import com.tepmex.hapticlab.core.spatial.HapticCommand
import com.tepmex.hapticlab.core.spatial.SpatialVibrationMachine
import com.tepmex.hapticlab.core.spatial.ZoneSample
import com.tepmex.hapticlab.core.spatial.isStateTransition
import com.tepmex.hapticlab.core.spatial.penetrationForTransition
import com.tepmex.hapticlab.core.spatial.zoneIndexAt

data class LocatedSample(
    val timeMs: Long,
    val elapsedRealtimeNanos: Long,
    val motionEventElapsedRealtimeNanos: Long?,
    val xPx: Float,
    val yPx: Float,
    val xMm: Float,
    val zoneWidthMm: Float,
    val zones: List<Boolean>,
)

data class FingerFrame(
    val zone: Int?,
    val requestedOn: Boolean,
    val speedMmPerSec: Float,
    val averageMmPerSec: Float,
    val maxMmPerSec: Float,
    val rawCrossings: Int,
    val transitions: Int,
    val passDone: Boolean,
    val durationMs: Long?,
    val switchHz: Double?,
    val message: String?,
    val budgetExhausted: Boolean,
)

class FingerSession(
    private val engine: HapticsEngine,
    val recorder: SessionRecorder,
) {
    val machine = SpatialVibrationMachine()
    val speed = SpeedTracker()
    val boundaries = BoundaryCounter()
    private var message: String? = null
    private var lastLoggedZone: Int? = null

    fun configure(debounceMm: Float, amplitude: Int, touchBudgetMs: Long) {
        machine.debounceMm = debounceMm
        machine.amplitude = amplitude
        machine.touchBudgetMs = touchBudgetMs
    }

    fun down(sample: LocatedSample): FingerFrame {
        speed.reset()
        boundaries.reset()
        lastLoggedZone = null
        message = null
        speed.sample(sample.xMm, sample.elapsedRealtimeNanos)
        val commands = machine.down(zoneSample(sample))
        return publish(sample, commands, logMotion = false)
    }

    fun move(sample: LocatedSample): FingerFrame {
        speed.sample(sample.xMm, sample.elapsedRealtimeNanos)
        val commands = machine.move(zoneSample(sample))
        return publish(sample, commands, logMotion = true)
    }

    fun up(timeMs: Long, elapsedRealtimeNanos: Long): FingerFrame {
        val commands = machine.up(timeMs)
        engine.stop()
        val frame = publish(
            LocatedSample(timeMs, elapsedRealtimeNanos, null, 0f, 0f, 0f, 1f, emptyList()),
            commands,
            logMotion = false,
        )
        recorder.log(
            LogEvent(
                elapsedRealtimeNanos = elapsedRealtimeNanos,
                kind = SessionRecorder.KIND_NOTE,
                requestedVibrationOn = false,
                detail = "finger_up",
            ),
        )
        return frame
    }

    fun tick(timeMs: Long, elapsedRealtimeNanos: Long): FingerFrame {
        val commands = machine.tick(timeMs)
        return publish(
            LocatedSample(timeMs, elapsedRealtimeNanos, null, 0f, 0f, 0f, 1f, emptyList()),
            commands,
            logMotion = false,
        )
    }

    fun resetLog() {
        recorder.clear()
    }

    private fun zoneSample(sample: LocatedSample): ZoneSample {
        val zone = zoneIndexAt(sample.xMm, sample.zoneWidthMm, sample.zones.size)
        val on = zone?.let { sample.zones.getOrNull(it) } == true
        val penetration = if (zone == null) {
            0f
        } else {
            penetrationForTransition(sample.xMm, sample.zoneWidthMm, zone, machine.latchedZone)
        }
        return ZoneSample(sample.timeMs, zone, on, penetration)
    }

    private fun publish(sample: LocatedSample, commands: List<HapticCommand>, logMotion: Boolean): FingerFrame {
        var failed = false
        commands.forEach { command ->
            when (command) {
                is HapticCommand.StartHold -> {
                    val result = engine.playOneShot(command.durationMs, command.amplitude)
                    if (!result.accepted) {
                        machine.forceOff()
                        message = result.message
                        failed = true
                    }
                    recorder.log(
                        LogEvent(
                            elapsedRealtimeNanos = result.commandedAtElapsedRealtimeNanos,
                            kind = SessionRecorder.KIND_VIBRATION_COMMAND,
                            requestedVibrationOn = result.accepted,
                            zoneIndex = machine.latchedZone,
                            detail = "start refresh=${command.refresh} duration=${command.durationMs} amplitude=${command.amplitude} ${result.message}",
                        ),
                    )
                    if (isStateTransition(command)) boundaries.onRequestedTransition()
                }
                HapticCommand.Stop -> {
                    val at = SystemClock.elapsedRealtimeNanos()
                    engine.stop()
                    recorder.log(
                        LogEvent(
                            elapsedRealtimeNanos = at,
                            kind = SessionRecorder.KIND_VIBRATION_COMMAND,
                            requestedVibrationOn = false,
                            zoneIndex = machine.latchedZone,
                            detail = "stop",
                        ),
                    )
                    boundaries.onRequestedTransition()
                }
            }
        }
        if (failed) engine.stop()
        val zone = machine.latchedZone
        if (zone != lastLoggedZone && sample.zones.isNotEmpty()) {
            boundaries.onZone(zone, sample.elapsedRealtimeNanos, sample.zones.size)
            recorder.log(
                LogEvent(
                    elapsedRealtimeNanos = sample.elapsedRealtimeNanos,
                    motionEventElapsedRealtimeNanos = sample.motionEventElapsedRealtimeNanos,
                    kind = SessionRecorder.KIND_ZONE,
                    xPx = sample.xPx,
                    yPx = sample.yPx,
                    xMm = sample.xMm,
                    speedMmPerSec = speed.instantaneousMmPerSec,
                    zoneIndex = zone,
                    requestedVibrationOn = machine.requestedOn,
                ),
            )
            lastLoggedZone = zone
        }
        if (logMotion) {
            recorder.logMotion(
                LogEvent(
                    elapsedRealtimeNanos = sample.elapsedRealtimeNanos,
                    motionEventElapsedRealtimeNanos = sample.motionEventElapsedRealtimeNanos,
                    kind = SessionRecorder.KIND_MOTION,
                    xPx = sample.xPx,
                    yPx = sample.yPx,
                    xMm = sample.xMm,
                    speedMmPerSec = speed.instantaneousMmPerSec,
                    zoneIndex = zone,
                    requestedVibrationOn = machine.requestedOn,
                ),
            )
        }
        return frame()
    }

    private fun frame(): FingerFrame = FingerFrame(
        zone = machine.latchedZone,
        requestedOn = machine.requestedOn,
        speedMmPerSec = speed.instantaneousMmPerSec,
        averageMmPerSec = speed.averageMmPerSec,
        maxMmPerSec = speed.maxMmPerSec,
        rawCrossings = boundaries.rawCrossings,
        transitions = boundaries.requestedTransitions,
        passDone = boundaries.passEndNs != null,
        durationMs = boundaries.durationMillis(),
        switchHz = boundaries.requestedSwitchHz(),
        message = message,
        budgetExhausted = machine.budgetExhausted,
    )
}
