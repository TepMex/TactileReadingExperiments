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
        val machine = TestMachine { SignalId.WEAK_SUSTAINED }
        assertEquals(TestPhase.Idle, machine.phase)
        assertEquals(SignalId.WEAK_SUSTAINED, machine.onBlankPressed())
        assertEquals(TestPhase.Prompt(SignalId.WEAK_SUSTAINED), machine.phase)
        assertEquals(SignalId.WEAK_SUSTAINED, machine.onBlankPressed())
        assertEquals(TestPhase.Prompt(SignalId.WEAK_SUSTAINED), machine.phase)
    }

    @Test
    fun answerGradesAndTheNextPressStartsOver() {
        val queue = ArrayDeque(listOf(SignalId.WEAK_TAP, SignalId.STRONG_DOUBLE))
        val machine = TestMachine { queue.removeFirst() }

        val played = machine.onBlankPressed()
        val wrong = machine.answer(SignalId.STRONG_TAP)
        assertFalse(wrong.correct)
        assertEquals(played, wrong.signal)
        assertEquals(SignalId.STRONG_TAP, wrong.choice)
        assertEquals(wrong, machine.phase)

        val next = machine.onBlankPressed()
        assertEquals(SignalId.STRONG_DOUBLE, next)
        assertTrue(machine.phase is TestPhase.Prompt)
        val right = machine.answer(SignalId.STRONG_DOUBLE)
        assertTrue(right.correct)
    }

    @Test
    fun answerBeforeASignalIsRejected() {
        val machine = TestMachine { SignalId.WEAK_TAP }
        val error = runCatching { machine.answer(SignalId.WEAK_TAP) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        machine.onBlankPressed()
        machine.answer(SignalId.WEAK_TAP)
        val second = runCatching { machine.answer(SignalId.STRONG_TAP) }.exceptionOrNull()
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
