package com.hapticlab.core.haptics

import kotlin.math.PI
import kotlin.math.ln

/**
 * Теоретическое время свободного затухания амплитуды до 10% для линейного резонатора:
 *
 * A(t) = A0 * exp(-π * f0 * t / Q)
 * t10 = Q * ln(10) / (π * f0)
 *
 * Это математическая оценка свободных колебаний. Она не учитывает активное торможение
 * и не является измеренным временем остановки мотора.
 */
fun freeDecayTo10PercentMillis(q: Double, resonantFrequencyHz: Double): Double? {
    if (!q.isFinite() || !resonantFrequencyHz.isFinite()) return null
    if (q <= 0.0 || resonantFrequencyHz <= 0.0) return null
    val seconds = q * ln(10.0) / (PI * resonantFrequencyHz)
    return seconds * 1_000.0
}
