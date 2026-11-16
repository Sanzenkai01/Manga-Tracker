package com.sanzenkai.mangatracker.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModelStore
import com.sanzenkai.mangatracker.core.domain.model.AppThemeMode
import com.sanzenkai.mangatracker.core.domain.model.LibraryLayout
import com.sanzenkai.mangatracker.core.domain.model.MonitoringInterval
import com.sanzenkai.mangatracker.core.domain.repository.LibraryPreferencesRepository
import com.sanzenkai.mangatracker.data.backup.MangaBackupExporter
import com.sanzenkai.mangatracker.data.backup.MangaBackupImporter
import com.sanzenkai.mangatracker.monitoring.MonitoringScheduler
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = SettingsMainDispatcherRule()

    @Test
    fun selectedThemeIsPersistedAndPublishedInUiState() = runTest {
        val preferences = FakeLibraryPreferencesRepository()
        val viewModel = SettingsViewModel(
            preferences,
            FakeMangaBackupExporter(),
            FakeMangaBackupImporter(),
        )
        val viewModelStore = ViewModelStore().apply { put("settings", viewModel) }
        var collection: Job? = null
        try {
            collection = backgroundScope.launch(StandardTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }
            advanceUntilIdle()

            assertEquals(AppThemeMode.SYSTEM, viewModel.uiState.value.themeMode)

            viewModel.setThemeMode(AppThemeMode.DARK)
            advanceUntilIdle()

            assertEquals(AppThemeMode.DARK, preferences.themeMode.value)
            assertEquals(AppThemeMode.DARK, viewModel.uiState.value.themeMode)
        } finally {
            collection?.cancel()
            viewModelStore.clear()
        }
    }

    @Test
    fun selectedMonitoringIntervalIsPersistedAndReschedulesPeriodicWork() = runTest {
        val preferences = FakeLibraryPreferencesRepository()
        val scheduler = RecordingMonitoringScheduler()
        val viewModel = SettingsViewModel(
            preferences,
            FakeMangaBackupExporter(),
            FakeMangaBackupImporter(),
            scheduler,
        )
        val viewModelStore = ViewModelStore().apply { put("settings", viewModel) }
        var collection: Job? = null
        try {
            collection = backgroundScope.launch(StandardTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }
            advanceUntilIdle()

            viewModel.setMonitoringInterval(MonitoringInterval.TWELVE_HOURS)
            advanceUntilIdle()

            assertEquals(MonitoringInterval.TWELVE_HOURS, preferences.monitoringInterval.value)
            assertEquals(MonitoringInterval.TWELVE_HOURS, viewModel.uiState.value.monitoringInterval)
            assertEquals(listOf(MonitoringInterval.TWELVE_HOURS), scheduler.scheduledIntervals)
        } finally {
            collection?.cancel()
            viewModelStore.clear()
        }
    }

    @Test
    fun backupExportReportsSuccessForSelectedDestination() = runTest {
        val exporter = FakeMangaBackupExporter()
        val viewModel = SettingsViewModel(
            FakeLibraryPreferencesRepository(),
            exporter,
            FakeMangaBackupImporter(),
        )
        val viewModelStore = ViewModelStore().apply { put("settings", viewModel) }
        val destination = Uri.parse("content://backup/test.json")
        var collection: Job? = null
        try {
            collection = backgroundScope.launch(StandardTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }
            advanceUntilIdle()

            viewModel.exportBackup(destination)
            advanceUntilIdle()

            assertEquals(destination, exporter.destination)
            assertEquals(false, viewModel.uiState.value.isBackupInProgress)
            assertEquals(false, viewModel.uiState.value.isBackupError)
            org.junit.Assert.assertNotNull(viewModel.uiState.value.backupMessage)
        } finally {
            collection?.cancel()
            viewModelStore.clear()
        }
    }

    @Test
    fun backupExportReportsFailureWhenSavingFails() = runTest {
        val exporter = FakeMangaBackupExporter().apply { failure = IOException() }
        val viewModel = SettingsViewModel(
            FakeLibraryPreferencesRepository(),
            exporter,
            FakeMangaBackupImporter(),
        )
        val viewModelStore = ViewModelStore().apply { put("settings", viewModel) }
        var collection: Job? = null
        try {
            collection = backgroundScope.launch(StandardTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }
            advanceUntilIdle()

            viewModel.exportBackup(Uri.parse("content://backup/test.json"))
            advanceUntilIdle()

            assertEquals(false, viewModel.uiState.value.isBackupInProgress)
            assertEquals(true, viewModel.uiState.value.isBackupError)
            org.junit.Assert.assertNotNull(viewModel.uiState.value.backupMessage)
        } finally {
            collection?.cancel()
            viewModelStore.clear()
        }
    }

    @Test
    fun backupImportReportsSuccessForSelectedSource() = runTest {
        val importer = FakeMangaBackupImporter()
        val viewModel = SettingsViewModel(
            FakeLibraryPreferencesRepository(),
            FakeMangaBackupExporter(),
            importer,
        )
        val viewModelStore = ViewModelStore().apply { put("settings", viewModel) }
        val source = Uri.parse("content://backup/source.json")
        var collection: Job? = null
        try {
            collection = backgroundScope.launch(StandardTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }
            advanceUntilIdle()

            viewModel.importBackup(source)
            advanceUntilIdle()

            assertEquals(source, importer.source)
            assertEquals(false, viewModel.uiState.value.isBackupInProgress)
            assertEquals(true, viewModel.uiState.value.backupMessage?.contains("merged") == true)
            assertEquals(false, viewModel.uiState.value.isBackupError)
        } finally {
            collection?.cancel()
            viewModelStore.clear()
        }
    }

    @Test
    fun invalidBackupImportReportsFailureWithoutSuccessMessage() = runTest {
        val importer = FakeMangaBackupImporter().apply {
            failure = IllegalArgumentException("Invalid backup.")
        }
        val viewModel = SettingsViewModel(
            FakeLibraryPreferencesRepository(),
            FakeMangaBackupExporter(),
            importer,
        )
        val viewModelStore = ViewModelStore().apply { put("settings", viewModel) }
        var collection: Job? = null
        try {
            collection = backgroundScope.launch(StandardTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }
            advanceUntilIdle()

            viewModel.importBackup(Uri.parse("content://backup/invalid.json"))
            advanceUntilIdle()

            assertEquals(false, viewModel.uiState.value.isBackupInProgress)
            assertEquals(true, viewModel.uiState.value.isBackupError)
            org.junit.Assert.assertNotNull(viewModel.uiState.value.backupMessage)
        } finally {
            collection?.cancel()
            viewModelStore.clear()
        }
    }

    private class FakeLibraryPreferencesRepository : LibraryPreferencesRepository {
        val layout = MutableStateFlow(LibraryLayout.LIST)
        val themeMode = MutableStateFlow(AppThemeMode.SYSTEM)
        val monitoringInterval = MutableStateFlow(MonitoringInterval.DEFAULT)

        override fun observeLayout(): Flow<LibraryLayout> = layout

        override suspend fun setLayout(layout: LibraryLayout) {
            this.layout.value = layout
        }

        override fun observeThemeMode(): Flow<AppThemeMode> = themeMode

        override suspend fun setThemeMode(themeMode: AppThemeMode) {
            this.themeMode.value = themeMode
        }

        override fun observeMonitoringInterval(): Flow<MonitoringInterval> = monitoringInterval

        override suspend fun setMonitoringInterval(interval: MonitoringInterval) {
            monitoringInterval.value = interval
        }
    }

    private class RecordingMonitoringScheduler : MonitoringScheduler {
        val scheduledIntervals = mutableListOf<MonitoringInterval>()

        override fun ensurePeriodicMonitoring() = Unit

        override fun ensurePeriodicMonitoring(interval: MonitoringInterval) {
            scheduledIntervals += interval
        }

        override fun enqueueAutomaticMangaCheck(mangaId: String) = Unit

        override fun cancelAutomaticMangaCheck(mangaId: String) = Unit
    }

    private class FakeMangaBackupExporter : MangaBackupExporter {
        var destination: Uri? = null
        var failure: IOException? = null

        override suspend fun export(destination: Uri) {
            this.destination = destination
            failure?.let { throw it }
        }
    }

    private class FakeMangaBackupImporter : MangaBackupImporter {
        var source: Uri? = null
        var failure: Exception? = null

        override suspend fun importBackup(source: Uri) {
            this.source = source
            failure?.let { throw it }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    class SettingsMainDispatcherRule : TestWatcher() {
        private val dispatcher = StandardTestDispatcher()

        override fun starting(description: Description) {
            Dispatchers.setMain(dispatcher)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }
}
