package com.sanzenkai.mangatracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanzenkai.mangatracker.navigation.MangaTrackerNavigation
import com.sanzenkai.mangatracker.ui.settings.SettingsViewModel
import com.sanzenkai.mangatracker.ui.theme.MangaTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val container = (application as MangaTrackerApplication).container
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = container.settingsViewModelFactory,
            )
            val settingsState = settingsViewModel.uiState.collectAsStateWithLifecycle()
            MangaTrackerTheme(themeMode = settingsState.value.themeMode) {
                MangaTrackerNavigation(container, settingsViewModel)
            }
        }
    }
}
