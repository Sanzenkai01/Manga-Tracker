package com.sanzenkai.mangatracker.core.data.repository

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.sanzenkai.mangatracker.core.data.local.MIGRATION_1_2
import com.sanzenkai.mangatracker.core.data.local.MIGRATION_2_3
import com.sanzenkai.mangatracker.core.data.local.MangaTrackerDatabase
import com.sanzenkai.mangatracker.core.data.local.entity.ChapterEntity
import com.sanzenkai.mangatracker.core.data.local.entity.MonitoringSettingsEntity
import com.sanzenkai.mangatracker.core.domain.model.CheckAttempt
import com.sanzenkai.mangatracker.core.domain.model.DiscoveredChapter
import com.sanzenkai.mangatracker.core.domain.model.Manga
import com.sanzenkai.mangatracker.core.domain.model.MangaMetadataUpdate
import com.sanzenkai.mangatracker.core.domain.model.MangaBackupEntry
import com.sanzenkai.mangatracker.core.domain.model.MangaBackupSnapshot
import com.sanzenkai.mangatracker.core.domain.model.MangaRegistrationResult
import com.sanzenkai.mangatracker.core.domain.model.MonitoringSettings
import com.sanzenkai.mangatracker.core.domain.model.ReadingAction
import com.sanzenkai.mangatracker.core.domain.model.ReadingActionOrigin
import com.sanzenkai.mangatracker.core.domain.model.ReadingHistoryEntry
import com.sanzenkai.mangatracker.core.domain.model.SiteConfiguration
import com.sanzenkai.mangatracker.core.domain.model.TrackedChapter
import com.sanzenkai.mangatracker.core.domain.model.ValidatedChapterSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class RoomMangaRepositoryTest {
    private lateinit var context: Context
    private lateinit var databaseDirectory: File
    private lateinit var databaseName: String
    private lateinit var database: MangaTrackerDatabase
    private lateinit var repository: RoomMangaRepository
    private var nextId = 0

    @Before
    fun setUp() {
        databaseName = "manga-tracker.db"
        databaseDirectory = File(
            System.getProperty("java.io.tmpdir"),
            "manga-tracker-test-${UUID.randomUUID()}",
        )
        check(databaseDirectory.mkdirs())
        context = FileDatabaseContext(RuntimeEnvironment.getApplication(), databaseDirectory)
        database = openDatabase()
        repository = newRepository()
    }

    @After
    fun tearDown() {
        database.close()
        databaseDirectory.deleteRecursively()
    }

    @Test
    fun observingChapterDoesNotChangeReadStateButExplicitActionsDo() = runBlocking {
        repository.saveManga(testManga)
        val chapterId = repository.saveFoundChapters(
            mangaId = testManga.id,
            snapshot = completeSnapshot(testChapter("chapter-1")),
            detectedAtEpochMillis = 10L,
        ).newChapterIds.single()

        assertFalse(repository.observeChapters(testManga.id).first().single().isRead)
        assertTrue(repository.observeReadingHistory(chapterId).first().isEmpty())
        assertFalse(repository.observeChapters(testManga.id).first().single().isRead)
        assertFalse(
            repository.setChapterReadState(
                chapterId = chapterId,
                action = ReadingAction.UNREAD,
                origin = ReadingActionOrigin.DETAIL_SCREEN,
                occurredAtEpochMillis = 15L,
            ),
        )
        assertTrue(repository.observeReadingHistory(chapterId).first().isEmpty())

        assertTrue(
            repository.setChapterReadState(
                chapterId = chapterId,
                action = ReadingAction.READ,
                origin = ReadingActionOrigin.DETAIL_SCREEN,
                occurredAtEpochMillis = 20L,
            ),
        )
        assertFalse(
            repository.setChapterReadState(
                chapterId = chapterId,
                action = ReadingAction.READ,
                origin = ReadingActionOrigin.DETAIL_SCREEN,
                occurredAtEpochMillis = 21L,
            ),
        )
        assertTrue(
            repository.setChapterReadState(
                chapterId = chapterId,
                action = ReadingAction.UNREAD,
                origin = ReadingActionOrigin.NOTIFICATION_ACTION,
                occurredAtEpochMillis = 30L,
            ),
        )

        assertFalse(repository.observeChapters(testManga.id).first().single().isRead)
        assertEquals(
            listOf(ReadingAction.UNREAD, ReadingAction.READ),
            repository.observeReadingHistory(chapterId).first().map { it.action },
        )
    }

    @Test
    fun backupSnapshotKeepsReadingAndMonitoringDataTogether() = runBlocking {
        repository.registerManga(
            manga = testManga,
            initialChapters = listOf(testChapter("chapter-1")),
            detectedAtEpochMillis = 10L,
        )
        val chapterId = repository.observeChapters(testManga.id).first().single().id
        repository.setChapterReadState(
            chapterId = chapterId,
            action = ReadingAction.READ,
            origin = ReadingActionOrigin.DETAIL_SCREEN,
            occurredAtEpochMillis = 20L,
        )
        repository.saveSiteConfiguration(
            SiteConfiguration(
                normalizedHost = "example.test",
                parserId = "manual-css",
                formatVersion = 2,
                configJson = """{"chapter":"a.chapter"}""",
                updatedAtEpochMillis = 25L,
                validatedAtEpochMillis = 24L,
            ),
        )
        repository.saveMonitoringSettings(
            MonitoringSettings(
                mangaId = testManga.id,
                isEnabled = true,
                lastAttemptAtEpochMillis = 30L,
                lastSuccessAtEpochMillis = 29L,
                lastOutcome = "SUCCESS",
            ),
        )
        repository.recordCheckAttempt(
            CheckAttempt(
                id = "backup-attempt",
                mangaId = testManga.id,
                trigger = "MANUAL",
                startedAtEpochMillis = 30L,
                finishedAtEpochMillis = 31L,
                outcome = "SUCCESS",
                errorCode = null,
            ),
        )

        val backup = repository.createBackupSnapshot(exportedAtEpochMillis = 40L)
        val backedUpManga = backup.mangas.single()

        assertEquals(40L, backup.exportedAtEpochMillis)
        assertEquals(testManga, backedUpManga.manga)
        assertTrue(backedUpManga.chapters.single().isRead)
        assertEquals(ReadingAction.READ, backedUpManga.readingHistory.single().action)
        assertTrue(backedUpManga.monitoringSettings?.isEnabled == true)
        assertEquals("backup-attempt", backedUpManga.checkAttempts.single().id)
        assertEquals("manual-css", backup.siteConfigurations.single().parserId)
    }

    @Test
    fun restoringBackupMergesDataWithoutOverwritingCurrentLibraryAndIsIdempotent() = runBlocking {
        val localManga = testManga.copy(id = "local-manga")
        repository.registerManga(
            manga = localManga,
            initialChapters = listOf(testChapter("chapter-1")),
            detectedAtEpochMillis = 10L,
        )
        val localChapter = repository.observeChapters(localManga.id).first().single()
        repository.setChapterReadState(
            chapterId = localChapter.id,
            action = ReadingAction.READ,
            origin = ReadingActionOrigin.DETAIL_SCREEN,
            occurredAtEpochMillis = 20L,
        )
        repository.saveMonitoringSettings(
            MonitoringSettings(
                mangaId = localManga.id,
                isEnabled = false,
                lastAttemptAtEpochMillis = 21L,
                lastSuccessAtEpochMillis = 20L,
                lastOutcome = "FAILED",
            ),
        )

        val backupManga = testManga.copy(id = "backup-manga", title = "Backup title")
        val backupOnlyManga = Manga(
            id = "backup-only-manga",
            title = "Another manga",
            originalCoverUrl = null,
            seriesUrl = "https://other.test/series",
            entryChapterUrl = "https://other.test/chapter/5",
            normalizedDomain = "other.test",
            createdAtEpochMillis = 2L,
            updatedAtEpochMillis = 3L,
        )
        val existingChapterFromBackup = TrackedChapter(
            id = "backup-chapter-1",
            mangaId = backupManga.id,
            sourceChapterKey = "chapter-1",
            label = "Chapter 1",
            chapterKind = "NUMBERED",
            normalizedNumber = "1",
            url = "https://example.test/1",
            publishedAtEpochMillis = null,
            firstDetectedAtEpochMillis = 10L,
            notificationPostedAtEpochMillis = null,
            isRead = false,
        )
        val newChapterFromBackup = existingChapterFromBackup.copy(
            id = "backup-chapter-2",
            sourceChapterKey = "chapter-2",
            label = "Chapter 2",
            normalizedNumber = "2",
            url = "https://example.test/2",
            firstDetectedAtEpochMillis = 15L,
            isRead = true,
        )
        val backupOnlyChapter = newChapterFromBackup.copy(
            id = "backup-only-chapter",
            mangaId = backupOnlyManga.id,
            sourceChapterKey = "chapter-5",
            label = "Chapter 5",
            normalizedNumber = "5",
            url = backupOnlyManga.entryChapterUrl,
            isRead = false,
        )
        val backup = MangaBackupSnapshot(
            exportedAtEpochMillis = 100L,
            mangas = listOf(
                MangaBackupEntry(
                    manga = backupManga,
                    chapters = listOf(existingChapterFromBackup, newChapterFromBackup),
                    readingHistory = listOf(
                        ReadingHistoryEntry(
                            id = 1L,
                            chapterId = existingChapterFromBackup.id,
                            action = ReadingAction.UNREAD,
                            origin = "DETAIL_SCREEN",
                            occurredAtEpochMillis = 15L,
                        ),
                        ReadingHistoryEntry(
                            id = 2L,
                            chapterId = newChapterFromBackup.id,
                            action = ReadingAction.READ,
                            origin = "DETAIL_SCREEN",
                            occurredAtEpochMillis = 16L,
                        ),
                    ),
                    monitoringSettings = MonitoringSettings(
                        mangaId = backupManga.id,
                        isEnabled = true,
                        lastAttemptAtEpochMillis = 25L,
                        lastSuccessAtEpochMillis = 24L,
                        lastOutcome = "SUCCESS",
                    ),
                    checkAttempts = listOf(
                        CheckAttempt(
                            id = "backup-attempt",
                            mangaId = backupManga.id,
                            trigger = "MANUAL",
                            startedAtEpochMillis = 25L,
                            finishedAtEpochMillis = 26L,
                            outcome = "SUCCESS",
                            errorCode = null,
                        ),
                    ),
                ),
                MangaBackupEntry(
                    manga = backupOnlyManga,
                    chapters = listOf(backupOnlyChapter),
                    readingHistory = emptyList(),
                    monitoringSettings = null,
                    checkAttempts = emptyList(),
                ),
            ),
            siteConfigurations = listOf(
                SiteConfiguration(
                    normalizedHost = "example.test",
                    parserId = "manual-css",
                    formatVersion = 1,
                    configJson = """{"chapter":"a.chapter"}""",
                    updatedAtEpochMillis = 30L,
                    validatedAtEpochMillis = 29L,
                ),
            ),
        )

        repository.restoreBackup(backup)

        assertEquals(2, repository.observeLibrary().first().size)
        assertEquals("Test manga", repository.observeManga(localManga.id).first()?.title)
        val restoredChapters = repository.observeChapters(localManga.id).first()
        assertEquals(2, restoredChapters.size)
        assertTrue(restoredChapters.single { it.sourceChapterKey == "chapter-1" }.isRead)
        assertTrue(restoredChapters.single { it.sourceChapterKey == "chapter-2" }.isRead)
        assertFalse(repository.observeLibrary().first().single { it.id == localManga.id }.monitoringEnabled)
        assertEquals(
            listOf(ReadingAction.READ, ReadingAction.UNREAD),
            repository.observeReadingHistory(localChapter.id).first().map { it.action },
        )
        assertEquals("backup-attempt", repository.observeCheckAttempts(localManga.id).first().single().id)
        assertEquals("manual-css", repository.getSiteConfiguration("example.test")?.parserId)
        assertEquals(
            1,
            repository.observeChapters(backupOnlyManga.id).first().size,
        )

        repository.restoreBackup(backup)

        assertEquals(2, repository.observeChapters(localManga.id).first().size)
        assertEquals(2, repository.observeReadingHistory(localChapter.id).first().size)
        assertEquals(1, repository.observeCheckAttempts(localManga.id).first().size)
    }

    @Test
    fun failedBackupMergeRollsBackPreviouslyInsertedManga() = runBlocking {
        val existingManga = testManga.copy(
            id = "conflicting-id",
            seriesUrl = "https://existing.test/series",
            entryChapterUrl = "https://existing.test/chapter/1",
            normalizedDomain = "existing.test",
        )
        repository.saveManga(existingManga)
        val firstBackupManga = testManga.copy(
            id = "new-backup-manga",
            seriesUrl = "https://new.test/series",
            entryChapterUrl = "https://new.test/chapter/1",
            normalizedDomain = "new.test",
        )
        val conflictingBackupManga = testManga.copy(
            id = existingManga.id,
            seriesUrl = "https://unrelated.test/series",
            entryChapterUrl = "https://unrelated.test/chapter/1",
            normalizedDomain = "unrelated.test",
        )
        val backup = MangaBackupSnapshot(
            exportedAtEpochMillis = 100L,
            mangas = listOf(firstBackupManga, conflictingBackupManga).map { manga ->
                MangaBackupEntry(
                    manga = manga,
                    chapters = emptyList(),
                    readingHistory = emptyList(),
                    monitoringSettings = null,
                    checkAttempts = emptyList(),
                )
            },
            siteConfigurations = emptyList(),
        )

        var failed = false
        try {
            repository.restoreBackup(backup)
        } catch (_: IllegalArgumentException) {
            failed = true
        }

        assertTrue(failed)
        assertEquals(
            listOf(existingManga.id),
            repository.observeLibrary().first().map { it.id },
        )
    }

    @Test
    fun notificationDeliveryIsRecordedOnceWithoutChangingReadingState() = runBlocking {
        repository.saveManga(testManga)
        val chapterId = repository.saveFoundChapters(
            mangaId = testManga.id,
            snapshot = completeSnapshot(testChapter("chapter-1")),
            detectedAtEpochMillis = 10L,
        ).newChapterIds.single()

        assertTrue(repository.markChapterNotificationPosted(chapterId, 20L))
        assertFalse(repository.markChapterNotificationPosted(chapterId, 30L))
        val chapter = repository.observeChapters(testManga.id).first().single()

        assertEquals(20L, chapter.notificationPostedAtEpochMillis)
        assertFalse(chapter.isRead)
        assertTrue(repository.observeReadingHistory(chapterId).first().isEmpty())
    }

    @Test
    fun notificationReadActionUpdatesThePersistentReadStateAndHistory() = runBlocking {
        repository.saveManga(testManga)
        val chapterId = repository.saveFoundChapters(
            mangaId = testManga.id,
            snapshot = completeSnapshot(testChapter("chapter-1")),
            detectedAtEpochMillis = 10L,
        ).newChapterIds.single()

        assertTrue(
            repository.setChapterReadState(
                chapterId = chapterId,
                action = ReadingAction.READ,
                origin = ReadingActionOrigin.NOTIFICATION_ACTION,
                occurredAtEpochMillis = 20L,
            ),
        )
        assertFalse(
            repository.setChapterReadState(
                chapterId = chapterId,
                action = ReadingAction.READ,
                origin = ReadingActionOrigin.NOTIFICATION_ACTION,
                occurredAtEpochMillis = 30L,
            ),
        )

        assertTrue(repository.observeChapters(testManga.id).first().single().isRead)
        assertEquals(ReadingAction.READ, repository.observeReadingHistory(chapterId).first().single().action)
        assertEquals(
            ReadingActionOrigin.NOTIFICATION_ACTION.name,
            repository.observeReadingHistory(chapterId).first().single().origin,
        )
    }

    @Test
    fun bulkReadStateUpdatesOnlyChangedChaptersAndRecordsItsOrigin() = runBlocking {
        repository.registerManga(
            manga = testManga,
            initialChapters = listOf(testChapter("chapter-1"), testChapter("chapter-2")),
            detectedAtEpochMillis = 10L,
        )
        val chapterIds = repository.observeChapters(testManga.id).first()
            .associate { it.sourceChapterKey to it.id }

        assertEquals(
            2,
            repository.setMangaReadState(
                mangaIds = setOf(testManga.id),
                action = ReadingAction.READ,
                origin = ReadingActionOrigin.LIBRARY_BULK_ACTION,
                occurredAtEpochMillis = 20L,
            ),
        )
        assertEquals(
            0,
            repository.setMangaReadState(
                mangaIds = setOf(testManga.id),
                action = ReadingAction.READ,
                origin = ReadingActionOrigin.LIBRARY_BULK_ACTION,
                occurredAtEpochMillis = 30L,
            ),
        )
        assertTrue(repository.observeChapters(testManga.id).first().all { it.isRead })
        chapterIds.values.forEach { chapterId ->
            val history = repository.observeReadingHistory(chapterId).first()
            assertEquals(1, history.size)
            assertEquals(ReadingAction.READ, history.single().action)
            assertEquals(ReadingActionOrigin.LIBRARY_BULK_ACTION.name, history.single().origin)
            assertEquals(20L, history.single().occurredAtEpochMillis)
        }
    }

    @Test
    fun removingMangaAtomicallyRemovesItsChaptersHistoryAndMonitoringData() = runBlocking {
        repository.registerManga(
            manga = testManga,
            initialChapters = listOf(testChapter("chapter-1")),
            detectedAtEpochMillis = 10L,
        )
        val chapterId = repository.observeChapters(testManga.id).first().single().id
        repository.setChapterReadState(
            chapterId = chapterId,
            action = ReadingAction.READ,
            origin = ReadingActionOrigin.DETAIL_SCREEN,
            occurredAtEpochMillis = 20L,
        )
        repository.recordCheckAttempt(
            CheckAttempt(
                id = "attempt-to-remove",
                mangaId = testManga.id,
                trigger = "MANUAL",
                startedAtEpochMillis = 30L,
                finishedAtEpochMillis = 40L,
                outcome = "SUCCESS",
                errorCode = null,
            ),
        )
        val retainedManga = testManga.copy(
            id = "manga-2",
            title = "Retained manga",
            seriesUrl = "https://example.test/other-series",
            entryChapterUrl = "https://example.test/other/1",
        )
        repository.registerManga(
            manga = retainedManga,
            initialChapters = listOf(testChapter("other-chapter-1")),
            detectedAtEpochMillis = 50L,
        )

        assertEquals(1, repository.removeMangas(setOf(testManga.id)))
        assertEquals(
            listOf("Retained manga"),
            repository.observeLibrary().first().map { it.title },
        )
        assertTrue(repository.observeChapters(testManga.id).first().isEmpty())
        assertTrue(repository.observeReadingHistory(chapterId).first().isEmpty())
        assertNull(repository.observeMonitoringSettings(testManga.id).first())
        assertTrue(repository.observeCheckAttempts(testManga.id).first().isEmpty())
        assertEquals(0, repository.removeMangas(setOf(testManga.id)))
        assertEquals(1, repository.observeChapters(retainedManga.id).first().size)
    }

    @Test
    fun refreshingChaptersPreservesExistingReadStateAndNewChaptersStartUnread() = runBlocking {
        repository.saveManga(testManga)
        val firstChapter = testChapter(sourceChapterKey = "chapter-1")
            .copy(publishedAtEpochMillis = 5L)
        val originalId = repository.saveFoundChapters(
            mangaId = testManga.id,
            snapshot = completeSnapshot(firstChapter),
            detectedAtEpochMillis = 10L,
        ).newChapterIds.single()
        repository.setChapterReadState(
            chapterId = originalId,
            action = ReadingAction.READ,
            origin = ReadingActionOrigin.DETAIL_SCREEN,
            occurredAtEpochMillis = 20L,
        )

        val refresh = repository.saveFoundChapters(
            mangaId = testManga.id,
            snapshot = completeSnapshot(
                firstChapter.copy(
                    label = "Chapter 1 revised",
                    url = "https://example.test/1-new",
                    publishedAtEpochMillis = null,
                ),
                testChapter(sourceChapterKey = "chapter-2"),
            ),
            detectedAtEpochMillis = 40L,
        )

        val chapters = repository.observeChapters(testManga.id).first()
        val refreshedOldChapter = chapters.single { it.sourceChapterKey == "chapter-1" }
        val newChapter = chapters.single { it.sourceChapterKey == "chapter-2" }

        assertEquals(1, refresh.newChapterIds.size)
        assertFalse(refresh.newChapterIds.contains(originalId))
        assertEquals("Chapter 1 revised", refreshedOldChapter.label)
        assertEquals("https://example.test/1-new", refreshedOldChapter.url)
        assertEquals(5L, refreshedOldChapter.publishedAtEpochMillis)
        assertTrue(refreshedOldChapter.isRead)
        assertFalse(newChapter.isRead)
        assertEquals(1, repository.observeUnreadCount(testManga.id).first())

        val repeatedSync = repository.saveFoundChapters(
            mangaId = testManga.id,
            snapshot = completeSnapshot(
                firstChapter.copy(
                    label = "Chapter 1 revised",
                    url = "https://example.test/1-new",
                    publishedAtEpochMillis = null,
                ),
                testChapter(sourceChapterKey = "chapter-2"),
            ),
            detectedAtEpochMillis = 50L,
        )
        assertTrue(repeatedSync.newChapterIds.isEmpty())
        assertTrue(
            repository.observeChapters(testManga.id).first()
                .single { it.sourceChapterKey == "chapter-1" }
                .isRead,
        )
    }

    @Test
    fun equalDetectionTimesUseChapterNumberForStableNewestFirstOrdering() = runBlocking {
        repository.saveManga(testManga)
        repository.saveFoundChapters(
            mangaId = testManga.id,
            snapshot = completeSnapshot(
                testChapter("chapter-1"),
                testChapter("chapter-2"),
                testChapter("chapter-10"),
            ),
            detectedAtEpochMillis = 100L,
        )

        assertEquals(
            listOf("Chapter 10", "Chapter 2", "Chapter 1"),
            repository.observeChapters(testManga.id).first().map { it.label },
        )
        assertEquals(
            "Chapter 10",
            repository.observeLibrary().first().single().latestChapterLabel,
        )
    }

    @Test
    fun successfulCheckAtomicallyPersistsSnapshotAndAttemptWithoutResettingReadState() = runBlocking {
        repository.saveManga(testManga)
        val existingChapterId = repository.saveFoundChapters(
            mangaId = testManga.id,
            snapshot = completeSnapshot(testChapter("chapter-1")),
            detectedAtEpochMillis = 10L,
        ).newChapterIds.single()
        repository.setChapterReadState(
            chapterId = existingChapterId,
            action = ReadingAction.READ,
            origin = ReadingActionOrigin.DETAIL_SCREEN,
            occurredAtEpochMillis = 20L,
        )

        val result = repository.recordSuccessfulCheck(
            mangaId = testManga.id,
            snapshot = completeSnapshot(
                testChapter("chapter-1"),
                testChapter("chapter-2"),
            ),
            attempt = CheckAttempt(
                id = "success-1",
                mangaId = testManga.id,
                trigger = "AUTOMATIC",
                startedAtEpochMillis = 30L,
                finishedAtEpochMillis = 40L,
                outcome = "SUCCESS",
                errorCode = null,
            ),
        )

        assertEquals(1, result.newChapterIds.size)
        assertEquals(2, repository.observeChapters(testManga.id).first().size)
        assertTrue(
            repository.observeChapters(testManga.id).first()
                .single { it.sourceChapterKey == "chapter-1" }
                .isRead,
        )
        assertFalse(
            repository.observeChapters(testManga.id).first()
                .single { it.sourceChapterKey == "chapter-2" }
                .isRead,
        )
        assertEquals(40L, repository.observeMonitoringSettings(testManga.id).first()?.lastSuccessAtEpochMillis)
        val libraryManga = repository.observeLibrary().first().single()
        assertEquals(1, libraryManga.unreadChapterCount)
        assertEquals(1, libraryManga.newUnreadChapterCount)
        assertEquals("SUCCESS", repository.observeCheckAttempts(testManga.id).first().single().outcome)
    }

    @Test
    fun metadataRefreshFillsMissingFieldsWithoutReplacingSavedValues() = runBlocking {
        val mangaWithMissingMetadata = testManga.copy(
            id = "manga-with-missing-metadata",
            title = "",
            originalCoverUrl = null,
            seriesUrl = null,
        )
        repository.saveManga(mangaWithMissingMetadata)

        assertTrue(
            repository.fillMissingMangaMetadata(
                mangaId = mangaWithMissingMetadata.id,
                metadata = MangaMetadataUpdate(
                    title = "Recovered title",
                    originalCoverUrl = "https://cdn.example.test/recovered-cover.jpg",
                    seriesUrl = "https://example.test/recovered-series",
                ),
                updatedAtEpochMillis = 40L,
            ),
        )
        assertEquals(
            mangaWithMissingMetadata.copy(
                title = "Recovered title",
                originalCoverUrl = "https://cdn.example.test/recovered-cover.jpg",
                seriesUrl = "https://example.test/recovered-series",
                updatedAtEpochMillis = 40L,
            ),
            repository.observeManga(mangaWithMissingMetadata.id).first(),
        )
        assertEquals(
            "https://cdn.example.test/recovered-cover.jpg",
            repository.observeLibrary().first()
                .single { it.id == mangaWithMissingMetadata.id }
                .originalCoverUrl,
        )

        assertFalse(
            repository.fillMissingMangaMetadata(
                mangaId = mangaWithMissingMetadata.id,
                metadata = MangaMetadataUpdate(
                    title = "Different title",
                    originalCoverUrl = "https://cdn.example.test/other-cover.jpg",
                    seriesUrl = "https://example.test/other-series",
                ),
                updatedAtEpochMillis = 50L,
            ),
        )
        assertEquals(
            "Recovered title",
            repository.observeManga(mangaWithMissingMetadata.id).first()?.title,
        )

        val existingManga = testManga.copy(
            originalCoverUrl = "https://cdn.example.test/existing-cover.jpg",
        )
        repository.saveManga(existingManga)
        assertFalse(
            repository.fillMissingMangaMetadata(
                mangaId = existingManga.id,
                metadata = MangaMetadataUpdate(
                    title = "Replacement title",
                    originalCoverUrl = "https://cdn.example.test/replacement.jpg",
                    seriesUrl = "https://example.test/replacement-series",
                ),
                updatedAtEpochMillis = 60L,
            ),
        )
        assertEquals(existingManga, repository.observeManga(existingManga.id).first())
    }

    @Test
    fun pendingChapterNotificationsSurviveReopenAndAreClearedAfterDeliveryOrRead() = runBlocking {
        repository.registerManga(
            manga = testManga,
            initialChapters = listOf(testChapter("chapter-1")),
            detectedAtEpochMillis = 10L,
        )
        assertTrue(repository.getPendingChapterNotifications(testManga.id).isEmpty())

        val firstNewChapterId = repository.recordSuccessfulCheck(
            mangaId = testManga.id,
            snapshot = completeSnapshot(testChapter("chapter-1"), testChapter("chapter-2")),
            attempt = CheckAttempt(
                id = "success-1",
                mangaId = testManga.id,
                trigger = "AUTOMATIC",
                startedAtEpochMillis = 20L,
                finishedAtEpochMillis = 30L,
                outcome = "SUCCESS",
                errorCode = null,
            ),
        ).newChapterIds.single()
        assertEquals(
            listOf(firstNewChapterId),
            repository.getPendingChapterNotifications(testManga.id).map { it.id },
        )

        database.close()
        database = openDatabase()
        repository = newRepository()
        assertEquals(
            listOf(firstNewChapterId),
            repository.getPendingChapterNotifications(testManga.id).map { it.id },
        )
        assertTrue(repository.markChapterNotificationPosted(firstNewChapterId, 40L))
        assertTrue(repository.getPendingChapterNotifications(testManga.id).isEmpty())

        val secondNewChapterId = repository.recordSuccessfulCheck(
            mangaId = testManga.id,
            snapshot = completeSnapshot(
                testChapter("chapter-1"),
                testChapter("chapter-2"),
                testChapter("chapter-3"),
            ),
            attempt = CheckAttempt(
                id = "success-2",
                mangaId = testManga.id,
                trigger = "AUTOMATIC",
                startedAtEpochMillis = 50L,
                finishedAtEpochMillis = 60L,
                outcome = "SUCCESS",
                errorCode = null,
            ),
        ).newChapterIds.single()
        assertTrue(
            repository.setChapterReadState(
                chapterId = secondNewChapterId,
                action = ReadingAction.READ,
                origin = ReadingActionOrigin.DETAIL_SCREEN,
                occurredAtEpochMillis = 70L,
            ),
        )
        assertTrue(repository.getPendingChapterNotifications(testManga.id).isEmpty())
    }

    @Test
    fun mangaConfigurationMonitoringAndCheckAttemptsSurviveDatabaseReopen() = runBlocking {
        repository.saveManga(testManga)
        repository.saveSiteConfiguration(
            SiteConfiguration(
                normalizedHost = "example.test",
                parserId = "manual-css",
                formatVersion = 2,
                configJson = """{"chapter":"a.chapter"}""",
                updatedAtEpochMillis = 100L,
                validatedAtEpochMillis = 90L,
            ),
        )
        repository.saveMonitoringSettings(
            MonitoringSettings(
                mangaId = testManga.id,
                isEnabled = true,
                lastAttemptAtEpochMillis = 80L,
                lastSuccessAtEpochMillis = 70L,
                lastOutcome = "SUCCESS",
            ),
        )
        repository.recordCheckAttempt(
            CheckAttempt(
                id = "attempt-1",
                mangaId = testManga.id,
                trigger = "MANUAL",
                startedAtEpochMillis = 60L,
                finishedAtEpochMillis = 65L,
                outcome = "SUCCESS",
                errorCode = null,
            ),
        )

        database.close()
        database = openDatabase()
        repository = newRepository()

        assertEquals("manual-css", repository.getSiteConfiguration("example.test")?.parserId)
        assertTrue(repository.observeMonitoringSettings(testManga.id).first()?.isEnabled == true)
        assertEquals(
            "attempt-1",
            repository.observeCheckAttempts(testManga.id).first().single().id,
        )

        repository.setMonitoringEnabled(testManga.id, isEnabled = false)
        val paused = repository.observeMonitoringSettings(testManga.id).first()
        assertFalse(paused?.isEnabled == true)
        assertEquals(80L, paused?.lastAttemptAtEpochMillis)
        assertEquals(70L, paused?.lastSuccessAtEpochMillis)
    }

    @Test
    fun migrationFromVersionOnePreservesTrackingAndRenamesConfigurationTables() = runBlocking {
        database.close()
        createVersionOneDatabase()
        database = openDatabase()

        val migratedConfiguration = database.siteConfigurationDao().getByHost("example.test")
        val migratedSettings = database.monitoringSettingsDao().getByMangaId("legacy-manga")
        val migratedReadState = database.readingStateDao().get("legacy-chapter")
        val migratedChapter =
            repositoryAfterMigration().observeChapters("legacy-manga").first().single()

        assertEquals("Legacy manga", database.mangaDao().getById("legacy-manga")?.title)
        assertEquals("""{"chapter":"a.chapter"}""", migratedConfiguration?.configJson)
        assertTrue(migratedSettings?.isEnabled == true)
        assertTrue(migratedReadState?.isRead == true)
        assertTrue(migratedChapter.isRead)
        assertEquals(
            ReadingAction.READ,
            repositoryAfterMigration().observeReadingHistory("legacy-chapter").first().single().action,
        )
        assertEquals(
            "SUCCESS",
            repositoryAfterMigration().observeCheckAttempts("legacy-manga").first().single().outcome,
        )
        assertNull(database.siteConfigurationDao().getByHost("missing.test"))
        assertNotNull(database.monitoringSettingsDao().getByMangaId("legacy-manga"))
    }

    @Test
    fun chaptersAndMonitoringSettingsRequireAnExistingManga() {
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking {
                database.chapterDao().insertIfMissing(
                    ChapterEntity(
                        id = "orphan-chapter",
                        mangaId = "missing-manga",
                        sourceChapterKey = "chapter-1",
                        label = "Chapter 1",
                        chapterKind = "NUMBERED",
                        normalizedNumber = "1",
                        url = "https://example.test/1",
                        publishedAtEpochMillis = null,
                        firstDetectedAtEpochMillis = 1L,
                        notificationPostedAtEpochMillis = null,
                    ),
                )
            }
        }
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking {
                database.monitoringSettingsDao().upsert(
                    MonitoringSettingsEntity(
                        mangaId = "missing-manga",
                        isEnabled = true,
                        lastAttemptAtEpochMillis = null,
                        lastSuccessAtEpochMillis = null,
                        lastOutcome = null,
                    ),
                )
            }
        }
    }

    @Test
    fun incompleteParserResultCannotBePersistedAsAnEmptySnapshot() {
        assertThrows(IllegalArgumentException::class.java) {
            ValidatedChapterSnapshot.fromParserSuccess(
                chapters = emptyList(),
                isComplete = false,
            )
        }
    }

    @Test
    fun successfulEmptySnapshotDoesNotDeletePreviouslyStoredChapters() = runBlocking {
        repository.saveManga(testManga)
        repository.saveFoundChapters(
            mangaId = testManga.id,
            snapshot = completeSnapshot(testChapter("chapter-1")),
            detectedAtEpochMillis = 10L,
        )

        val result = repository.saveFoundChapters(
            mangaId = testManga.id,
            snapshot = completeSnapshot(),
            detectedAtEpochMillis = 20L,
        )

        assertTrue(result.newChapterIds.isEmpty())
        assertEquals(1, repository.observeChapters(testManga.id).first().size)
    }

    @Test
    fun registeringMangaAtomicallySeedsChaptersAndDuplicateSeriesKeepsReadingState() = runBlocking {
        val firstRegistration = repository.registerManga(
            manga = testManga,
            initialChapters = listOf(testChapter("chapter-1")),
            detectedAtEpochMillis = 10L,
        )

        assertEquals(MangaRegistrationResult.Added(testManga.id, 1), firstRegistration)
        val firstChapter = repository.observeChapters(testManga.id).first().single()
        repository.setChapterReadState(
            chapterId = firstChapter.id,
            action = ReadingAction.READ,
            origin = ReadingActionOrigin.DETAIL_SCREEN,
            occurredAtEpochMillis = 15L,
        )

        val duplicateRegistration = repository.registerManga(
            manga = testManga.copy(
                id = "duplicate-manga",
                title = "A title that must not replace the saved one",
                entryChapterUrl = "https://example.test/chapter/2",
            ),
            initialChapters = listOf(
                testChapter("chapter-1").copy(url = "https://example.test/chapter/1-updated"),
                testChapter("chapter-2"),
            ),
            detectedAtEpochMillis = 20L,
        )

        assertEquals(MangaRegistrationResult.AlreadyTracked(testManga.id), duplicateRegistration)
        val libraryManga = repository.observeLibrary().first().single()
        assertEquals("Test manga", libraryManga.title)
        assertEquals("Chapter 2", libraryManga.latestChapterLabel)
        assertEquals(20L, libraryManga.latestChapterAtEpochMillis)
        assertEquals(1, libraryManga.unreadChapterCount)
        assertTrue(libraryManga.monitoringEnabled)
        val chapters = repository.observeChapters(testManga.id).first()
        assertEquals(2, chapters.size)
        assertTrue(chapters.single { it.sourceChapterKey == "chapter-1" }.isRead)
        assertFalse(chapters.single { it.sourceChapterKey == "chapter-2" }.isRead)
    }

    @Test
    fun registeringFromAnIncompleteSiteCanSeedItsKnownCurrentChapter() = runBlocking {
        val result = repository.registerManga(
            manga = testManga,
            initialChapters = listOf(testChapter("chapter-426")),
            detectedAtEpochMillis = 10L,
        )

        assertEquals(MangaRegistrationResult.Added(testManga.id, 1), result)
        val chapter = repository.observeChapters(testManga.id).first().single()
        assertEquals("chapter-426", chapter.sourceChapterKey)
        assertFalse(chapter.isRead)
        assertTrue(repository.observeLibrary().first().single().monitoringEnabled)
    }

    @Test
    fun registeringWithUnsupportedAutomaticUpdatesKeepsMonitoringPaused() = runBlocking {
        val result = repository.registerManga(
            manga = testManga,
            initialChapters = listOf(testChapter("chapter-185")),
            detectedAtEpochMillis = 10L,
            monitoringEnabled = false,
        )

        assertEquals(MangaRegistrationResult.Added(testManga.id, 1), result)
        assertFalse(repository.observeLibrary().first().single().monitoringEnabled)
        assertFalse(repository.observeMonitoringSettings(testManga.id).first()?.isEnabled == true)
        assertEquals(1, repository.observeChapters(testManga.id).first().size)
    }

    private fun openDatabase(): MangaTrackerDatabase =
        Room.databaseBuilder(
            context,
            MangaTrackerDatabase::class.java,
            databaseName,
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

    private fun newRepository() =
        RoomMangaRepository(database) {
            nextId += 1
            "new-chapter-$nextId"
        }

    private fun repositoryAfterMigration() = RoomMangaRepository(database)

    private fun createVersionOneDatabase() {
        val file = context.getDatabasePath(databaseName)
        file.parentFile?.mkdirs()
        val legacy = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            legacy.execSQL(
                """
                CREATE TABLE manga (
                    id TEXT NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL,
                    originalCoverUrl TEXT,
                    seriesUrl TEXT,
                    entryChapterUrl TEXT NOT NULL,
                    normalizedDomain TEXT NOT NULL,
                    createdAtEpochMillis INTEGER NOT NULL,
                    updatedAtEpochMillis INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            legacy.execSQL(
                "CREATE UNIQUE INDEX index_manga_normalizedDomain_seriesUrl " +
                    "ON manga (normalizedDomain, seriesUrl)",
            )
            legacy.execSQL(
                """
                CREATE TABLE chapter (
                    id TEXT NOT NULL PRIMARY KEY,
                    mangaId TEXT NOT NULL,
                    sourceChapterKey TEXT NOT NULL,
                    label TEXT NOT NULL,
                    chapterKind TEXT NOT NULL,
                    normalizedNumber TEXT,
                    url TEXT NOT NULL,
                    publishedAtEpochMillis INTEGER,
                    firstDetectedAtEpochMillis INTEGER NOT NULL,
                    notificationPostedAtEpochMillis INTEGER,
                    FOREIGN KEY(mangaId) REFERENCES manga(id) ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            legacy.execSQL(
                "CREATE UNIQUE INDEX index_chapter_mangaId_sourceChapterKey " +
                    "ON chapter (mangaId, sourceChapterKey)",
            )
            legacy.execSQL(
                """
                CREATE TABLE reading_state (
                    chapterId TEXT NOT NULL PRIMARY KEY,
                    isRead INTEGER NOT NULL,
                    changedAtEpochMillis INTEGER NOT NULL,
                    FOREIGN KEY(chapterId) REFERENCES chapter(id) ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            legacy.execSQL(
                "CREATE INDEX index_reading_state_chapterId ON reading_state (chapterId)",
            )
            legacy.execSQL(
                """
                CREATE TABLE reading_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    chapterId TEXT NOT NULL,
                    action TEXT NOT NULL,
                    origin TEXT NOT NULL,
                    occurredAtEpochMillis INTEGER NOT NULL,
                    FOREIGN KEY(chapterId) REFERENCES chapter(id) ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            legacy.execSQL(
                "CREATE INDEX index_reading_history_chapterId_occurredAtEpochMillis " +
                    "ON reading_history (chapterId, occurredAtEpochMillis)",
            )
            legacy.execSQL(
                """
                CREATE TABLE domain_parser_config (
                    normalizedHost TEXT NOT NULL PRIMARY KEY,
                    parserId TEXT NOT NULL,
                    formatVersion INTEGER NOT NULL,
                    configJson TEXT NOT NULL,
                    updatedAtEpochMillis INTEGER NOT NULL,
                    validatedAtEpochMillis INTEGER
                )
                """.trimIndent(),
            )
            legacy.execSQL(
                """
                CREATE TABLE monitoring_state (
                    mangaId TEXT NOT NULL PRIMARY KEY,
                    isEnabled INTEGER NOT NULL,
                    lastAttemptAtEpochMillis INTEGER,
                    lastSuccessAtEpochMillis INTEGER,
                    lastOutcome TEXT,
                    FOREIGN KEY(mangaId) REFERENCES manga(id) ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            legacy.execSQL(
                """
                CREATE TABLE check_attempt (
                    id TEXT NOT NULL PRIMARY KEY,
                    mangaId TEXT NOT NULL,
                    trigger TEXT NOT NULL,
                    startedAtEpochMillis INTEGER NOT NULL,
                    finishedAtEpochMillis INTEGER,
                    outcome TEXT NOT NULL,
                    errorCode TEXT,
                    FOREIGN KEY(mangaId) REFERENCES manga(id) ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            legacy.execSQL(
                "CREATE INDEX index_check_attempt_mangaId_startedAtEpochMillis " +
                    "ON check_attempt (mangaId, startedAtEpochMillis)",
            )
            legacy.execSQL(
                """
                INSERT INTO manga VALUES (
                    'legacy-manga', 'Legacy manga', NULL, 'https://example.test/series',
                    'https://example.test/chapter/1', 'example.test', 1, 2
                )
                """.trimIndent(),
            )
            legacy.execSQL(
                """
                INSERT INTO chapter VALUES (
                    'legacy-chapter', 'legacy-manga', 'chapter-1', 'Chapter 1',
                    'NUMBERED', '1', 'https://example.test/chapter/1', NULL, 3, NULL
                )
                """.trimIndent(),
            )
            legacy.execSQL(
                "INSERT INTO reading_state VALUES ('legacy-chapter', 1, 4)",
            )
            legacy.execSQL(
                "INSERT INTO reading_history VALUES (1, 'legacy-chapter', 'READ', 'DETAIL_SCREEN', 4)",
            )
            legacy.execSQL(
                """
                INSERT INTO domain_parser_config VALUES (
                    'example.test', 'manual-css', 2, '{"chapter":"a.chapter"}', 5, 4
                )
                """.trimIndent(),
            )
            legacy.execSQL(
                "INSERT INTO monitoring_state VALUES ('legacy-manga', 1, 6, 5, 'SUCCESS')",
            )
            legacy.execSQL(
                "INSERT INTO check_attempt VALUES ('legacy-attempt', 'legacy-manga', 'MANUAL', 6, 7, 'SUCCESS', NULL)",
            )
            legacy.execSQL(
                "CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)",
            )
            legacy.execSQL(
                """
                INSERT INTO room_master_table (id, identity_hash)
                VALUES (42, '3e7953fe75e6966b9e871c0bbd4172ff')
                """.trimIndent(),
            )
            legacy.version = 1
        } finally {
            legacy.close()
        }
    }

    private fun testChapter(sourceChapterKey: String) =
        DiscoveredChapter(
            sourceChapterKey = sourceChapterKey,
            label = "Chapter ${sourceChapterKey.substringAfterLast('-')}",
            chapterKind = "NUMBERED",
            normalizedNumber = sourceChapterKey.substringAfterLast('-'),
            url = "https://example.test/${sourceChapterKey.substringAfterLast('-')}",
            publishedAtEpochMillis = null,
        )

    private fun completeSnapshot(vararg chapters: DiscoveredChapter) =
        ValidatedChapterSnapshot.fromParserSuccess(
            chapters = chapters.toList(),
            isComplete = true,
        )

    private val testManga =
        Manga(
            id = "manga-1",
            title = "Test manga",
            originalCoverUrl = null,
            seriesUrl = "https://example.test/series",
            entryChapterUrl = "https://example.test/chapter/1",
            normalizedDomain = "example.test",
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 2L,
        )
}

private class FileDatabaseContext(
    base: Context,
    private val databaseDirectory: File,
) : ContextWrapper(base) {
    override fun getApplicationContext(): Context = this

    override fun getDatabasePath(name: String): File = File(databaseDirectory, name)

    override fun openOrCreateDatabase(
        name: String,
        mode: Int,
        factory: SQLiteDatabase.CursorFactory?,
    ): SQLiteDatabase {
        val file = getDatabasePath(name)
        file.parentFile?.mkdirs()
        return SQLiteDatabase.openOrCreateDatabase(file, factory)
    }

    override fun openOrCreateDatabase(
        name: String,
        mode: Int,
        factory: SQLiteDatabase.CursorFactory?,
        errorHandler: DatabaseErrorHandler?,
    ): SQLiteDatabase {
        val file = getDatabasePath(name)
        file.parentFile?.mkdirs()
        return SQLiteDatabase.openDatabase(
            file.absolutePath,
            factory,
            SQLiteDatabase.CREATE_IF_NECESSARY,
            errorHandler,
        )
    }
}
