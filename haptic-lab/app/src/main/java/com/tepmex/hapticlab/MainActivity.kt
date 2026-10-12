package com.tepmex.hapticlab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tepmex.hapticlab.ui.GraphHolder
import com.tepmex.hapticlab.ui.HapticLabApp
import com.tepmex.hapticlab.ui.theme.HapticLabTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val holder: GraphHolder = viewModel()
            HapticLabTheme {
                HapticLabApp(holder.graph)
            }
        }
    }
}
