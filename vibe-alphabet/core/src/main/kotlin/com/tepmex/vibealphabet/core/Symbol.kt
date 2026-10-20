package com.tepmex.vibealphabet.core

import java.util.Locale

const val MAX_SYMBOLS = 64
const val MAX_SYMBOL_LETTERS = 8
const val MAX_SIGNALS_IN_SYMBOL = 8
const val MIN_SYMBOLS_TO_TRAIN = 2

/**
 * Буква или буквосочетание и цепочка сигналов из редактора.
 * Надпись уже приведена к верхнему регистру и состоит только из букв.
 * Сигналы ссылаются на текущие рисунки: правка на обучении меняет и символ.
 */
data class Symbol(
    val id: String,
    val spelling: String,
    val signals: List<SignalId>,
) {
    init {
        require(isSymbolId(id)) { "Идентификатор символа не годится" }
        require(canonicalSpelling(spelling) == spelling) {
            "Надпись «$spelling» — не буква и не буквосочетание"
        }
        require(signals.isNotEmpty()) { "У символа нет сигналов" }
        require(signals.size <= MAX_SIGNALS_IN_SYMBOL) {
            "В символе не больше $MAX_SIGNALS_IN_SYMBOL сигналов"
        }
    }
}

/** Порядок символов. Надписи и идентификаторы не повторяются. */
data class SymbolBook(
    val symbols: List<Symbol> = emptyList(),
) {
    init {
        require(symbols.size <= MAX_SYMBOLS) { "Символов не больше $MAX_SYMBOLS" }
        require(symbols.map { it.id }.toSet().size == symbols.size) { "Идентификаторы символов повторяются" }
        require(symbols.map { it.spelling }.toSet().size == symbols.size) { "Надписи символов повторяются" }
    }

    fun symbol(id: String): Symbol? = symbols.find { it.id == id }

    val canTrain: Boolean get() = symbols.size >= MIN_SYMBOLS_TO_TRAIN

    fun withSymbol(symbol: Symbol): SymbolBook {
        val duplicate = symbols.any { it.spelling == symbol.spelling && it.id != symbol.id }
        require(!duplicate) { "Символ «${symbol.spelling}» уже есть" }
        val index = symbols.indexOfFirst { it.id == symbol.id }
        if (index < 0) {
            require(symbols.size < MAX_SYMBOLS) { "Символов уже $MAX_SYMBOLS" }
            return SymbolBook(symbols + symbol)
        }
        val next = symbols.toMutableList()
        next[index] = symbol
        return SymbolBook(next)
    }

    fun without(id: String): SymbolBook = SymbolBook(symbols.filter { it.id != id })

    fun move(id: String, delta: Int): SymbolBook {
        val index = symbols.indexOfFirst { it.id == id }
        if (index < 0) return this
        val target = index + delta
        if (target !in symbols.indices) return this
        val next = symbols.toMutableList()
        next.add(target, next.removeAt(index))
        return SymbolBook(next)
    }

    fun renamed(id: String, spelling: String): SymbolBook {
        val symbol = symbol(id) ?: return this
        val canonical = canonicalSpelling(spelling) ?: error("Надпись «$spelling» — не буква и не буквосочетание")
        if (canonical == symbol.spelling) return this
        return withSymbol(symbol.copy(spelling = canonical))
    }

    fun appendSignal(id: String, signal: SignalId): SymbolBook {
        val symbol = symbol(id) ?: return this
        if (symbol.signals.size >= MAX_SIGNALS_IN_SYMBOL) return this
        return withSymbol(symbol.copy(signals = symbol.signals + signal))
    }

    fun removeSignalAt(id: String, index: Int): SymbolBook {
        val symbol = symbol(id) ?: return this
        if (symbol.signals.size <= 1 || index !in symbol.signals.indices) return this
        val next = symbol.signals.filterIndexed { signalIndex, _ -> signalIndex != index }
        return withSymbol(symbol.copy(signals = next))
    }

    fun moveSignal(id: String, index: Int, delta: Int): SymbolBook {
        val symbol = symbol(id) ?: return this
        val target = index + delta
        if (index !in symbol.signals.indices || target !in symbol.signals.indices) return this
        val next = symbol.signals.toMutableList()
        next.add(target, next.removeAt(index))
        return withSymbol(symbol.copy(signals = next))
    }
}

/**
 * Верхний регистр и только буквы, от 1 до 8. Иначе null.
 * «ё» становится «Ё». Пробелы по краям снимаются, внутри надписи их нет.
 */
fun canonicalSpelling(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.length !in 1..MAX_SYMBOL_LETTERS) return null
    if (!trimmed.all { it.isLetter() }) return null
    return trimmed.uppercase(Locale.forLanguageTag("ru"))
}

/** Пустая строка для годной новой или текущей надписи. Иначе текст ошибки для поля. */
fun spellingIssue(raw: String, book: SymbolBook, exceptId: String?): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return "Введите букву или буквосочетание."
    if (trimmed.length > MAX_SYMBOL_LETTERS) return "Не длиннее $MAX_SYMBOL_LETTERS букв."
    if (!trimmed.all { it.isLetter() }) return "Только буквы, без пробелов и цифр."
    val spelling = trimmed.uppercase(Locale.forLanguageTag("ru"))
    val taken = book.symbols.any { it.spelling == spelling && it.id != exceptId }
    if (taken) return "Такой символ уже есть."
    return null
}

/** Сигналы символа подряд. Стык не сливается, даже если полярность совпала. */
fun waveformForSymbol(symbol: Symbol, book: PatternBook): Waveform {
    val parts = symbol.signals.map { signal -> book.pattern(signal).toWaveform() }
    return Waveform(
        timingsMs = parts.flatMap { it.timingsMs },
        amplitudes = parts.flatMap { it.amplitudes },
    )
}

/** «1 Короткий → 4 Средний». */
fun formatSymbolSignals(signals: List<SignalId>): String =
    signals.joinToString(" → ") { signal -> "${signal.title} ${signal.patternName}" }

/**
 * Книга в одну строку на символ: `id|А|1+4+1`.
 * Номера — подписи сигналов, не имена констант.
 */
fun encodeSymbolBook(book: SymbolBook): String =
    book.symbols.joinToString("\n") { symbol ->
        val chain = symbol.signals.joinToString("+") { it.title }
        "${symbol.id}|${symbol.spelling}|$chain"
    }

/** Битая строка, повтор и лишние символы пропускаются. Остальное сохраняется по порядку. */
fun decodeSymbolBook(raw: String): SymbolBook {
    if (raw.isBlank()) return SymbolBook()
    val accepted = mutableListOf<Symbol>()
    val ids = mutableSetOf<String>()
    val spellings = mutableSetOf<String>()
    for (line in raw.lineSequence()) {
        if (accepted.size >= MAX_SYMBOLS) break
        val symbol = decodeSymbolLine(line) ?: continue
        if (!ids.add(symbol.id) || !spellings.add(symbol.spelling)) continue
        accepted += symbol
    }
    return SymbolBook(accepted)
}

private fun decodeSymbolLine(line: String): Symbol? {
    val parts = line.split('|')
    if (parts.size != 3) return null
    val id = parts[0].trim()
    if (!isSymbolId(id)) return null
    val spelling = canonicalSpelling(parts[1]) ?: return null
    val tokens = parts[2].split('+')
    if (tokens.isEmpty() || tokens.size > MAX_SIGNALS_IN_SYMBOL) return null
    val signals = tokens.map { token ->
        signalByTitle(token.trim()) ?: return null
    }
    return Symbol(id, spelling, signals)
}

private fun signalByTitle(title: String): SignalId? =
    SignalId.entries.firstOrNull { it.title == title }

private fun isSymbolId(id: String): Boolean {
    if (id.length !in 1..64) return false
    return id.all { character ->
        character in 'a'..'z' || character in 'A'..'Z' || character in '0'..'9' || character == '-' || character == '_'
    }
}
