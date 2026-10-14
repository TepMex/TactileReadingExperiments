package com.tepmex.vibealphabet.data

import android.content.Context
import com.tepmex.vibealphabet.core.normalizeWindow

class WindowPreference(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun read(): Int = normalizeWindow(prefs.getInt(KEY, 0))

    fun write(ms: Int) {
        prefs.edit().putInt(KEY, normalizeWindow(ms)).apply()
    }

    private companion object {
        const val FILE = "vibe_alphabet"
        const val KEY = "window_ms"
    }
}
