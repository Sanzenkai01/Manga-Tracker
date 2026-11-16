package com.sanzenkai.mangatracker.ui.theme

import com.sanzenkai.mangatracker.core.domain.model.AppThemeMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {
    @Test
    fun systemModeFollowsTheDeviceTheme() {
        assertTrue(AppThemeMode.SYSTEM.usesDarkColors(systemInDarkTheme = true))
        assertFalse(AppThemeMode.SYSTEM.usesDarkColors(systemInDarkTheme = false))
    }

    @Test
    fun explicitThemeModesOverrideTheDeviceTheme() {
        assertTrue(AppThemeMode.DARK.usesDarkColors(systemInDarkTheme = false))
        assertFalse(AppThemeMode.LIGHT.usesDarkColors(systemInDarkTheme = true))
    }
}
