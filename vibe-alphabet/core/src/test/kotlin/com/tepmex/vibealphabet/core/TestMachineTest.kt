package com.tepmex.vibealphabet.core

import java.util.ArrayDeque
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestMachineTest {
    @Test
    fun blankPressReplaysUntilTheAnswer() {
        val machine = TestMachine { SignalId.LONG }
        assertEquals(TestPhase.Idle, machine.phase)
        assertEquals(SignalId.LONG, machine.onBlankPressed())
        assertEquals(TestPhase.Prompt(SignalId.LONG), machine.phase)
        assertEquals(SignalId.LONG, machine.onBlankPressed())
        assertEquals(TestPhase.Prompt(SignalId.LONG), machine.phase)
    }

    @Test
    fun answerGradesAndTheNextPressStartsOver() {
        val queue = ArrayDeque(listOf(SignalId.SHORT, SignalId.LONG_SHORT))
        val machine = TestMachine { queue.removeFirst() }

        val played = machine.onBlankPressed()
        val wrong = machine.answer(SignalId.MEDIUM)
        assertFalse(wrong.correct)
        assertEquals(played, wrong.signal)
        assertEquals(SignalId.MEDIUM, wrong.choice)
        assertEquals(wrong, machine.phase)

        val next = machine.onBlankPressed()
        assertEquals(SignalId.LONG_SHORT, next)
        assertTrue(machine.phase is TestPhase.Prompt)
        val right = machine.answer(SignalId.LONG_SHORT)
        assertTrue(right.correct)
    }

    @Test
    fun answerBeforeASignalIsRejected() {
        val machine = TestMachine { SignalId.SHORT }
        val error = runCatching { machine.answer(SignalId.SHORT) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        machine.onBlankPressed()
        machine.answer(SignalId.SHORT)
        val second = runCatching { machine.answer(SignalId.MEDIUM) }.exceptionOrNull()
        assertTrue(second is IllegalStateException)
    }

    @Test
    fun randomDrawUsesOnlyTheSixSignals() {
        val random = Random(7)
        val machine = TestMachine(random)
        val seen = mutableSetOf<SignalId>()
        repeat(60) {
            val signal = machine.onBlankPressed()
            seen += signal
            machine.answer(signal)
        }
        assertEquals(SignalId.entries.toSet(), seen)
    }
}
