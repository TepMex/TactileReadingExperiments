package com.tepmex.vibealphabet.data

import android.content.Context
import com.tepmex.vibealphabet.core.PatternBook
import com.tepmex.vibealphabet.core.SignalId
import com.tepmex.vibealphabet.core.encodePattern
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

    private fun patternKey(signal: SignalId) = "pattern_${signal.title}"

    private companion object {
        const val FILE = "vibe_alphabet"
        const val WINDOW = "window_ms"
    }
}
