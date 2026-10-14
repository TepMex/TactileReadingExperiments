package com.tepmex.vibealphabet.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlphabetTest {
    @Test
    fun referenceWindowMatchesTheTimingAlphabet() {
        expect(SignalId.SHORT, 100, listOf(20L, 80L))
        expect(SignalId.MEDIUM, 100, listOf(50L, 50L))
        expect(SignalId.LONG, 100, listOf(90L, 10L))
        expect(SignalId.DOUBLE, 100, listOf(20L, 50L, 20L, 10L))
        expect(SignalId.SHORT_LONG, 100, listOf(20L, 20L, 50L, 10L))
        expect(SignalId.LONG_SHORT, 100, listOf(50L, 20L, 20L, 10L))
    }

    @Test
    fun namesMatchTheSixSensations() {
        assertEquals(listOf("1", "2", "3", "4", "5", "6"), SignalId.entries.map { it.title })
        assertEquals(
            listOf(
                "Короткий" to "Тычок",
                "Средний" to "Короткое гудение",
                "Длинный" to "Протяжённое гудение",
                "Двойной" to "Два тычка",
                "Короткий–длинный" to "Тычок, затем гудение",
                "Длинный–короткий" to "Гудение, затем тычок",
            ),
            SignalId.entries.map { it.patternName to it.sensation },
        )
    }

    @Test
    fun everyAllowedWindowScalesExactlyAndStaysFullStrength() {
        val windows = allowedWindowMillis
        assertEquals(listOf(50, 100, 150, 200, 250, 300, 350, 400, 450, 500), windows)
        for (window in windows) {
            val seen = mutableSetOf<List<Long>>()
            for (signal in SignalId.entries) {
                val waveform = waveformFor(signal, window)
                val reference = waveformFor(signal, REFERENCE_WINDOW_MS)
                assertEquals(window.toLong(), waveform.totalMs)
                assertEquals(reference.amplitudes, waveform.amplitudes)
                assertEquals(reference.timingsMs.size, waveform.timingsMs.size)
                assertTrue(seen.add(waveform.timingsMs))
                reference.timingsMs.zip(waveform.timingsMs).forEach { (base, scaled) ->
                    assertEquals(base * window / REFERENCE_WINDOW_MS, scaled)
                }
                waveform.amplitudes.forEachIndexed { index, amplitude ->
                    assertEquals(if (index % 2 == 0) ON_AMPLITUDE else 0, amplitude)
                }
            }
            assertEquals(SignalId.entries.size, seen.size)
        }
    }

    @Test
    fun halfAndFiveTimesTheReferenceStayOnThePattern() {
        expect(SignalId.SHORT, 50, listOf(10L, 40L))
        expect(SignalId.DOUBLE, 50, listOf(10L, 25L, 10L, 5L))
        expect(SignalId.LONG, 500, listOf(450L, 50L))
        expect(SignalId.MEDIUM, 250, listOf(125L, 125L))
        expect(SignalId.SHORT_LONG, 200, listOf(40L, 40L, 100L, 20L))
        expect(SignalId.LONG_SHORT, 150, listOf(75L, 30L, 30L, 15L))
    }

    @Test
    fun rejectedWindowDoesNotPlay() {
        for (ms in listOf(0, 49, 75, 99, 501, 1000, -100)) {
            val error = runCatching { waveformFor(SignalId.SHORT, ms) }.exceptionOrNull()
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
    fun segmentCaptionNamesOnAndOff() {
        assertEquals(
            "20 мс включён, 80 мс выключен",
            formatSegments(waveformFor(SignalId.SHORT, 100)),
        )
        assertEquals(
            "20 мс включён, 20 мс выключен, 50 мс включён, 10 мс выключен",
            formatSegments(waveformFor(SignalId.SHORT_LONG, 100)),
        )
    }

    private fun expect(signal: SignalId, windowMs: Int, timings: List<Long>) {
        val waveform = waveformFor(signal, windowMs)
        assertEquals(timings, waveform.timingsMs)
        assertEquals(List(timings.size) { index -> if (index % 2 == 0) ON_AMPLITUDE else 0 }, waveform.amplitudes)
    }
}
