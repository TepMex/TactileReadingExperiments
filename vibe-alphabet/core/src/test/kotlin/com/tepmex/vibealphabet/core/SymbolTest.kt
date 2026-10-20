package com.tepmex.vibealphabet.core

import java.util.ArrayDeque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolTest {
    @Test
    fun spellingKeepsLettersAndUppercase() {
        assertEquals("А", canonicalSpelling(" а "))
        assertEquals("СТ", canonicalSpelling("ст"))
        assertEquals("Ё", canonicalSpelling("ё"))
        assertEquals("ЩА", canonicalSpelling("ща"))
        assertNull(canonicalSpelling(""))
        assertNull(canonicalSpelling("   "))
        assertNull(canonicalSpelling("А1"))
        assertNull(canonicalSpelling("С Т"))
        assertNull(canonicalSpelling("А-Б"))
        assertNull(canonicalSpelling("АБВГДЕЖЗИ"))
        assertNull(spellingIssue("б", bookOf(symbol("a", "СТ")), exceptId = null))
        assertEquals("Такой символ уже есть.", spellingIssue("ст", bookOf(symbol("a", "СТ")), exceptId = null))
        assertEquals("Такой символ уже есть.", spellingIssue("ст", bookOf(symbol("a", "СТ")), exceptId = "b"))
        assertNull(spellingIssue("ст", bookOf(symbol("a", "СТ")), exceptId = "a"))
    }

    @Test
    fun chainEditsKeepOneSignalAndTheCap() {
        var book = bookOf(symbol("a", "А", SignalId.SHORT))
        book = book.appendSignal("a", SignalId.LONG)
        book = book.appendSignal("a", SignalId.SHORT)
        book = book.moveSignal("a", 2, -1)
        assertEquals(
            listOf(SignalId.SHORT, SignalId.SHORT, SignalId.LONG),
            book.symbol("a")!!.signals,
        )
        book = book.removeSignalAt("a", 1)
        assertEquals(listOf(SignalId.SHORT, SignalId.LONG), book.symbol("a")!!.signals)
        book = book.removeSignalAt("a", 0)
        assertEquals(listOf(SignalId.LONG), book.symbol("a")!!.signals)
        assertEquals(book, book.removeSignalAt("a", 0))

        var full = symbol("b", "Б", SignalId.SHORT)
        repeat(MAX_SIGNALS_IN_SYMBOL - 1) {
            full = full.copy(signals = full.signals + SignalId.DOUBLE)
        }
        val capped = bookOf(full).appendSignal("b", SignalId.TRIPLE)
        assertEquals(MAX_SIGNALS_IN_SYMBOL, capped.symbol("b")!!.signals.size)
        assertEquals(SignalId.DOUBLE, capped.symbol("b")!!.signals.last())
    }

    @Test
    fun renameMoveAndDeleteKeepUniqueSpellings() {
        var book = bookOf(
            symbol("a", "А", SignalId.SHORT),
            symbol("b", "Б", SignalId.LONG),
        )
        book = book.renamed("b", " ст ")
        assertEquals("СТ", book.symbol("b")!!.spelling)
        val duplicate = runCatching { book.renamed("b", "а") }.exceptionOrNull()
        assertTrue(duplicate is IllegalArgumentException)
        book = book.move("b", -1)
        assertEquals(listOf("СТ", "А"), book.symbols.map { it.spelling })
        book = book.without("a")
        assertEquals(listOf("b"), book.symbols.map { it.id })
        assertFalse(book.canTrain)
        assertTrue(bookOf(symbol("a", "А"), symbol("b", "Б")).canTrain)
    }

    @Test
    fun storageRoundTripSkipsBrokenLines() {
        val book = bookOf(
            symbol("id-1", "А", SignalId.SHORT, SignalId.LONG),
            symbol("id_2", "ЩА", SignalId.TRIPLE),
        )
        val raw = encodeSymbolBook(book)
        assertEquals("id-1|А|1+5\nid_2|ЩА|3", raw)
        assertEquals(book, decodeSymbolBook(raw))
        assertEquals(SymbolBook(), decodeSymbolBook(""))

        val dirty = """
            id-1|А|1+5
            broken
            id-1|Б|2
            |В|1
            bad id|Г|1
            id-3|А1|1
            id-4|Д|
            id-5|Е|1+9
            id-6|Ё|6+6
            id-2|Ж|4
        """.trimIndent()
        val decoded = decodeSymbolBook(dirty)
        assertEquals(listOf("id-1", "id-6", "id-2"), decoded.symbols.map { it.id })
        assertEquals(listOf("А", "Ё", "Ж"), decoded.symbols.map { it.spelling })
        assertEquals(listOf(SignalId.LONG_SHORT, SignalId.LONG_SHORT), decoded.symbol("id-6")!!.signals)
    }

    @Test
    fun playbackConcatenatesCurrentDrawingsWithoutMergingTheJoin() {
        val book = PatternBook(windowMs = 100)
            .withPattern(SignalId.SHORT, decode("-50,+50"))
            .withPattern(SignalId.LONG, decode("-100"))
        val symbol = symbol("a", "А", SignalId.SHORT, SignalId.LONG, SignalId.MEDIUM)
        val waveform = waveformForSymbol(symbol, book)
        assertEquals(listOf(50L, 50L, 100L, 50L, 50L), waveform.timingsMs)
        assertEquals(listOf(0, ON_AMPLITUDE, 0, ON_AMPLITUDE, 0), waveform.amplitudes)
        assertEquals(300L, waveform.totalMs)
        assertEquals("1 Короткий → 5 Длинный → 4 Средний", formatSymbolSignals(symbol.signals))

        val shorter = waveformForSymbol(symbol, book.withWindow(50))
        assertEquals(150L, shorter.totalMs)
    }

    @Test
    fun drillRepeatsUntilTheAnswerThenDrawsAgain() {
        val queue = ArrayDeque(listOf("А", "СТ"))
        val machine = DrillMachine { queue.removeFirst() }
        assertEquals(DrillPhase.Idle, machine.phase)
        assertEquals("А", machine.onBlankPressed())
        assertEquals("А", machine.onBlankPressed())
        val wrong = machine.answer("Б")
        assertFalse(wrong.correct)
        assertEquals("А", wrong.item)
        assertEquals(wrong, machine.phase)
        assertEquals("СТ", machine.onBlankPressed())
        assertTrue(machine.answer("СТ").correct)
        machine.reset()
        assertEquals(DrillPhase.Idle, machine.phase)
    }

    @Test
    fun drillRejectsAnAnswerBeforeThePrompt() {
        val machine = DrillMachine { "А" }
        val error = runCatching { machine.answer("А") }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        machine.onBlankPressed()
        machine.answer("А")
        val second = runCatching { machine.answer("Б") }.exceptionOrNull()
        assertTrue(second is IllegalStateException)
    }

    @Test
    fun storedDrillSubjectIgnoresTheEnumName() {
        assertEquals(DrillSubject.Signals, DrillSubject.fromStorage(null))
        assertEquals(DrillSubject.Signals, DrillSubject.fromStorage("Signals"))
        assertEquals(DrillSubject.Symbols, DrillSubject.fromStorage("symbols"))
    }

    private fun symbol(id: String, spelling: String, vararg signals: SignalId): Symbol =
        Symbol(id, spelling, signals.toList().ifEmpty { listOf(SignalId.SHORT) })

    private fun bookOf(vararg symbols: Symbol): SymbolBook = SymbolBook(symbols.toList())

    private fun decode(raw: String): Pattern = decodePattern(raw) ?: error(raw)
}
