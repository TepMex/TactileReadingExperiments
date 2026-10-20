package com.tepmex.vibealphabet.data

import android.content.Context
import com.tepmex.vibealphabet.core.DrillSubject
import com.tepmex.vibealphabet.core.PatternBook
import com.tepmex.vibealphabet.core.SignalId
import com.tepmex.vibealphabet.core.SymbolBook
import com.tepmex.vibealphabet.core.decodeSymbolBook
import com.tepmex.vibealphabet.core.encodePattern
import com.tepmex.vibealphabet.core.encodeSymbolBook
import com.tepmex.vibealphabet.core.patternBookFromStored

class AlphabetPreference(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun read(): PatternBook {
        val encoded = buildMap {
            for (signal in SignalId.entries) {
                val raw = prefs.getString(patternKey(signal), null) ?: continue
                put(signal, raw)
            }
        }
        return patternBookFromStored(prefs.getInt(WINDOW, 0), encoded)
    }

    fun write(book: PatternBook) {
        val editor = prefs.edit().putInt(WINDOW, book.windowMs)
        for (signal in SignalId.entries) {
            val key = patternKey(signal)
            val encoded = book.overrides[signal]?.let(::encodePattern)
            if (encoded == null) editor.remove(key) else editor.putString(key, encoded)
        }
        editor.apply()
    }

    fun readSymbols(): SymbolBook = decodeSymbolBook(prefs.getString(SYMBOLS, null).orEmpty())

    fun writeSymbols(book: SymbolBook) {
        val editor = prefs.edit()
        val encoded = encodeSymbolBook(book)
        if (encoded.isEmpty()) editor.remove(SYMBOLS) else editor.putString(SYMBOLS, encoded)
        editor.apply()
    }

    fun readDrillSubject(): DrillSubject = DrillSubject.fromStorage(prefs.getString(DRILL, null))

    fun writeDrillSubject(subject: DrillSubject) {
        prefs.edit().putString(DRILL, subject.storageKey).apply()
    }

    private fun patternKey(signal: SignalId) = "pattern_${signal.title}"

    private companion object {
        const val FILE = "vibe_alphabet"
        const val WINDOW = "window_ms"
        const val SYMBOLS = "symbols"
        const val DRILL = "drill"
    }
}
