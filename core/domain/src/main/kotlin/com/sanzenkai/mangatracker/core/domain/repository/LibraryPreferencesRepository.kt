package com.sanzenkai.mangatracker.core.domain.repository

import com.sanzenkai.mangatracker.core.domain.model.AppThemeMode
import com.sanzenkai.mangatracker.core.domain.model.LibraryLayout
import com.sanzenkai.mangatracker.core.domain.model.MonitoringInterval
import kotlinx.coroutines.flow.Flow

interface LibraryPreferencesRepository {
    fun observeLayout(): Flow<LibraryLayout>

    suspend fun setLayout(layout: LibraryLayout)

    fun observeThemeMode(): Flow<AppThemeMode>

    suspend fun setThemeMode(themeMode: AppThemeMode)

    fun observeMonitoringInterval(): Flow<MonitoringInterval>

    suspend fun setMonitoringInterval(interval: MonitoringInterval)
}
