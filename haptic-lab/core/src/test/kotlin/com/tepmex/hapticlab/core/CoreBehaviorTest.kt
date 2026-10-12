package com.tepmex.hapticlab.core

import com.tepmex.hapticlab.core.alphabet.demoAlphabet
import com.tepmex.hapticlab.core.alphabet.renderText
import com.tepmex.hapticlab.core.export.csvEscape
import com.tepmex.hapticlab.core.export.sessionToCsv
import com.tepmex.hapticlab.core.haptics.freeDecayTo10PercentMillis
import com.tepmex.hapticlab.core.log.DeviceSnapshot
import com.tepmex.hapticlab.core.log.ExperimentSession
import com.tepmex.hapticlab.core.log.LogEvent
import com.tepmex.hapticlab.core.log.SessionRecorder
import com.tepmex.hapticlab.core.metrics.BoundaryCounter
import com.tepmex.hapticlab.core.metrics.SpeedTracker
import com.tepmex.hapticlab.core.psych.AdaptiveStaircase
import com.tepmex.hapticlab.core.psych.TrialAnswer
import com.tepmex.hapticlab.core.psych.TrialSpec
import com.tepmex.hapticlab.core.psych.buildConstantStimuliSchedule
import com.tepmex.hapticlab.core.psych.summarize
import com.tepmex.hapticlab.core.psych.wilson95
import com.tepmex.hapticlab.core.signal.buildDiscriminationWaveform
import com.tepmex.hapticlab.core.signal.buildPulseTrain
import com.tepmex.hapticlab.core.spatial.HapticCommand
import com.tepmex.hapticlab.core.spatial.PatternPreset
import com.tepmex.hapticlab.core.spatial.SpatialVibrationMachine
import com.tepmex.hapticlab.core.spatial.ZoneSample
import com.tepmex.hapticlab.core.spatial.alternatingPattern
import com.tepmex.hapticlab.core.spatial.boundaryCrossings
import com.tepmex.hapticlab.core.spatial.calibratedMmPerPx
import com.tepmex.hapticlab.core.spatial.isStateTransition
import com.tepmex.hapticlab.core.spatial.penetrationForTransition
import com.tepmex.hapticlab.core.spatial.presetPattern
import com.tepmex.hapticlab.core.spatial.scaleFromMeasuredWidth
import com.tepmex.hapticlab.core.spatial.zoneIndexAt
import com.tepmex.hapticlab.core.time.mapUptimeMillisToElapsedRealtimeNanos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CoreBehaviorTest {

    @Test
    fun waveformTrainHasOneSegmentPerPulseAndGap() {
        val spec = buildPulseTrain(onMs = 20, offMs = 10, pulses = 3, amplitude = 200, repetitions = 2)
        assertEquals(listOf(20L, 10L, 20L, 10L, 20L, 10L, 20L, 10L, 20L, 10L, 20L), spec.timingsMs)
        assertEquals(listOf(200, 0, 200, 0, 200, 0, 200, 0, 200, 0, 200), spec.amplitudes)
        assertEquals(170L, spec.totalMs)
        assertFalse(spec.truncated)
    }

    @Test
    fun waveformIsCappedAndMarkedTruncated() {
        val spec = buildPulseTrain(
            onMs = 500,
            offMs = 500,
            pulses = 20,
            amplitude = 255,
            repetitions = 20,
            maxTotalMs = 15_000,
        )
        assertTrue(spec.truncated)
        assertTrue(spec.totalMs <= 15_000)
        assertTrue(spec.amplitudes.first() == 255)
        assertEquals(spec.timingsMs.size, spec.amplitudes.size)
    }

    @Test
    fun discriminationWaveformDistinguishesOneAndTwoPulses() {
        val one = buildDiscriminationWaveform(onMs = 30, gapMs = null, amplitude = 100)
        val two = buildDiscriminationWaveform(onMs = 30, gapMs = 15, amplitude = 100)
        assertEquals(listOf(30L), one.timingsMs)
        assertEquals(listOf(30L, 15L, 30L), two.timingsMs)
        assertEquals(listOf(100, 0, 100), two.amplitudes)
        assertEquals(75L, two.totalMs)
    }

    @Test
    fun presetsMatchDocumentedExamples() {
        assertEquals("1100110011", bits(presetPattern(PatternPreset.THREE_PULSES, 10)))
        assertEquals("1111001111", bits(presetPattern(PatternPreset.TWO_LONG, 10)))
        assertEquals("1100001111", bits(presetPattern(PatternPreset.SHORT_LONG, 10)))
        assertEquals("1010101010", bits(presetPattern(PatternPreset.ALTERNATING, 10)))
        assertEquals("11111", bits(presetPattern(PatternPreset.ALL_ON, 5)))
        assertEquals("00000", bits(presetPattern(PatternPreset.ALL_OFF, 5)))
    }

    @Test
    fun speedPatternPeriodAndDuty() {
        assertEquals(listOf(true, true, false, false, true, true), alternatingPattern(6, period = 4, onInPeriod = 2))
    }

    @Test
    fun zoneIndexRejectsOutsideTrack() {
        assertNull(zoneIndexAt(-0.1f, zoneWidthMm = 5f, zoneCount = 4))
        assertNull(zoneIndexAt(20f, zoneWidthMm = 5f, zoneCount = 4))
        assertEquals(0, zoneIndexAt(0f, 5f, 4))
        assertEquals(3, zoneIndexAt(19.9f, 5f, 4))
        assertEquals(1, zoneIndexAt(5f, 5f, 4))
    }

    @Test
    fun penetrationIsMeasuredFromTheBoundaryThatWasCrossed() {
        val intoNext = penetrationForTransition(xMm = 10.4f, zoneWidthMm = 10f, zone = 1, previousLatched = 0)
        assertEquals(0.4f, intoNext, 0.001f)
        val backward = penetrationForTransition(xMm = 9.2f, zoneWidthMm = 10f, zone = 0, previousLatched = 1)
        assertEquals(0.8f, backward, 0.001f)
        assertEquals(2, boundaryCrossings(1, 3))
    }

    @Test
    fun stateMachineDoesNotRestartInsideTheSameOnZone() {
        val machine = SpatialVibrationMachine(debounceMm = 1f, maxHoldMs = 2_000, refreshLeadMs = 500)
        val first = machine.down(ZoneSample(timeMs = 0, zoneIndex = 0, zoneIsOn = true, penetrationMm = 5f))
        assertEquals(1, first.size)
        val start = first.single() as HapticCommand.StartHold
        assertFalse(start.refresh)
        assertTrue(machine.requestedOn)

        val inside = machine.move(ZoneSample(timeMs = 100, zoneIndex = 0, zoneIsOn = true, penetrationMm = 4f))
        assertTrue(inside.isEmpty())

        val earlyTick = machine.tick(timeMs = 1_000)
        assertTrue(earlyTick.isEmpty())
    }

    @Test
    fun debounceCanBeDisabledAndFiltersBoundaryChatter() {
        val filtered = SpatialVibrationMachine(debounceMm = 1f)
        filtered.down(ZoneSample(0, zoneIndex = 0, zoneIsOn = true, penetrationMm = 5f))
        val chatter = filtered.move(ZoneSample(50, zoneIndex = 1, zoneIsOn = false, penetrationMm = 0.3f))
        assertTrue(chatter.none { isStateTransition(it) })
        assertEquals(0, filtered.latchedZone)
        assertTrue(filtered.requestedOn)

        val committed = filtered.move(ZoneSample(80, zoneIndex = 1, zoneIsOn = false, penetrationMm = 1.2f))
        assertEquals(listOf(HapticCommand.Stop), committed.filter { isStateTransition(it) })
        assertFalse(filtered.requestedOn)

        val immediate = SpatialVibrationMachine(debounceMm = 0f)
        immediate.down(ZoneSample(0, zoneIndex = 1, zoneIsOn = false, penetrationMm = 0f))
        val switched = immediate.move(ZoneSample(10, zoneIndex = 2, zoneIsOn = true, penetrationMm = 0f))
        assertTrue(switched.filterIsInstance<HapticCommand.StartHold>().any { !it.refresh })
        assertEquals(2, immediate.latchedZone)
    }

    @Test
    fun adjacentOnZonesDoNotRestartAndLiftAlwaysStops() {
        val machine = SpatialVibrationMachine(debounceMm = 0f)
        machine.down(ZoneSample(0, 0, zoneIsOn = true, penetrationMm = 2f))
        val nextOn = machine.move(ZoneSample(30, 1, zoneIsOn = true, penetrationMm = 2f))
        assertTrue(nextOn.none { it is HapticCommand.StartHold && !it.refresh })
        assertTrue(machine.requestedOn)

        val up = machine.up(timeMs = 40)
        assertEquals(listOf(HapticCommand.Stop), up)
        assertFalse(machine.fingerDown)
        assertTrue(machine.up(timeMs = 50).isEmpty())
    }

    @Test
    fun holdRefreshesOnlyNearTheDeadlineAndBudgetStopsIt() {
        val machine = SpatialVibrationMachine(
            debounceMm = 0f,
            maxHoldMs = 1_000,
            refreshLeadMs = 200,
            touchBudgetMs = 1_500,
        )
        machine.down(ZoneSample(0, 0, true, 2f))
        assertTrue(machine.tick(700).isEmpty())
        val refresh = machine.tick(850)
        val command = refresh.single() as HapticCommand.StartHold
        assertTrue(command.refresh)
        assertFalse(isStateTransition(command))

        val exhausted = machine.tick(1_500)
        assertEquals(listOf(HapticCommand.Stop), exhausted)
        assertTrue(machine.budgetExhausted)
        assertTrue(machine.tick(1_600).isEmpty())
    }

    @Test
    fun leavingTheTrackStopsVibration() {
        val machine = SpatialVibrationMachine(debounceMm = 0f)
        machine.down(ZoneSample(0, 0, true, 1f))
        val off = machine.move(ZoneSample(20, zoneIndex = null, zoneIsOn = false, penetrationMm = 0f))
        assertEquals(listOf(HapticCommand.Stop), off)
    }

    @Test
    fun wilsonIntervalMatchesReferenceAndStaysInsideUnitInterval() {
        val estimate = wilson95(k = 15, n = 20)!!
        assertEquals(0.75, estimate.proportion, 1e-9)
        assertEquals(0.5312991224, estimate.lower95, 1e-6)
        assertEquals(0.8881382986, estimate.upper95, 1e-6)
        assertTrue(estimate.lower95 <= estimate.proportion)
        assertTrue(estimate.upper95 >= estimate.proportion)
        assertNull(wilson95(0, 0))
    }

    @Test
    fun scheduleIsRandomButContainsControlsAndAllGaps() {
        val schedule = buildConstantStimuliSchedule(repetitions = 3, random = Random(7))
        assertEquals(30, schedule.size)
        assertEquals(3, schedule.count { it.isControl })
        listOf(10, 15, 20, 30, 40, 50, 75, 100, 150).forEach { gap ->
            assertEquals(3, schedule.count { !it.isControl && it.gapMs == gap })
        }
        assertEquals(schedule.indices.toList(), schedule.map { it.index })
        val again = buildConstantStimuliSchedule(repetitions = 3, random = Random(7))
        assertEquals(schedule.map { it.gapMs to it.isControl }, again.map { it.gapMs to it.isControl })
    }

    @Test
    fun summaryCountsHitsAndFalseTwos() {
        val answers = listOf(
            answer(gap = 10, control = false, two = true),
            answer(gap = 10, control = false, two = false),
            answer(gap = null, control = true, two = true),
            answer(gap = null, control = true, two = false),
        )
        val summary = summarize(answers)
        val ten = summary.rows.single { it.gapMs == 10 }
        assertEquals(1, ten.correct)
        assertEquals(2, ten.trials)
        assertEquals(1, summary.controlFalseTwos)
        assertEquals(0.5, summary.falseTwoRate!!.proportion, 1e-9)
    }

    @Test
    fun staircaseMovesTwoDownOneUpAndRecordsReversals() {
        val stair = AdaptiveStaircase(levels = listOf(10, 20, 40, 80, 100), startIndex = 4)
        assertEquals(100, stair.currentGapMs)
        stair.onSignalAnswer(correct = true)
        assertEquals(100, stair.currentGapMs)
        stair.onSignalAnswer(correct = true)
        assertEquals(80, stair.currentGapMs)
        stair.onSignalAnswer(correct = false)
        assertEquals(100, stair.currentGapMs)
        assertEquals(listOf(80), stair.reversals)
        assertNull(stair.thresholdMs())
    }

    @Test
    fun freeDecayUsesTheRequestedFormulaAndRejectsMissingValues() {
        val millis = freeDecayTo10PercentMillis(q = 10.0, resonantFrequencyHz = 150.0)!!
        assertEquals(48.862, millis, 0.01)
        assertNull(freeDecayTo10PercentMillis(Double.NaN, 150.0))
        assertNull(freeDecayTo10PercentMillis(10.0, 0.0))
    }

    @Test
    fun calibrationScaleUsesMeasuredWidth() {
        assertEquals(1.1f, scaleFromMeasuredWidth(systemWidthMm = 70f, measuredWidthMm = 77f)!!, 0.0001f)
        assertNull(scaleFromMeasuredWidth(0f, 70f))
        val mmPerPx = calibratedMmPerPx(xdpi = 400f, scale = 1f)
        assertEquals(25.4f / 400f, mmPerPx, 0.00001f)
    }

    @Test
    fun motionTimestampIsShiftedOntoElapsedRealtime() {
        val mapped = mapUptimeMillisToElapsedRealtimeNanos(
            eventUptimeMillis = 1_000,
            nowUptimeMillis = 1_050,
            nowElapsedRealtimeNanos = 5_000_000_000,
        )
        assertEquals(4_950_000_000, mapped)
    }

    @Test
    fun speedAndPassDurationIgnoreImplausibleJumps() {
        val speed = SpeedTracker()
        assertNull(speed.sample(0f, 0L))
        val value = speed.sample(10f, 100_000_000L)
        assertEquals(100f, value!!, 0.1f)
        assertNull(speed.sample(10_000f, 110_000_000L))
        assertEquals(100f, speed.maxMmPerSec, 0.1f)

        val pass = BoundaryCounter()
        pass.onZone(0, 1_000_000_000L, zoneCount = 4)
        pass.onZone(1, 1_100_000_000L, zoneCount = 4)
        pass.onZone(3, 1_300_000_000L, zoneCount = 4)
        assertEquals(3, pass.rawCrossings)
        assertEquals(300L, pass.durationMillis())
    }

    @Test
    fun alphabetRendersKnownLettersAndSkipsUnknownWithoutBreakingTheLine() {
        val alphabet = demoAlphabet(7)
        val line = renderText("АВЯ", alphabet, gapZones = 2)
        assertEquals(listOf(0, 9), line.symbolStarts)
        assertEquals(listOf("Я"), line.unknown)
        assertEquals(7 + 2 + 7, line.zones.size)
        assertEquals("1100110", bits(line.zones.take(7)))
    }

    @Test
    fun verboseMotionLogIsThrottledAndCanBeDisabled() {
        val quiet = SessionRecorder(verboseMotion = false)
        quiet.logMotion(LogEvent(elapsedRealtimeNanos = 1, kind = SessionRecorder.KIND_MOTION))
        assertTrue(quiet.snapshot().isEmpty())
        assertEquals(1, quiet.droppedMotionCount())

        val verbose = SessionRecorder(verboseMotion = true, minMotionIntervalNanos = 50_000_000L)
        verbose.logMotion(LogEvent(10, kind = SessionRecorder.KIND_MOTION))
        verbose.logMotion(LogEvent(20_000_000, kind = SessionRecorder.KIND_MOTION))
        verbose.logMotion(LogEvent(80_000_000, kind = SessionRecorder.KIND_MOTION))
        assertEquals(2, verbose.snapshot().size)
    }

    @Test
    fun csvEscapesCommasAndStatesThatTimesAreCommands() {
        assertEquals("\"a,b\"", csvEscape("a,b"))
        assertEquals("\"say \"\"two\"\"\"", csvEscape("say \"two\""))
        val session = ExperimentSession(
            id = "s1",
            type = "psychophysics",
            wallClockIso = "2026-10-12T00:00:00Z",
            startedElapsedRealtimeNanos = 10,
            device = DeviceSnapshot(
                manufacturer = "HONOR",
                model = "400",
                androidRelease = "16",
                sdkInt = 36,
                sdkIntFull = 3600000,
                vibratorCount = 1,
                hasVibrator = true,
                hasAmplitudeControl = true,
                resonantFrequencyHz = null,
                qFactor = null,
                freeDecayT10Ms = null,
                envelopeSupported = false,
            ),
            parameters = mapOf("gap" to "10,15"),
            events = listOf(
                LogEvent(
                    elapsedRealtimeNanos = 20,
                    kind = SessionRecorder.KIND_ANSWER,
                    detail = "response=two",
                ),
            ),
            summary = mapOf("correct" to "1"),
        )
        val csv = sessionToCsv(session)
        assertTrue(csv.contains("не физический отклик"))
        assertTrue(csv.contains("\"10,15\""))
        assertTrue(csv.contains("response=two"))
    }

    private fun bits(zones: List<Boolean>): String = zones.joinToString("") { if (it) "1" else "0" }

    private fun answer(gap: Int?, control: Boolean, two: Boolean) = TrialAnswer(
        spec = TrialSpec(index = 0, gapMs = gap, isControl = control),
        respondedTwo = two,
    )
}
