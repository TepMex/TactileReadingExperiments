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
 * Испытание сигналов. Правила те же, что у [DrillMachine]:
 * до ответа повтор отдаёт тот же сигнал, после ответа следующее нажатие начинает заново.
 */
class TestMachine(
    nextSignal: () -> SignalId,
) {
    constructor(random: Random = Random.Default) : this({
        SignalId.entries[random.nextInt(SignalId.entries.size)]
    })

    private val drill = DrillMachine(nextSignal)

    val phase: TestPhase
        get() = when (val current = drill.phase) {
            DrillPhase.Idle -> TestPhase.Idle
            is DrillPhase.Prompt -> TestPhase.Prompt(current.item)
            is DrillPhase.Result -> TestPhase.Result(current.item, current.choice)
        }

    fun onBlankPressed(): SignalId = drill.onBlankPressed()

    fun answer(choice: SignalId): TestPhase.Result {
        val result = try {
            drill.answer(choice)
        } catch (error: IllegalStateException) {
            error("Ответ принимается после сигнала, до него выбора нет")
        }
        return TestPhase.Result(result.item, result.choice)
    }
}
