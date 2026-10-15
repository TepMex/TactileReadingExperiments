package com.tepmex.vibealphabet.core

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternTest {
    @Test
    fun defaultsMatchTheTimingAlphabetAtEveryWindow() {
        for (window in allowedWindowMillis) {
            for (signal in SignalId.entries) {
                val pattern = defaultPattern(signal, window)
                val waveform = waveformFor(signal, window)
                assertEquals(waveform.timingsMs, pattern.toWaveform().timingsMs)
                assertEquals(waveform.amplitudes, pattern.toWaveform().amplitudes)
                assertEquals(window, pattern.totalMs)
                assertEquals(pattern, scalePattern(defaultPattern(signal, REFERENCE_WINDOW_MS), window))
            }
        }
    }

    @Test
    fun durationEditKeepsTheWindowAndMovesTimeToNeighbors() {
        val pattern = decode("+20,-50,+30")
        assertEquals(decode("+40,-30,+30"), setSegmentDuration(pattern, 0, 40))
        assertEquals(decode("+90,-1,+9"), setSegmentDuration(pattern, 0, 90))
        assertEquals(decode("+19,-1,+80"), setSegmentDuration(pattern, 2, 80))
        assertEquals(decode("+20,-1,+79"), setSegmentDuration(pattern, 1, 1))
        assertEquals(decode("+1,-98,+1"), setSegmentDuration(pattern, 1, 500))
        assertEquals(decode("+20,-1,+79"), setSegmentDuration(pattern, 1, 0))
        assertEquals(pattern, setSegmentDuration(pattern, 0, 20))
        assertEquals(decode("+100"), setSegmentDuration(decode("+100"), 0, 40))
    }

    @Test
    fun toggleMergesMatchingNeighbors() {
        assertEquals(decode("-100"), toggleSegment(decode("+20,-80"), 0))
        assertEquals(decode("+100"), toggleSegment(decode("+20,-40,+40"), 1))
        assertEquals(decode("+100"), toggleSegment(decode("+50,-30,+20"), 1))
        assertEquals(decode("-100"), toggleSegment(decode("+100"), 0))
    }

    @Test
    fun addAppendsTheOppositeInterval() {
        assertEquals(decode("+20,-40,+40"), addSegment(decode("+20,-80")))
        assertEquals(decode("+50,-50"), addSegment(decode("+100")))
        assertEquals(decode("-50,+50"), addSegment(decode("-100")))
        assertEquals(decode("+3,-1,+2"), addSegment(decode("+5,-1")))
        val full = decode("+1,-1,+1")
        assertFalse(canAddSegment(full))
        assertEquals(full, addSegment(full))
    }

    @Test
    fun splitInsertsTheOppositeIntervalInTheMiddle() {
        assertEquals(decode("+30,-30,+30,-10"), splitSegment(decode("+90,-10"), 0))
        assertEquals(decode("+20,-27,+26,-27"), splitSegment(decode("+20,-80"), 1))
        assertEquals(decode("+1,-1,+2"), splitSegment(decode("+4"), 0))
        assertEquals(decode("+2"), splitSegment(decode("+2"), 0))
    }

    @Test
    fun removeGivesTimeToANeighborAndMerges() {
        assertEquals(decode("+20,-80"), removeSegment(decode("+20,-40,+40"), 2))
        assertEquals(decode("+100"), removeSegment(decode("+20,-40,+40"), 1))
        assertEquals(decode("-100"), removeSegment(decode("+20,-80"), 0))
        assertEquals(decode("+40"), removeSegment(decode("+40"), 0))
    }

    @Test
    fun scaleFillsEveryWindowWithoutDroppingBelowOneMillisecond() {
        assertEquals(decode("+15,-35"), scalePattern(decode("+30,-70"), 50))
        assertEquals(decode("+150,-350"), scalePattern(decode("+30,-70"), 500))
        assertEquals(decode("+4,-3,+3"), scalePattern(decode("+1,-1,+1"), 10))
        val random = Random(1)
        repeat(40) {
            val window = allowedWindowMillis.random(random)
            val pattern = randomPattern(random, window)
            for (target in allowedWindowMillis) {
                val scaled = scalePattern(pattern, target)
                assertEquals(target, scaled.totalMs)
                assertTrue(scaled.segments.all { it.durationMs >= 1 })
                assertTrue(scaled.segments.size <= target)
                assertTrue(scaled.segments.zipWithNext().all { (left, right) -> left.on != right.on })
            }
        }
    }

    @Test
    fun tooManySegmentsMergeUntilTheyFitTheWindow() {
        val pieces = List(60) { index -> Segment(on = index % 2 == 0, durationMs = 1) }
        val scaled = scalePattern(Pattern(pieces), 50)
        assertEquals(50, scaled.totalMs)
        assertTrue(scaled.segments.size <= 50)
        assertTrue(scaled.segments.all { it.durationMs >= 1 })
    }

    @Test
    fun codecRoundTripsAndRejectsBrokenText() {
        val pattern = decode("+20,-18,+20,-18,+20,-4")
        assertEquals(pattern, decodePattern(encodePattern(pattern)))
        assertEquals(decode("+50,-50"), decodePattern("+20,+30,-50"))
        assertEquals(decode("-40,+60"), decodePattern("-40,+60"))
        for (raw in listOf("", "20,-80", "+0,-100", "+20,", "+", "+20,-", "++20", "+60001")) {
            assertNull(raw, decodePattern(raw))
        }
    }

    @Test
    fun captionAllowsASignalThatStartsWithSilence() {
        assertEquals(
            "40 мс выключен, 60 мс включён",
            formatSegments(decode("-40,+60").toWaveform()),
        )
    }

    @Test
    fun bookStoresEditsScalesThemAndDropsTheReference() {
        var book = PatternBook(100)
        assertFalse(book.isEdited(SignalId.SHORT))
        assertEquals(defaultPattern(SignalId.SHORT, 100), book.pattern(SignalId.SHORT))

        book = book.withPattern(SignalId.SHORT, decode("+30,-70"))
        assertTrue(book.isEdited(SignalId.SHORT))
        assertEquals(decode("+30,-70"), book.pattern(SignalId.SHORT))
        assertEquals(defaultPattern(SignalId.LONG, 100), book.pattern(SignalId.LONG))

        book = book.withWindow(200)
        assertEquals(200, book.windowMs)
        assertEquals(decode("+60,-140"), book.pattern(SignalId.SHORT))
        assertEquals(defaultPattern(SignalId.LONG, 200), book.pattern(SignalId.LONG))
        assertFalse(book.isEdited(SignalId.LONG))

        book = book.withPattern(SignalId.SHORT, defaultPattern(SignalId.SHORT, 200))
        assertFalse(book.isEdited(SignalId.SHORT))
        assertEquals(book, book.withWindow(200))
    }

    @Test
    fun storedBookScalesIntoTheSavedWindowAndIgnoresGarbage() {
        val book = patternBookFromStored(
            windowMs = 200,
            encodedOverrides = mapOf(
                SignalId.SHORT to "+30,-70",
                SignalId.DOUBLE to "+20,-50,+20,-10",
                SignalId.MEDIUM to "сломан",
                SignalId.LONG to "+90,-10",
            ),
        )
        assertEquals(200, book.windowMs)
        assertEquals(decode("+60,-140"), book.pattern(SignalId.SHORT))
        assertFalse(book.isEdited(SignalId.DOUBLE))
        assertFalse(book.isEdited(SignalId.MEDIUM))
        assertEquals(defaultPattern(SignalId.LONG, 200), book.pattern(SignalId.LONG))
        assertFalse(book.isEdited(SignalId.LONG))

        val fallback = patternBookFromStored(75, mapOf(SignalId.SHORT to "+10,-40"))
        assertEquals(DEFAULT_WINDOW_MS, fallback.windowMs)
        assertFalse(fallback.isEdited(SignalId.SHORT))
    }

    @Test
    fun directConstructionRejectsAGaplessPairAndAnEmptyPattern() {
        val samePolarity = runCatching {
            Pattern(listOf(Segment(true, 10), Segment(true, 10)))
        }.exceptionOrNull()
        assertTrue(samePolarity is IllegalArgumentException)
        val empty = runCatching { Pattern(emptyList()) }.exceptionOrNull()
        assertTrue(empty is IllegalArgumentException)
        val zero = runCatching { Segment(true, 0) }.exceptionOrNull()
        assertTrue(zero is IllegalArgumentException)
    }

    private fun decode(raw: String): Pattern = decodePattern(raw) ?: error(raw)

    private fun randomPattern(random: Random, window: Int): Pattern {
        val count = random.nextInt(1, minOf(window, 12) + 1)
        val durations = MutableList(count) { 1 }
        var left = window - count
        while (left > 0) {
            val index = random.nextInt(count)
            val give = random.nextInt(1, left + 1)
            durations[index] += give
            left -= give
        }
        val startOn = random.nextBoolean()
        return Pattern(
            List(count) { index ->
                Segment(on = startOn xor (index % 2 == 1), durationMs = durations[index])
            },
        )
    }
}
