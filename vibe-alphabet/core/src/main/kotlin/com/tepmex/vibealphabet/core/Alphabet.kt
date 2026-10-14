package com.tepmex.vibealphabet.core

const val REFERENCE_WINDOW_MS = 100
const val DEFAULT_WINDOW_MS = 100
const val MIN_WINDOW_MS = 50
const val MAX_WINDOW_MS = 500
const val WINDOW_STEP_MS = 50

/** Включённый участок. Телефон не умеет менять силу, поэтому амплитуда всегда полная. */
const val ON_AMPLITUDE = 255

enum class SignalId(
    val title: String,
    val patternName: String,
    val sensation: String,
    val baseTimingsMs: List<Int>,
) {
    SHORT("1", "Короткий", "Тычок", listOf(20, 80)),
    DOUBLE("2", "Двойной", "Два тычка", listOf(20, 50, 20, 10)),
    // Тот же тычок 20 мс, что у одного и двух. Две паузы равны.
    // 18 и хвост 4 — самые длинные чётные паузы в остатке 40 мс:
    // нечётные 15 мс с хвостом 10 мс на окне 50 мс дали бы сумму 49.
    TRIPLE("3", "Тройной", "Три тычка", listOf(20, 18, 20, 18, 20, 4)),
    MEDIUM("4", "Средний", "Короткое гудение", listOf(50, 50)),
    LONG("5", "Длинный", "Протяжённое гудение", listOf(90, 10)),
    LONG_SHORT("6", "Длинный–короткий", "Гудение, затем тычок", listOf(50, 20, 20, 10)),
    ;

    init {
        require(baseTimingsMs.sum() == REFERENCE_WINDOW_MS) {
            "Эталон «$patternName» должен занимать $REFERENCE_WINDOW_MS мс"
        }
        require(baseTimingsMs.all { it > 0 }) { "Участок «$patternName» должен быть длиннее 0 мс" }
    }
}

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
 * Чётный участок включён на полной силе, нечётный выключен. Амплитуда не масштабируется.
 */
fun waveformFor(signal: SignalId, windowMs: Int): Waveform {
    require(windowMs in allowedWindowMillis) {
        "Окно $windowMs мс недопустимо. Нужно от $MIN_WINDOW_MS до $MAX_WINDOW_MS с шагом $WINDOW_STEP_MS"
    }
    val timings = signal.baseTimingsMs.map { segment ->
        segment.toLong() * windowMs / REFERENCE_WINDOW_MS
    }
    val amplitudes = timings.indices.map { index -> if (index % 2 == 0) ON_AMPLITUDE else 0 }
    val waveform = Waveform(timings, amplitudes)
    check(waveform.totalMs == windowMs.toLong()) {
        "Сумма участков ${waveform.totalMs} мс не равна окну $windowMs мс"
    }
    return waveform
}

/** Подпись команды: «20 мс включён, 80 мс выключен». */
fun formatSegments(waveform: Waveform): String =
    waveform.timingsMs.zip(waveform.amplitudes).joinToString(", ") { (duration, amplitude) ->
        if (amplitude == 0) "$duration мс выключен" else "$duration мс включён"
    }
