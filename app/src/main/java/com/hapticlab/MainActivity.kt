package com.hapticlab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hapticlab.ui.GraphHolder
import com.hapticlab.ui.HapticLabApp
import com.hapticlab.ui.theme.HapticLabTheme

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
