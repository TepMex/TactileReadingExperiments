package com.tepmex.vibealphabet.core

/** Что спрашивает экран теста. В память пишется [storageKey], не имя константы. */
enum class DrillSubject(val storageKey: String) {
    Signals("signals"),
    Symbols("symbols"),
    ;

    companion object {
        fun fromStorage(raw: String?): DrillSubject =
            entries.firstOrNull { it.storageKey == raw } ?: Signals
    }
}

sealed interface DrillPhase<out T> {
    data object Idle : DrillPhase<Nothing>

    data class Prompt<T>(val item: T) : DrillPhase<T>

    data class Result<T>(val item: T, val choice: T) : DrillPhase<T> {
        val correct: Boolean get() = item == choice
    }
}

/**
 * Одно испытание за раз на любом наборе.
 * До ответа повторное нажатие круглой кнопки отдаёт тот же элемент.
 * После ответа следующее нажатие начинает испытание заново.
 */
class DrillMachine<T>(
    private val nextItem: () -> T,
) {
    var phase: DrillPhase<T> = DrillPhase.Idle
        private set

    fun reset() {
        phase = DrillPhase.Idle
    }

    fun onBlankPressed(): T = when (val current = phase) {
        is DrillPhase.Prompt -> current.item
        is DrillPhase.Idle, is DrillPhase.Result -> {
            val item = nextItem()
            phase = DrillPhase.Prompt(item)
            item
        }
    }

    fun answer(choice: T): DrillPhase.Result<T> {
        val prompt = phase as? DrillPhase.Prompt
            ?: error("Ответ принимается после предъявления, до него выбора нет")
        val result = DrillPhase.Result(prompt.item, choice)
        phase = result
        return result
    }
}
