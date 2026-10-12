package com.tepmex.hapticlab.core.spatial

/**
 * Команда уровня А: приложение просит включить или выключить вибрацию.
 * Время команды не равно моменту физического отклика.
 */
sealed interface HapticCommand {
    data class StartHold(
        val durationMs: Long,
        val amplitude: Int,
        val refresh: Boolean,
    ) : HapticCommand

    data object Stop : HapticCommand
}

data class ZoneSample(
    val timeMs: Long,
    val zoneIndex: Int?,
    val zoneIsOn: Boolean,
    val penetrationMm: Float,
)

/**
 * Включает удержание только при смене запрошенного состояния.
 * Повторные события внутри той же зоны ON мотор не перезапускают,
 * пока не подойдёт конец ограниченного удержания.
 */
class SpatialVibrationMachine(
    var debounceMm: Float = 0.5f,
    var amplitude: Int = 255,
    var maxHoldMs: Long = 2_000L,
    var refreshLeadMs: Long = 700L,
    var touchBudgetMs: Long = 60_000L,
) {
    var fingerDown: Boolean = false
        private set
    var latchedZone: Int? = null
        private set
    var requestedOn: Boolean = false
        private set
    var budgetExhausted: Boolean = false
        private set

    private var holdUntilMs: Long = 0L
    private var touchStartedMs: Long = 0L

    fun down(sample: ZoneSample): List<HapticCommand> {
        fingerDown = true
        budgetExhausted = false
        touchStartedMs = sample.timeMs
        holdUntilMs = 0L
        latchedZone = sample.zoneIndex
        requestedOn = false
        return if (sample.zoneIndex != null && sample.zoneIsOn) {
            startHold(sample.timeMs, refresh = false)
        } else {
            emptyList()
        }
    }

    fun move(sample: ZoneSample): List<HapticCommand> {
        if (!fingerDown || budgetExhausted) return emptyList()
        val commands = ArrayList<HapticCommand>(2)
        val next = sample.zoneIndex
        val current = latchedZone
        if (next != current) {
            val allowed = next == null || debounceMm <= 0f || sample.penetrationMm >= debounceMm
            if (allowed) {
                latchedZone = next
                commands += applyState(on = next != null && sample.zoneIsOn, timeMs = sample.timeMs)
            }
        }
        if (requestedOn) commands += refreshIfNeeded(sample.timeMs)
        return commands
    }

    fun up(timeMs: Long): List<HapticCommand> {
        fingerDown = false
        latchedZone = null
        budgetExhausted = false
        holdUntilMs = 0L
        return stopIfNeeded()
    }

    fun forceOff() {
        requestedOn = false
        holdUntilMs = 0L
    }

    fun tick(timeMs: Long): List<HapticCommand> {
        if (!fingerDown || !requestedOn || budgetExhausted) return emptyList()
        return refreshIfNeeded(timeMs)
    }

    private fun applyState(on: Boolean, timeMs: Long): List<HapticCommand> {
        return if (on) {
            if (requestedOn) emptyList() else startHold(timeMs, refresh = false)
        } else {
            stopIfNeeded()
        }
    }

    private fun startHold(timeMs: Long, refresh: Boolean): List<HapticCommand> {
        val elapsed = timeMs - touchStartedMs
        if (elapsed >= touchBudgetMs) {
            budgetExhausted = true
            return stopIfNeeded()
        }
        val remaining = (touchBudgetMs - elapsed).coerceAtLeast(1L)
        val duration = minOf(maxHoldMs, remaining)
        requestedOn = true
        holdUntilMs = timeMs + duration
        return listOf(
            HapticCommand.StartHold(
                durationMs = duration,
                amplitude = amplitude.coerceIn(1, 255),
                refresh = refresh,
            ),
        )
    }

    private fun refreshIfNeeded(timeMs: Long): List<HapticCommand> {
        if (timeMs + refreshLeadMs < holdUntilMs) return emptyList()
        return startHold(timeMs, refresh = true)
    }

    private fun stopIfNeeded(): List<HapticCommand> {
        if (!requestedOn) return emptyList()
        requestedOn = false
        holdUntilMs = 0L
        return listOf(HapticCommand.Stop)
    }
}

fun isStateTransition(command: HapticCommand): Boolean = when (command) {
    is HapticCommand.StartHold -> !command.refresh
    HapticCommand.Stop -> true
}
