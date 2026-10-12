package com.tepmex.hapticlab.core.spatial

enum class PatternPreset(val title: String) {
    THREE_PULSES("Три участка"),
    TWO_LONG("Два длинных"),
    SHORT_LONG("Короткий и длинный"),
    ALTERNATING("Чередование"),
    ALL_ON("Все включены"),
    ALL_OFF("Все выключены"),
}

/**
 * [onBlocks] включённых блоков и (onBlocks - 1) промежутков, длины выровнены.
 * Для 10 зон и 3 блоков получается ██░░██░░██.
 */
fun repeatingBlocks(zoneCount: Int, onBlocks: Int): List<Boolean> {
    require(zoneCount >= 1)
    require(onBlocks >= 1)
    val gaps = (onBlocks - 1).coerceAtLeast(0)
    val parts = onBlocks + gaps
    if (parts > zoneCount) {
        return List(zoneCount) { index -> index % 2 == 0 }
    }
    val base = zoneCount / parts
    var extra = zoneCount % parts
    val result = ArrayList<Boolean>(zoneCount)
    for (part in 0 until parts) {
        val len = base + if (extra > 0) 1 else 0
        if (extra > 0) extra -= 1
        val on = part % 2 == 0
        repeat(len) { result += on }
    }
    return result
}

/** Два включённых участка с одним промежутком по центру. Для 10 зон: ████░░████. */
fun twoLongBlocks(zoneCount: Int): List<Boolean> {
    if (zoneCount <= 0) return emptyList()
    if (zoneCount < 3) return List(zoneCount) { true }
    val gap = (zoneCount / 5).coerceAtLeast(1)
    val side = (zoneCount - gap) / 2
    val left = side
    val rightStart = left + gap
    return List(zoneCount) { index -> index < left || index >= rightStart }
}

/** Короткий участок, промежуток, длинный участок. Для 10 зон: ██░░░░████. */
fun shortThenLong(zoneCount: Int): List<Boolean> {
    if (zoneCount <= 0) return emptyList()
    if (zoneCount < 3) return List(zoneCount) { it == 0 }
    val short = (zoneCount / 5).coerceAtLeast(1)
    val long = (zoneCount * 2 / 5).coerceAtLeast(1)
    val gap = zoneCount - short - long
    if (gap < 1) return twoLongBlocks(zoneCount)
    return List(zoneCount) { index -> index < short || index >= short + gap }
}

fun alternatingPattern(zoneCount: Int, period: Int, onInPeriod: Int): List<Boolean> {
    require(zoneCount >= 0)
    val safePeriod = period.coerceAtLeast(1)
    val onCount = onInPeriod.coerceIn(0, safePeriod)
    return List(zoneCount) { index -> (index % safePeriod) < onCount }
}

fun presetPattern(preset: PatternPreset, zoneCount: Int): List<Boolean> = when (preset) {
    PatternPreset.THREE_PULSES -> repeatingBlocks(zoneCount, 3)
    PatternPreset.TWO_LONG -> twoLongBlocks(zoneCount)
    PatternPreset.SHORT_LONG -> shortThenLong(zoneCount)
    PatternPreset.ALTERNATING -> alternatingPattern(zoneCount, period = 2, onInPeriod = 1)
    PatternPreset.ALL_ON -> List(zoneCount) { true }
    PatternPreset.ALL_OFF -> List(zoneCount) { false }
}

fun patternSignature(zones: List<Boolean>): String = zones.joinToString("") { if (it) "1" else "0" }
