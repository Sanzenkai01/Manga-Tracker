package com.sanzenkai.mangatracker.core.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sanzenkai.mangatracker.core.domain.model.AppThemeMode
import com.sanzenkai.mangatracker.core.domain.model.LibraryLayout
import com.sanzenkai.mangatracker.core.domain.repository.LibraryPreferencesRepository
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

    private companion object {
        val GRID_LAYOUT_KEY = booleanPreferencesKey("library_grid_layout")
        val THEME_MODE_KEY = stringPreferencesKey("app_theme_mode")
    }
}
