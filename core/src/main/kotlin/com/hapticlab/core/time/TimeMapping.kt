package com.hapticlab.core.time

/**
 * Переносит метку MotionEvent из шкалы uptimeMillis в elapsedRealtimeNanos.
 *
 * Оба чтения «сейчас» должны быть сделаны в один момент. Результат — время события
 * ввода на монотонной шкале журнала. Это не задержка вибромотора.
 */
fun mapUptimeMillisToElapsedRealtimeNanos(
    eventUptimeMillis: Long,
    nowUptimeMillis: Long,
    nowElapsedRealtimeNanos: Long,
): Long {
    val deltaMillis = nowUptimeMillis - eventUptimeMillis
    return nowElapsedRealtimeNanos - deltaMillis * 1_000_000L
}
