package com.tepmex.vibealphabet.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlphabetTest {
    @Test
    fun referenceWindowMatchesTheStartingAlphabet() {
        expect(SignalId.WEAK_TAP, 100, listOf(20L, 80L), listOf(90, 0))
        expect(SignalId.STRONG_TAP, 100, listOf(20L, 80L), listOf(255, 0))
        expect(SignalId.WEAK_SUSTAINED, 100, listOf(90L, 10L), listOf(90, 0))
        expect(SignalId.STRONG_SUSTAINED, 100, listOf(90L, 10L), listOf(255, 0))
        expect(SignalId.WEAK_DOUBLE, 100, listOf(20L, 50L, 20L, 10L), listOf(90, 0, 90, 0))
        expect(SignalId.STRONG_DOUBLE, 100, listOf(20L, 50L, 20L, 10L), listOf(255, 0, 255, 0))
    }

    @Test
    fun everyAllowedWindowScalesExactlyAndKeepsAmplitude() {
        val windows = allowedWindowMillis
        assertEquals(listOf(50, 100, 150, 200, 250, 300, 350, 400, 450, 500), windows)
        for (window in windows) {
            for (signal in SignalId.entries) {
                val waveform = waveformFor(signal, window)
                val reference = waveformFor(signal, REFERENCE_WINDOW_MS)
                assertEquals(window.toLong(), waveform.totalMs)
                assertEquals(reference.amplitudes, waveform.amplitudes)
                assertEquals(reference.timingsMs.size, waveform.timingsMs.size)
                reference.timingsMs.zip(waveform.timingsMs).forEach { (base, scaled) ->
                    assertEquals(base * window / REFERENCE_WINDOW_MS, scaled)
                }
            }
        }
    }

    @Test
    fun halfAndFiveTimesTheReferenceStayOnThePattern() {
        expect(SignalId.WEAK_TAP, 50, listOf(10L, 40L), listOf(90, 0))
        expect(SignalId.STRONG_DOUBLE, 50, listOf(10L, 25L, 10L, 5L), listOf(255, 0, 255, 0))
        expect(SignalId.WEAK_SUSTAINED, 500, listOf(450L, 50L), listOf(90, 0))
        expect(SignalId.STRONG_TAP, 250, listOf(50L, 200L), listOf(255, 0))
    }

    @Test
    fun sameShapeSharesTimingsAndDiffersOnlyByForce() {
        for (window in allowedWindowMillis) {
            for (shape in Shape.entries) {
                val pair = SignalId.entries.filter { it.shape == shape }
                assertEquals(2, pair.size)
                val weak = waveformFor(pair.first { it.strength == Strength.WEAK }, window)
                val strong = waveformFor(pair.first { it.strength == Strength.STRONG }, window)
                assertEquals(weak.timingsMs, strong.timingsMs)
                assertTrue(weak.amplitudes.all { it == 0 || it == WEAK_AMPLITUDE })
                assertTrue(strong.amplitudes.all { it == 0 || it == STRONG_AMPLITUDE })
            }
        }
    }

    @Test
    fun rejectedWindowDoesNotPlay() {
        for (ms in listOf(0, 49, 75, 99, 501, 1000, -100)) {
            val error = runCatching { waveformFor(SignalId.WEAK_TAP, ms) }.exceptionOrNull()
            assertTrue(ms.toString(), error is IllegalArgumentException)
        }
    }

    @Test
    fun storedWindowFallsBackToOneHundred() {
        assertEquals(100, normalizeWindow(100))
        assertEquals(500, normalizeWindow(500))
        assertEquals(DEFAULT_WINDOW_MS, normalizeWindow(75))
        assertEquals(DEFAULT_WINDOW_MS, normalizeWindow(0))
    }

    @Test
    fun segmentCaptionNamesSilence() {
        val caption = formatSegments(waveformFor(SignalId.WEAK_DOUBLE, 100))
        assertEquals("20 мс · 90, 50 мс тишина, 20 мс · 90, 10 мс тишина", caption)
    }

    @Test
    fun titlesAreNumbersOneThroughSix() {
        assertEquals(listOf("1", "2", "3", "4", "5", "6"), SignalId.entries.map { it.title })
    }

    private fun expect(
        signal: SignalId,
        windowMs: Int,
        timings: List<Long>,
        amplitudes: List<Int>,
    ) {
        val waveform = waveformFor(signal, windowMs)
        assertEquals(timings, waveform.timingsMs)
        assertEquals(amplitudes, waveform.amplitudes)
    }
}
