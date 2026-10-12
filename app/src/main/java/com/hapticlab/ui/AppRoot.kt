package com.hapticlab.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hapticlab.data.LabPreferences
import com.hapticlab.data.SessionStore
import com.hapticlab.haptics.HapticInventory
import com.hapticlab.haptics.HapticsEngine
import com.hapticlab.ui.alphabet.AlphabetScreen
import com.hapticlab.ui.diagnostics.DiagnosticsScreen
import com.hapticlab.ui.experiment.ExperimentScreen
import com.hapticlab.ui.generator.GeneratorScreen
import com.hapticlab.ui.home.HomeScreen
import com.hapticlab.ui.spatial.SpatialScreen
import com.hapticlab.ui.speed.SpeedScreen

class AppGraph(context: Context) {
    private val appContext = context.applicationContext
    val engine = HapticsEngine(appContext)
    val preferences = LabPreferences(appContext)
    val sessions = SessionStore(appContext)
    var calibrationScale by mutableStateOf(preferences.calibrationScale)
    var verboseLogging by mutableStateOf(preferences.verboseLogging)
    var inventory by mutableStateOf(engine.inventoryOrFailure())

    fun updateCalibrationScale(scale: Float) {
        calibrationScale = scale
        preferences.calibrationScale = scale
    }

    fun updateVerboseLogging(enabled: Boolean) {
        verboseLogging = enabled
        preferences.verboseLogging = enabled
    }

    fun refreshInventory(): HapticInventory {
        val fresh = engine.inventoryOrFailure()
        inventory = fresh
        return fresh
    }
}

class GraphHolder(app: Application) : AndroidViewModel(app) {
    val graph = AppGraph(app)
}

val LocalGraph = staticCompositionLocalOf<AppGraph> { error("Граф приложения не задан") }

object Routes {
    const val Home = "home"
    const val Diagnostics = "diagnostics"
    const val Generator = "generator"
    const val Spatial = "spatial"
    const val Speed = "speed"
    const val Experiment = "experiment"
    const val Alphabet = "alphabet"
}

@Composable
fun HapticLabApp(graph: AppGraph) {
    val nav = rememberNavController()
    CompositionLocalProvider(LocalGraph provides graph) {
        VibrationGuard(graph)
        NavHost(navController = nav, startDestination = Routes.Home) {
            composable(Routes.Home) { HomeScreen(onOpen = nav::navigate) }
            composable(Routes.Diagnostics) { DiagnosticsScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.Generator) { GeneratorScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.Spatial) { SpatialScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.Speed) { SpeedScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.Experiment) { ExperimentScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.Alphabet) { AlphabetScreen(onBack = { nav.popBackStack() }) }
        }
    }
}

@Composable
private fun VibrationGuard(graph: AppGraph) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, graph) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) graph.engine.stop()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            graph.engine.stop()
        }
    }
}

@Composable
inline fun <reified VM : ViewModel> labViewModel(crossinline build: (AppGraph) -> VM): VM {
    val graph = LocalGraph.current
    return viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return build(graph) as T
            }
        },
    )
}

@Composable
fun graph(): AppGraph = LocalGraph.current

@Composable
fun activityContext(): Context = LocalContext.current
