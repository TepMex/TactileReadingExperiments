package com.tepmex.vibealphabet.core

enum class Strength {
    WEAK,
    STRONG,
}

enum class Shape {
    TAP,
    SUSTAINED,
    DOUBLE,
}

enum class SignalId(
    val title: String,
    val shape: Shape,
    val strength: Strength,
) {
    WEAK_TAP("Слабый короткий удар", Shape.TAP, Strength.WEAK),
    STRONG_TAP("Сильный короткий удар", Shape.TAP, Strength.STRONG),
    WEAK_SUSTAINED("Слабая протяжённая вибрация", Shape.SUSTAINED, Strength.WEAK),
    STRONG_SUSTAINED("Сильная протяжённая вибрация", Shape.SUSTAINED, Strength.STRONG),
    WEAK_DOUBLE("Два слабых удара", Shape.DOUBLE, Strength.WEAK),
    STRONG_DOUBLE("Два сильных удара", Shape.DOUBLE, Strength.STRONG),
}

const val REFERENCE_WINDOW_MS = 100
const val DEFAULT_WINDOW_MS = 100
const val MIN_WINDOW_MS = 50
const val MAX_WINDOW_MS = 500
const val WINDOW_STEP_MS = 50
const val WEAK_AMPLITUDE = 90
const val STRONG_AMPLITUDE = 255

val allowedWindowMillis: List<Int> =
    (MIN_WINDOW_MS..MAX_WINDOW_MS step WINDOW_STEP_MS).toList()

fun normalizeWindow(ms: Int): Int = if (ms in allowedWindowMillis) ms else DEFAULT_WINDOW_MS

data class Waveform(
    val timingsMs: List<Long>,
    val amplitudes: List<Int>,
) {
    init {
        require(timingsMs.isNotEmpty()) { "В сигнале нет участков" }
        require(timingsMs.size == amplitudes.size) { "Длительности и амплитуды разной длины" }
        require(timingsMs.all { it > 0L }) { "Участок должен быть длиннее 0 мс" }
        require(amplitudes.all { it in 0..255 }) { "Амплитуда должна быть от 0 до 255" }
    }

    val totalMs: Long get() = timingsMs.sum()
}

/**
 * Команда мотору на всё окно, включая хвостовую тишину.
 * Эталон — 100 мс. Другое разрешённое окно умножает каждый участок на окно/100.
 * Амплитуды не масштабируются.
 */
fun waveformFor(signal: SignalId, windowMs: Int): Waveform {
    require(windowMs in allowedWindowMillis) {
        "Окно $windowMs мс недопустимо. Нужно от $MIN_WINDOW_MS до $MAX_WINDOW_MS с шагом $WINDOW_STEP_MS"
    }
    val timings = baseTimings(signal.shape).map { segment ->
        segment.toLong() * windowMs / REFERENCE_WINDOW_MS
    }
    val level = if (signal.strength == Strength.STRONG) STRONG_AMPLITUDE else WEAK_AMPLITUDE
    val amplitudes = timings.indices.map { index -> if (index % 2 == 0) level else 0 }
    val waveform = Waveform(timings, amplitudes)
    check(waveform.totalMs == windowMs.toLong()) {
        "Сумма участков ${waveform.totalMs} мс не равна окну $windowMs мс"
    }
    return waveform
}

fun shapeTitle(shape: Shape): String = when (shape) {
    Shape.TAP -> "Короткий удар"
    Shape.SUSTAINED -> "Протяжённая вибрация"
    Shape.DOUBLE -> "Двойной удар"
}

/** Подпись команды: «20 мс · 90, 80 мс тишина». */
fun formatSegments(waveform: Waveform): String =
    waveform.timingsMs.zip(waveform.amplitudes).joinToString(", ") { (duration, amplitude) ->
        if (amplitude == 0) "$duration мс тишина" else "$duration мс · $amplitude"
    }

private fun baseTimings(shape: Shape): List<Int> = when (shape) {
    Shape.TAP -> listOf(20, 80)
    Shape.SUSTAINED -> listOf(90, 10)
    Shape.DOUBLE -> listOf(20, 50, 20, 10)
}
