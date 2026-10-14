package com.tepmex.vibealphabet

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tepmex.vibealphabet.ui.GraphHolder
import com.tepmex.vibealphabet.ui.VibeAlphabetApp
import com.tepmex.vibealphabet.ui.theme.VibeAlphabetTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        window.isNavigationBarContrastEnforced = false
        window.isStatusBarContrastEnforced = false
        setContent {
            val holder: GraphHolder = viewModel()
            VibeAlphabetTheme {
                VibeAlphabetApp(holder.graph)
            }
        }
    }
}
