package com.tepmex.vibealphabet.core

import kotlin.random.Random

sealed interface TestPhase {
    data object Idle : TestPhase

    data class Prompt(val signal: SignalId) : TestPhase

    data class Result(val signal: SignalId, val choice: SignalId) : TestPhase {
        val correct: Boolean get() = signal == choice
    }
}

/**
 * Одно испытание за раз.
 * До ответа повторное нажатие круглой кнопки отдаёт тот же сигнал.
 * После ответа следующее нажатие начинает испытание заново.
 */
class TestMachine(
    private val nextSignal: () -> SignalId,
) {
    constructor(random: Random = Random.Default) : this({
        SignalId.entries[random.nextInt(SignalId.entries.size)]
    })

    var phase: TestPhase = TestPhase.Idle
        private set

    fun onBlankPressed(): SignalId = when (val current = phase) {
        is TestPhase.Prompt -> current.signal
        is TestPhase.Idle, is TestPhase.Result -> {
            val signal = nextSignal()
            phase = TestPhase.Prompt(signal)
            signal
        }
    }

    fun answer(choice: SignalId): TestPhase.Result {
        val prompt = phase as? TestPhase.Prompt
            ?: error("Ответ принимается после сигнала, до него выбора нет")
        val result = TestPhase.Result(prompt.signal, choice)
        phase = result
        return result
    }
}
