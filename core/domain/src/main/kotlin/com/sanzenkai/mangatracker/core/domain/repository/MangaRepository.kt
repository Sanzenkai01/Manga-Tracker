package com.sanzenkai.mangatracker.core.domain.repository

import com.sanzenkai.mangatracker.core.domain.model.CheckAttempt
import com.sanzenkai.mangatracker.core.domain.model.ChapterSyncResult
import com.sanzenkai.mangatracker.core.domain.model.DiscoveredChapter
import com.sanzenkai.mangatracker.core.domain.model.Manga
import com.sanzenkai.mangatracker.core.domain.model.MangaMetadataUpdate
import com.sanzenkai.mangatracker.core.domain.model.MangaBackupSnapshot
import com.sanzenkai.mangatracker.core.domain.model.MangaRegistrationResult
import com.sanzenkai.mangatracker.core.domain.model.MangaSummary
import com.sanzenkai.mangatracker.core.domain.model.MonitoringProblemSignals
import com.sanzenkai.mangatracker.core.domain.model.MonitoringSettings
import com.sanzenkai.mangatracker.core.domain.model.ReadingAction
import com.sanzenkai.mangatracker.core.domain.model.ReadingActionOrigin
import com.sanzenkai.mangatracker.core.domain.model.ReadingHistoryEntry
import com.sanzenkai.mangatracker.core.domain.model.SiteConfiguration
import com.sanzenkai.mangatracker.core.domain.model.TrackedChapter
import com.sanzenkai.mangatracker.core.domain.model.ValidatedChapterSnapshot
import kotlinx.coroutines.flow.Flow

interface MangaRepository {
    suspend fun createBackupSnapshot(exportedAtEpochMillis: Long): MangaBackupSnapshot

    suspend fun restoreBackup(snapshot: MangaBackupSnapshot)

    fun observeLibrary(): Flow<List<MangaSummary>>

    fun observeManga(mangaId: String): Flow<Manga?>

    suspend fun saveManga(manga: Manga)

    suspend fun fillMissingMangaMetadata(
        mangaId: String,
        metadata: MangaMetadataUpdate,
        updatedAtEpochMillis: Long,
    ): Boolean

    suspend fun registerManga(
        manga: Manga,
        initialChapters: List<DiscoveredChapter>,
        detectedAtEpochMillis: Long,
        monitoringEnabled: Boolean = true,
    ): MangaRegistrationResult

    fun observeChapters(mangaId: String): Flow<List<TrackedChapter>>

    fun observeUnreadCount(mangaId: String): Flow<Int>

    suspend fun saveFoundChapters(
        mangaId: String,
        snapshot: ValidatedChapterSnapshot,
        detectedAtEpochMillis: Long,
    ): ChapterSyncResult

    suspend fun recordSuccessfulCheck(
        mangaId: String,
        snapshot: ValidatedChapterSnapshot,
        attempt: CheckAttempt,
    ): ChapterSyncResult

    suspend fun setChapterReadState(
        chapterId: String,
        action: ReadingAction,
        origin: ReadingActionOrigin,
        occurredAtEpochMillis: Long,
    ): Boolean

    suspend fun setMangaReadState(
        mangaIds: Set<String>,
        action: ReadingAction,
        origin: ReadingActionOrigin,
        occurredAtEpochMillis: Long,
    ): Int

    suspend fun removeMangas(mangaIds: Set<String>): Int

    suspend fun markChapterNotificationPosted(
        chapterId: String,
        postedAtEpochMillis: Long,
    ): Boolean

    suspend fun getPendingChapterNotifications(mangaId: String): List<TrackedChapter>

    fun observeReadingHistory(chapterId: String): Flow<List<ReadingHistoryEntry>>

    suspend fun saveSiteConfiguration(configuration: SiteConfiguration)

    suspend fun getSiteConfiguration(normalizedHost: String): SiteConfiguration?

    fun observeMonitoringSettings(mangaId: String): Flow<MonitoringSettings?>

    suspend fun saveMonitoringSettings(settings: MonitoringSettings)

    suspend fun setMonitoringEnabled(mangaId: String, isEnabled: Boolean)

    fun observeCheckAttempts(mangaId: String): Flow<List<CheckAttempt>>

    fun observeMonitoringProblem(mangaId: String): Flow<MonitoringProblemSignals>

    suspend fun recordCheckAttempt(attempt: CheckAttempt)
}
