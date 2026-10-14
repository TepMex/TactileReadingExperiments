package com.tepmex.hapticlab.core.signal

data class WaveformSpec(
    val timingsMs: List<Long>,
    val amplitudes: List<Int>,
    val totalMs: Long,
    val truncated: Boolean,
)

class SignalRequestException(message: String) : IllegalArgumentException(message)

const val MAX_COMMANDED_TRAIN_MS = 15_000L

/**
 * Один waveform: ON/OFF/ON/... без серии отложенных вызовов vibrate().
 * Общая длительность ограничена, чтобы вибрация не шла неопределённо долго.
 */
fun buildPulseTrain(
    onMs: Long,
    offMs: Long,
    pulses: Int,
    amplitude: Int,
    repetitions: Int,
    maxTotalMs: Long = MAX_COMMANDED_TRAIN_MS,
): WaveformSpec {
    if (onMs !in 1L..5_000L) throw SignalRequestException("Длительность включения должна быть от 1 до 5000 мс")
    if (offMs !in 0L..5_000L) throw SignalRequestException("Длительность паузы должна быть от 0 до 5000 мс")
    if (pulses !in 1..20) throw SignalRequestException("Число импульсов должно быть от 1 до 20")
    if (repetitions !in 1..20) throw SignalRequestException("Число повторений должно быть от 1 до 20")
    if (amplitude !in 1..255) throw SignalRequestException("Амплитуда должна быть от 1 до 255")

    val timings = ArrayList<Long>()
    val amplitudes = ArrayList<Int>()
    var total = 0L
    var truncated = false
    val cycles = pulses * repetitions
    for (index in 0 until cycles) {
        if (total + onMs > maxTotalMs) {
            truncated = true
            break
        }
        timings += onMs
        amplitudes += amplitude
        total += onMs
        val needsGap = index != cycles - 1
        if (needsGap) {
            if (total + offMs > maxTotalMs) {
                truncated = true
                break
            }
            timings += offMs
            amplitudes += 0
            total += offMs
        }
    }
    if (timings.isEmpty()) {
        throw SignalRequestException("После ограничения длительности сигнал пуст")
    }
    check(timings.size == amplitudes.size)
    return WaveformSpec(timings, amplitudes, total, truncated)
}

/** Один импульс либо два импульса с заданной паузой. gapMs == null — контрольная проба. */
fun buildDiscriminationWaveform(
    onMs: Long,
    gapMs: Long?,
    amplitude: Int,
): WaveformSpec {
    if (onMs !in 1L..5_000L) throw SignalRequestException("Длительность импульса должна быть от 1 до 5000 мс")
    if (amplitude !in 1..255) throw SignalRequestException("Амплитуда должна быть от 1 до 255")
    if (gapMs == null) {
        return WaveformSpec(listOf(onMs), listOf(amplitude), onMs, truncated = false)
    }
    if (gapMs !in 0L..2_000L) throw SignalRequestException("Интервал должен быть от 0 до 2000 мс")
    return WaveformSpec(
        timingsMs = listOf(onMs, gapMs, onMs),
        amplitudes = listOf(amplitude, 0, amplitude),
        totalMs = onMs + gapMs + onMs,
        truncated = false,
    )
}

/** Три импульса по 40 мс с паузами 40 мс: слышно и длительность, и промежуток. */
fun timedPulseProbe(): WaveformSpec = buildPulseTrain(onMs = 40, offMs = 40, pulses = 3, amplitude = 200, repetitions = 1)

/**
 * Слабый импульс, пауза, сильный импульс.
 * Без управления амплитудой оба ненулевых уровня становятся одинаковым «включено».
 */
fun amplitudeContrastProbe(): WaveformSpec = WaveformSpec(
    timingsMs = listOf(80L, 160L, 80L),
    amplitudes = listOf(48, 0, 220),
    totalMs = 320L,
    truncated = false,
)
