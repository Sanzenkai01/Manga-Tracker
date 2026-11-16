package com.sanzenkai.mangatracker.ui.library

import androidx.lifecycle.ViewModelStore
import com.sanzenkai.mangatracker.core.domain.model.AppThemeMode
import com.sanzenkai.mangatracker.core.domain.model.CheckAttempt
import com.sanzenkai.mangatracker.core.domain.model.ChapterSyncResult
import com.sanzenkai.mangatracker.core.domain.model.DiscoveredChapter
import com.sanzenkai.mangatracker.core.domain.model.LibraryLayout
import com.sanzenkai.mangatracker.core.domain.model.Manga
import com.sanzenkai.mangatracker.core.domain.model.MangaMetadataUpdate
import com.sanzenkai.mangatracker.core.domain.model.MangaBackupSnapshot
import com.sanzenkai.mangatracker.core.domain.model.MangaRegistrationResult
import com.sanzenkai.mangatracker.core.domain.model.MangaSummary
import com.sanzenkai.mangatracker.core.domain.model.MonitoringSettings
import com.sanzenkai.mangatracker.core.domain.model.ReadingAction
import com.sanzenkai.mangatracker.core.domain.model.ReadingActionOrigin
import com.sanzenkai.mangatracker.core.domain.model.ReadingHistoryEntry
import com.sanzenkai.mangatracker.core.domain.model.SiteConfiguration
import com.sanzenkai.mangatracker.core.domain.model.TrackedChapter
import com.sanzenkai.mangatracker.core.domain.model.ValidatedChapterSnapshot
import com.sanzenkai.mangatracker.core.domain.repository.MangaRepository
import com.sanzenkai.mangatracker.core.domain.repository.LibraryPreferencesRepository
import com.sanzenkai.mangatracker.monitoring.MonitoringScheduler
import com.sanzenkai.mangatracker.notifications.ChapterNotificationPublisher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun repositoryFailureIsPublishedInsteadOfAnEmptySuccess() = runTest {
        val viewModel = LibraryViewModel(
            TestMangaRepository(flow {
                throw IllegalStateException("Local database unavailable")
            }),
            TestLibraryPreferencesRepository,
        )
        val store = ViewModelStore()
        store.put("library", viewModel)
        val job = launch(StandardTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        advanceUntilIdle()

        assertEquals(
            "Não foi possível carregar a biblioteca.",
            viewModel.uiState.value.error,
        )
        assertFalse(viewModel.uiState.value.isLoading)
        job.cancel()
        store.clear()
    }

    @Test
    fun selectedMangaCanBeReadOrRemovedAndItsBackgroundWorkIsCancelled() = runTest {
        val repository = TestMangaRepository(flowOf(emptyList()))
        val scheduler = RecordingMonitoringScheduler()
        val notificationPublisher = RecordingChapterNotificationPublisher()
        val viewModel = LibraryViewModel(
            mangaRepository = repository,
            preferencesRepository = TestLibraryPreferencesRepository,
            monitoringScheduler = scheduler,
            notificationPublisher = notificationPublisher,
        )
        val store = ViewModelStore()
        store.put("library", viewModel)
        val job = launch(StandardTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.selectManga("manga-1")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSelectionMode)

        viewModel.markSelectedAsRead()
        advanceUntilIdle()
        assertEquals(setOf("manga-1"), repository.lastReadMangaIds)
        assertEquals(ReadingAction.READ, repository.lastReadAction)
        assertEquals(ReadingActionOrigin.LIBRARY_BULK_ACTION, repository.lastReadOrigin)
        assertFalse(viewModel.uiState.value.isSelectionMode)

        viewModel.selectManga("manga-1")
        advanceUntilIdle()
        viewModel.markSelectedAsUnread()
        advanceUntilIdle()
        assertEquals(ReadingAction.UNREAD, repository.lastReadAction)
        assertFalse(viewModel.uiState.value.isSelectionMode)

        viewModel.selectManga("manga-1")
        advanceUntilIdle()
        viewModel.removeSelectedMangas()
        advanceUntilIdle()
        assertEquals(setOf("manga-1"), repository.removedMangaIds)
        assertEquals(listOf("manga-1"), scheduler.cancelledMangaIds)
        assertEquals(setOf("manga-1"), notificationPublisher.cancelledMangaIds)
        assertFalse(viewModel.uiState.value.isSelectionMode)

        job.cancel()
        store.clear()
    }

    private class TestMangaRepository(
        private val library: Flow<List<MangaSummary>>,
    ) : MangaRepository {
        var lastReadMangaIds: Set<String>? = null
        var lastReadAction: ReadingAction? = null
        var lastReadOrigin: ReadingActionOrigin? = null
        var removedMangaIds: Set<String>? = null

        override suspend fun createBackupSnapshot(exportedAtEpochMillis: Long) =
            error("Not used in this test")

        override suspend fun restoreBackup(snapshot: MangaBackupSnapshot) =
            error("Not used in this test")

        override fun observeLibrary(): Flow<List<MangaSummary>> = library

        override fun observeManga(mangaId: String): Flow<Manga?> = emptyFlow()

        override suspend fun saveManga(manga: Manga) = error("Not used in this test")

        override suspend fun fillMissingMangaMetadata(
            mangaId: String,
            metadata: MangaMetadataUpdate,
            updatedAtEpochMillis: Long,
        ): Boolean = error("Not used in this test")

        override suspend fun registerManga(
            manga: Manga,
            initialChapters: List<DiscoveredChapter>,
            detectedAtEpochMillis: Long,
            monitoringEnabled: Boolean,
        ): MangaRegistrationResult = error("Not used in this test")

        override fun observeChapters(mangaId: String): Flow<List<TrackedChapter>> = emptyFlow()

        override fun observeUnreadCount(mangaId: String): Flow<Int> = emptyFlow()

        override suspend fun saveFoundChapters(
            mangaId: String,
            snapshot: ValidatedChapterSnapshot,
            detectedAtEpochMillis: Long,
        ): ChapterSyncResult = error("Not used in this test")

        override suspend fun recordSuccessfulCheck(
            mangaId: String,
            snapshot: ValidatedChapterSnapshot,
            attempt: CheckAttempt,
        ): ChapterSyncResult = error("Not used in this test")

        override suspend fun setChapterReadState(
            chapterId: String,
            action: ReadingAction,
            origin: ReadingActionOrigin,
            occurredAtEpochMillis: Long,
        ): Boolean = error("Not used in this test")

        override suspend fun setMangaReadState(
            mangaIds: Set<String>,
            action: ReadingAction,
            origin: ReadingActionOrigin,
            occurredAtEpochMillis: Long,
        ): Int {
            lastReadMangaIds = mangaIds
            lastReadAction = action
            lastReadOrigin = origin
            return mangaIds.size
        }

        override suspend fun removeMangas(mangaIds: Set<String>): Int {
            removedMangaIds = mangaIds
            return mangaIds.size
        }

        override suspend fun markChapterNotificationPosted(
            chapterId: String,
            postedAtEpochMillis: Long,
        ): Boolean = error("Not used in this test")

        override suspend fun getPendingChapterNotifications(mangaId: String): List<TrackedChapter> =
            error("Not used in this test")

        override fun observeReadingHistory(chapterId: String): Flow<List<ReadingHistoryEntry>> =
            emptyFlow()

        override suspend fun saveSiteConfiguration(configuration: SiteConfiguration) =
            error("Not used in this test")

        override suspend fun getSiteConfiguration(normalizedHost: String): SiteConfiguration? =
            error("Not used in this test")

        override fun observeMonitoringSettings(mangaId: String): Flow<MonitoringSettings?> =
            emptyFlow()

        override suspend fun saveMonitoringSettings(settings: MonitoringSettings) =
            error("Not used in this test")

        override suspend fun setMonitoringEnabled(mangaId: String, isEnabled: Boolean) =
            error("Not used in this test")

        override fun observeCheckAttempts(mangaId: String): Flow<List<CheckAttempt>> =
            emptyFlow()

        override suspend fun recordCheckAttempt(attempt: CheckAttempt) =
            error("Not used in this test")
    }

    private object TestLibraryPreferencesRepository : LibraryPreferencesRepository {
        override fun observeLayout(): Flow<LibraryLayout> = flowOf(LibraryLayout.LIST)

        override suspend fun setLayout(layout: LibraryLayout) = Unit

        override fun observeThemeMode(): Flow<AppThemeMode> = flowOf(AppThemeMode.SYSTEM)

        override suspend fun setThemeMode(themeMode: AppThemeMode) = Unit
    }

    private class RecordingMonitoringScheduler : MonitoringScheduler {
        val cancelledMangaIds = mutableListOf<String>()

        override fun ensurePeriodicMonitoring() = Unit

        override fun enqueueAutomaticMangaCheck(mangaId: String) = Unit

        override fun cancelAutomaticMangaCheck(mangaId: String) {
            cancelledMangaIds += mangaId
        }
    }

    private class RecordingChapterNotificationPublisher : ChapterNotificationPublisher {
        var cancelledMangaIds: Set<String> = emptySet()

        override suspend fun publishNewChapters(
            manga: Manga,
            chapters: List<TrackedChapter>,
        ): Set<String> = emptySet()

        override fun ensureChannel() = Unit

        override fun cancelChapter(chapterId: String) = Unit

        override fun cancelMangaNotifications(mangaIds: Set<String>) {
            cancelledMangaIds = mangaIds
        }
    }

    class MainDispatcherRule : TestWatcher() {
        private val dispatcher = StandardTestDispatcher()

        override fun starting(description: Description) {
            Dispatchers.setMain(dispatcher)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }
}
