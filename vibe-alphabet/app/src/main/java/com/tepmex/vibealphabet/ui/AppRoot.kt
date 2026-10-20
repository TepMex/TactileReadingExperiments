package com.tepmex.vibealphabet.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tepmex.vibealphabet.core.DrillSubject
import com.tepmex.vibealphabet.core.Pattern
import com.tepmex.vibealphabet.core.SignalId
import com.tepmex.vibealphabet.core.Symbol
import com.tepmex.vibealphabet.core.SymbolBook
import com.tepmex.vibealphabet.core.Waveform
import com.tepmex.vibealphabet.core.canonicalSpelling
import com.tepmex.vibealphabet.core.defaultPattern
import com.tepmex.vibealphabet.core.normalizeWindow
import com.tepmex.vibealphabet.core.spellingIssue
import com.tepmex.vibealphabet.core.waveformForSymbol
import com.tepmex.vibealphabet.data.AlphabetPreference
import com.tepmex.vibealphabet.haptics.VibePlayer
import com.tepmex.vibealphabet.ui.learn.LearnScreen
import com.tepmex.vibealphabet.ui.settings.SettingsScreen
import com.tepmex.vibealphabet.ui.symbols.SymbolScreen
import com.tepmex.vibealphabet.ui.test.TestScreen
import com.tepmex.vibealphabet.ui.theme.Ink
import java.util.UUID

class AppGraph(app: Application) {
    val player = VibePlayer(app)
    private val preference = AlphabetPreference(app)
    var book by mutableStateOf(preference.read())
        private set

    var symbols by mutableStateOf(preference.readSymbols())
        private set

    var drillSubject by mutableStateOf(preference.readDrillSubject())
        private set

    val windowMs: Int get() = book.windowMs

    fun setWindow(ms: Int) {
        val stored = normalizeWindow(ms)
        if (stored != ms || stored == windowMs) return
        book = book.withWindow(stored)
        preference.write(book)
    }

    fun pattern(signal: SignalId): Pattern = book.pattern(signal)

    fun waveform(signal: SignalId): Waveform = pattern(signal).toWaveform()

    fun isEdited(signal: SignalId): Boolean = book.isEdited(signal)

    fun updatePattern(signal: SignalId, pattern: Pattern) {
        val next = book.withPattern(signal, pattern)
        if (next == book) return
        book = next
        preference.write(book)
    }

    fun resetPattern(signal: SignalId) {
        updatePattern(signal, defaultPattern(signal, windowMs))
    }

    fun selectDrill(subject: DrillSubject) {
        if (subject == drillSubject) return
        drillSubject = subject
        preference.writeDrillSubject(subject)
    }

    fun addSymbol(spelling: String, first: SignalId): Symbol? {
        if (spellingIssue(spelling, symbols, null) != null) return null
        val canonical = canonicalSpelling(spelling) ?: return null
        val symbol = Symbol(UUID.randomUUID().toString(), canonical, listOf(first))
        replaceSymbols(symbols.withSymbol(symbol))
        return symbol
    }

    fun renameSymbol(id: String, spelling: String): Boolean {
        if (symbols.symbol(id) == null || spellingIssue(spelling, symbols, id) != null) return false
        replaceSymbols(symbols.renamed(id, spelling))
        return true
    }

    fun appendSymbolSignal(id: String, signal: SignalId) {
        replaceSymbols(symbols.appendSignal(id, signal))
    }

    fun removeSymbolSignalAt(id: String, index: Int) {
        replaceSymbols(symbols.removeSignalAt(id, index))
    }

    fun moveSymbolSignal(id: String, index: Int, delta: Int) {
        replaceSymbols(symbols.moveSignal(id, index, delta))
    }

    fun moveSymbol(id: String, delta: Int) {
        replaceSymbols(symbols.move(id, delta))
    }

    fun removeSymbol(id: String) {
        replaceSymbols(symbols.without(id))
    }

    fun symbolWaveform(symbol: Symbol): Waveform = waveformForSymbol(symbol, book)

    private fun replaceSymbols(next: SymbolBook) {
        if (next == symbols) return
        symbols = next
        preference.writeSymbols(next)
    }
}

class GraphHolder(app: Application) : AndroidViewModel(app) {
    val graph = AppGraph(app)
}

val LocalGraph = staticCompositionLocalOf<AppGraph> { error("Граф приложения не задан") }

private enum class Tab(val label: String, val icon: ImageVector) {
    Learn("Обучение", LearnGlyph),
    Symbols("Символы", SymbolGlyph),
    Test("Тест", TestGlyph),
    Settings("Настройки", SettingsGlyph),
}

@Composable
fun VibeAlphabetApp(graph: AppGraph) {
    var tabName by rememberSaveable { mutableStateOf(Tab.Learn.name) }
    val tab = Tab.entries.firstOrNull { it.name == tabName } ?: Tab.Learn
    CompositionLocalProvider(
        LocalGraph provides graph,
        LocalHapticFeedback provides SilentHapticFeedback,
    ) {
        SilenceSystemCues()
        VibrationGuard(graph)
        DisposableEffect(tab) {
            onDispose { graph.player.stop() }
        }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = Ink) {
                    Tab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = item == tab,
                            onClick = { tabName = item.name },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                when (tab) {
                    Tab.Learn -> LearnScreen()
                    Tab.Symbols -> SymbolScreen()
                    Tab.Test -> TestScreen()
                    Tab.Settings -> SettingsScreen()
                }
            }
        }
    }
}

@Composable
private fun SilenceSystemCues() {
    val view = LocalView.current
    DisposableEffect(view) {
        val sound = view.isSoundEffectsEnabled
        val haptic = view.isHapticFeedbackEnabled
        view.isSoundEffectsEnabled = false
        view.isHapticFeedbackEnabled = false
        onDispose {
            view.isSoundEffectsEnabled = sound
            view.isHapticFeedbackEnabled = haptic
        }
    }
}

@Composable
private fun VibrationGuard(graph: AppGraph) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, graph) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) graph.player.stop()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            graph.player.stop()
        }
    }
}

private object SilentHapticFeedback : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}
