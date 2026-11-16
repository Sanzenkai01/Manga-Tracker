package com.sanzenkai.mangatracker.ui.settings

import com.sanzenkai.mangatracker.core.domain.model.AppThemeMode
import com.sanzenkai.mangatracker.core.domain.model.LibraryLayout

data class SettingsUiState(
    val isLoading: Boolean = true,
    val libraryLayout: LibraryLayout = LibraryLayout.LIST,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val error: String? = null,
    val isBackupInProgress: Boolean = false,
    val isBackupImporting: Boolean = false,
    val backupMessage: String? = null,
    val isBackupError: Boolean = false,
)
