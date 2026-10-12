package com.tepmex.hapticlab.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tepmex.hapticlab.ui.Routes
import com.tepmex.hapticlab.ui.components.LevelBanner

@Composable
fun HomeScreen(onOpen: (String) -> Unit) {
    val sections = listOf(
        Routes.Diagnostics to "Диагностика",
        Routes.Generator to "Генератор",
        Routes.Spatial to "Пространственные рисунки",
        Routes.Speed to "Скоростной тест",
        Routes.Experiment to "Эксперимент",
        Routes.Alphabet to "Тактильная азбука",
    )
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Haptic Lab", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Исследование чтения пространственных вибрационных рисунков. Вибрация привязана к положению пальца, а не к заранее заданному времени.",
                style = MaterialTheme.typography.bodyLarge,
            )
            LevelBanner()
            sections.forEach { (route, title) ->
                Button(
                    onClick = { onOpen(route) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
