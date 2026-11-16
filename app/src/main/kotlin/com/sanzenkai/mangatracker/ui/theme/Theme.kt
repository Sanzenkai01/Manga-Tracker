package com.sanzenkai.mangatracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.sanzenkai.mangatracker.core.domain.model.AppThemeMode

private val LightColors = lightColorScheme(
    primary = PurplePrimary,
    onPrimary = PurpleOnPrimary,
    primaryContainer = PurpleContainer,
    onPrimaryContainer = OnPurpleContainer,
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkContainer,
    onPrimaryContainer = OnDarkContainer,
)

@Composable
fun MangaTrackerTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (themeMode.usesDarkColors(isSystemInDarkTheme())) DarkColors else LightColors,
        content = content,
    )
}

internal fun AppThemeMode.usesDarkColors(systemInDarkTheme: Boolean): Boolean =
    when (this) {
        AppThemeMode.SYSTEM -> systemInDarkTheme
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
