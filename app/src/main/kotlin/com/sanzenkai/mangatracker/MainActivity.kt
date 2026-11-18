package com.sanzenkai.mangatracker

import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.sanzenkai.mangatracker.navigation.MangaTrackerNavigation
import com.sanzenkai.mangatracker.ui.opening.OpeningAnimation
import com.sanzenkai.mangatracker.ui.opening.shouldShowOpeningAnimation
import com.sanzenkai.mangatracker.ui.settings.SettingsViewModel
import com.sanzenkai.mangatracker.ui.theme.MangaTrackerTheme

class MainActivity : ComponentActivity() {
    private var themePreferenceLoaded = false
    private var splashStartedAtMillis = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashStartedAtMillis = SystemClock.uptimeMillis()
        splashScreen.setKeepOnScreenCondition {
            !themePreferenceLoaded &&
                SystemClock.uptimeMillis() - splashStartedAtMillis < MAX_THEME_LOAD_WAIT_MILLIS
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val showOpeningAnimation = shouldShowOpeningAnimation(
            isNewActivityInstance = savedInstanceState == null,
            isLauncherLaunch = intent.action == Intent.ACTION_MAIN &&
                intent.hasCategory(Intent.CATEGORY_LAUNCHER),
            animationsEnabled = Settings.Global.getFloat(
                contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) != 0f,
        )

        setContent {
            val container = (application as MangaTrackerApplication).container
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = container.settingsViewModelFactory,
            )
            val settingsState = settingsViewModel.uiState.collectAsStateWithLifecycle()
            SideEffect {
                if (settingsState.value.isThemePreferenceLoaded) {
                    themePreferenceLoaded = true
                }
            }
            MangaTrackerTheme(themeMode = settingsState.value.themeMode) {
                var openingAnimationState by rememberSaveable {
                    mutableIntStateOf(
                        if (showOpeningAnimation) OPENING_NOT_STARTED else OPENING_FINISHED,
                    )
                }
                Box(Modifier.fillMaxSize()) {
                    MangaTrackerNavigation(container, settingsViewModel)
                    if (openingAnimationState != OPENING_FINISHED) {
                        OpeningAnimation(
                            skipEntrance = openingAnimationState == OPENING_RUNNING,
                            onStarted = {
                                if (openingAnimationState == OPENING_NOT_STARTED) {
                                    openingAnimationState = OPENING_RUNNING
                                }
                            },
                            onFinished = { openingAnimationState = OPENING_FINISHED },
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val MAX_THEME_LOAD_WAIT_MILLIS = 600L
        const val OPENING_NOT_STARTED = 0
        const val OPENING_RUNNING = 1
        const val OPENING_FINISHED = 2
    }
}
