package com.hapticlab.core.alphabet

import kotlinx.serialization.Serializable

@Serializable
data class Glyph(
    val symbol: String,
    val zones: List<Boolean>,
)

@Serializable
data class TactileAlphabet(
    val zonesPerSymbol: Int,
    val glyphs: List<Glyph>,
)

fun fitZones(source: List<Boolean>, zones: Int): List<Boolean> {
    val count = zones.coerceAtLeast(1)
    return List(count) { index -> source.getOrNull(index) == true }
}

fun bitsToZones(bits: String, zones: Int): List<Boolean> {
    val cleaned = bits.filter { it == '0' || it == '1' }
    return List(zones.coerceAtLeast(1)) { index -> cleaned.getOrNull(index) == '1' }
}

/** Несколько демонстрационных знаков, не оптимизированный алфавит. */
fun demoAlphabet(zonesPerSymbol: Int = 7): TactileAlphabet {
    val count = zonesPerSymbol.coerceIn(3, 16)
    val seeds = listOf(
        "А" to "1100110",
        "Б" to "1111000",
        "В" to "1010101",
        "Г" to "1110000",
        "Д" to "1000001",
        "Е" to "1011110",
        " " to "0000000",
    )
    return TactileAlphabet(
        zonesPerSymbol = count,
        glyphs = seeds.map { (symbol, bits) -> Glyph(symbol, bitsToZones(bits, count)) },
    )
}

data class RenderedLine(
    val zones: List<Boolean>,
    val symbolStarts: List<Int>,
    val unknown: List<String>,
)

/**
 * Склеивает знаки в одну дорожку. Промежуток — выключенные зоны между знаками,
 * чтобы палец не отрывался.
 */
fun renderText(
    text: String,
    alphabet: TactileAlphabet,
    gapZones: Int,
): RenderedLine {
    val gap = gapZones.coerceIn(0, 8)
    val zones = ArrayList<Boolean>()
    val starts = ArrayList<Int>()
    val unknown = ArrayList<String>()
    val bySymbol = alphabet.glyphs.associateBy { it.symbol }
    text.forEach { character ->
        val key = character.toString()
        val glyph = bySymbol[key]
        if (glyph == null) {
            unknown += key
            return@forEach
        }
        if (zones.isNotEmpty() && gap > 0) {
            repeat(gap) { zones += false }
        }
        starts += zones.size
        zones += fitZones(glyph.zones, alphabet.zonesPerSymbol)
    }
    return RenderedLine(zones, starts, unknown)
}

fun resizeAlphabet(alphabet: TactileAlphabet, zonesPerSymbol: Int): TactileAlphabet {
    val count = zonesPerSymbol.coerceIn(3, 16)
    return alphabet.copy(
        zonesPerSymbol = count,
        glyphs = alphabet.glyphs.map { glyph -> glyph.copy(zones = fitZones(glyph.zones, count)) },
    )
}
