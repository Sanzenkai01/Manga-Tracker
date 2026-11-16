package com.sanzenkai.mangatracker.core.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sanzenkai.mangatracker.core.domain.model.AppThemeMode
import com.sanzenkai.mangatracker.core.domain.model.LibraryLayout
import com.sanzenkai.mangatracker.core.domain.model.MonitoringInterval
import com.sanzenkai.mangatracker.core.domain.repository.LibraryPreferencesRepository
import com.sanzenkai.mangatracker.core.domain.repository.SiteProblemNotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.preferencesDataStore by preferencesDataStore(name = "manga_tracker_preferences")

class DataStoreLibraryPreferencesRepository(
    context: Context,
) : LibraryPreferencesRepository {
    private val dataStore = context.applicationContext.preferencesDataStore

    override fun observeLayout(): Flow<LibraryLayout> =
        dataStore.data.map { preferences ->
            if (preferences[GRID_LAYOUT_KEY] == true) {
                LibraryLayout.GRID
            } else {
                LibraryLayout.LIST
            }
        }

    override suspend fun setLayout(layout: LibraryLayout) {
        dataStore.edit { preferences ->
            preferences[GRID_LAYOUT_KEY] = layout == LibraryLayout.GRID
        }
    }

    override fun observeThemeMode(): Flow<AppThemeMode> =
        dataStore.data.map { preferences ->
            preferences[THEME_MODE_KEY]
                ?.let { storedMode ->
                    AppThemeMode.values().firstOrNull { it.name == storedMode }
                }
                ?: AppThemeMode.SYSTEM
        }

    override suspend fun setThemeMode(themeMode: AppThemeMode) {
        dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = themeMode.name
        }
    }

    override fun observeMonitoringInterval(): Flow<MonitoringInterval> =
        dataStore.data.map { preferences ->
            MonitoringInterval.fromHours(
                preferences[MONITORING_INTERVAL_HOURS_KEY]?.toLong()
                    ?: MonitoringInterval.DEFAULT.hours,
            )
        }

    override suspend fun setMonitoringInterval(interval: MonitoringInterval) {
        dataStore.edit { preferences ->
            preferences[MONITORING_INTERVAL_HOURS_KEY] = interval.hours.toInt()
        }
    }

    private companion object {
        val GRID_LAYOUT_KEY = booleanPreferencesKey("library_grid_layout")
        val THEME_MODE_KEY = stringPreferencesKey("app_theme_mode")
        val MONITORING_INTERVAL_HOURS_KEY = intPreferencesKey("monitoring_interval_hours")
    }
}

class DataStoreSiteProblemNotificationRepository(
    context: Context,
) : SiteProblemNotificationRepository {
    private val dataStore = context.applicationContext.preferencesDataStore

    override suspend fun claimEpisodeNotification(mangaId: String, episodeId: String): Boolean {
        var claimed = false
        dataStore.edit { preferences ->
            val key = stringPreferencesKey("monitoring_problem_episode:$mangaId")
            if (preferences[key] != episodeId) {
                preferences[key] = episodeId
                claimed = true
            }
        }
        return claimed
    }
}
