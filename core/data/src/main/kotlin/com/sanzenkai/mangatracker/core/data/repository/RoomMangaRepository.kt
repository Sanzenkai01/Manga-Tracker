package com.sanzenkai.mangatracker.core.data.repository

import androidx.room.withTransaction
import com.sanzenkai.mangatracker.core.data.local.MangaTrackerDatabase
import com.sanzenkai.mangatracker.core.data.local.entity.ChapterEntity
import com.sanzenkai.mangatracker.core.data.local.entity.CheckAttemptEntity
import com.sanzenkai.mangatracker.core.data.local.entity.MangaEntity
import com.sanzenkai.mangatracker.core.data.local.entity.MonitoringSettingsEntity
import com.sanzenkai.mangatracker.core.data.local.entity.PendingChapterNotificationEntity
import com.sanzenkai.mangatracker.core.data.local.entity.ReadingHistoryEntity
import com.sanzenkai.mangatracker.core.data.local.entity.ReadingStateEntity
import com.sanzenkai.mangatracker.core.data.local.entity.SiteConfigurationEntity
import com.sanzenkai.mangatracker.core.domain.model.CheckAttempt
import com.sanzenkai.mangatracker.core.domain.model.ChapterSyncResult
import com.sanzenkai.mangatracker.core.domain.model.DiscoveredChapter
import com.sanzenkai.mangatracker.core.domain.model.Manga
import com.sanzenkai.mangatracker.core.domain.model.MangaMetadataUpdate
import com.sanzenkai.mangatracker.core.domain.model.MangaBackupEntry
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
import com.sanzenkai.mangatracker.core.domain.repository.MangaRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class RoomMangaRepository(
    private val database: MangaTrackerDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) : MangaRepository {
    private val mangaDao = database.mangaDao()
    private val chapterDao = database.chapterDao()
    private val readingStateDao = database.readingStateDao()
    private val readingHistoryDao = database.readingHistoryDao()
    private val siteConfigurationDao = database.siteConfigurationDao()
    private val monitoringSettingsDao = database.monitoringSettingsDao()
    private val checkAttemptDao = database.checkAttemptDao()
    private val pendingChapterNotificationDao = database.pendingChapterNotificationDao()

    override suspend fun createBackupSnapshot(
        exportedAtEpochMillis: Long,
    ): MangaBackupSnapshot = database.withTransaction {
        val mangas = mangaDao.getAllForBackup().map { it.toDomain() }
        val chapters = chapterDao.getAllForBackup().map { row ->
            row.chapter.toDomain(isRead = row.isRead)
        }
        val mangaIdByChapterId = chapters.associate { it.id to it.mangaId }
        val historyByMangaId = readingHistoryDao.getAllForBackup()
            .map { entry ->
                val mangaId = checkNotNull(mangaIdByChapterId[entry.chapterId]) {
                    "Reading history refers to an unknown chapter '${entry.chapterId}'."
                }
                mangaId to ReadingHistoryEntry(
                    id = entry.id,
                    chapterId = entry.chapterId,
                    action = ReadingAction.valueOf(entry.action),
                    origin = entry.origin,
                    occurredAtEpochMillis = entry.occurredAtEpochMillis,
                )
            }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
        val chaptersByMangaId = chapters.groupBy(TrackedChapter::mangaId)
        val monitoringByMangaId = monitoringSettingsDao.getAllForBackup()
            .associate { it.mangaId to it.toDomain() }
        val attemptsByMangaId = checkAttemptDao.getAllForBackup()
            .map { it.toDomain() }
            .groupBy(CheckAttempt::mangaId)

        MangaBackupSnapshot(
            exportedAtEpochMillis = exportedAtEpochMillis,
            mangas = mangas.map { manga ->
                MangaBackupEntry(
                    manga = manga,
                    chapters = chaptersByMangaId[manga.id].orEmpty(),
                    readingHistory = historyByMangaId[manga.id].orEmpty(),
                    monitoringSettings = monitoringByMangaId[manga.id],
                    checkAttempts = attemptsByMangaId[manga.id].orEmpty(),
                )
            },
            siteConfigurations = siteConfigurationDao.getAllForBackup().map { it.toDomain() },
        )
    }

    override suspend fun restoreBackup(snapshot: MangaBackupSnapshot) {
        validateBackupSnapshot(snapshot)
        database.withTransaction {
            val targetMangaIds = snapshot.mangas.associate { entry ->
                entry.manga.id to resolveBackupMangaId(entry.manga)
            }
            val targetChapterIds = mutableMapOf<String, String>()

            snapshot.mangas.forEach { entry ->
                val targetMangaId = checkNotNull(targetMangaIds[entry.manga.id])
                entry.chapters.forEach { backupChapter ->
                    val existing = chapterDao.getBySourceKey(
                        mangaId = targetMangaId,
                        sourceChapterKey = backupChapter.sourceChapterKey,
                    )
                    val persistedChapter = if (existing != null) {
                        existing
                    } else {
                        val chapterId = if (chapterDao.getById(backupChapter.id) == null) {
                            backupChapter.id
                        } else {
                            idGenerator()
                        }
                        check(chapterDao.getById(chapterId) == null) {
                            "A backup chapter ID conflicts with an existing chapter."
                        }
                        val importedChapter = backupChapter.copy(
                            id = chapterId,
                            mangaId = targetMangaId,
                        )
                        check(chapterDao.insertIfMissing(importedChapter.toEntity()) != -1L) {
                            "A backup chapter could not be merged."
                        }
                        chapterDao.getBySourceKey(
                            mangaId = targetMangaId,
                            sourceChapterKey = backupChapter.sourceChapterKey,
                        ) ?: error("A restored chapter was not persisted.")
                    }
                    targetChapterIds[backupChapter.id] = persistedChapter.id

                    if (existing == null) {
                        val changedAt = entry.readingHistory
                            .asSequence()
                            .filter { it.chapterId == backupChapter.id }
                            .maxOfOrNull { it.occurredAtEpochMillis }
                            ?: backupChapter.firstDetectedAtEpochMillis
                        readingStateDao.insertIfMissing(
                            ReadingStateEntity(
                                chapterId = persistedChapter.id,
                                isRead = backupChapter.isRead,
                                changedAtEpochMillis = changedAt,
                            ),
                        )
                    }
                }
            }

            val existingHistory = readingHistoryDao.getAllForBackup()
                .map(ReadingHistoryEntity::fingerprint)
                .toMutableSet()
            val historiesToInsert = snapshot.mangas.flatMap { entry ->
                entry.readingHistory.mapNotNull { history ->
                    val targetChapterId = checkNotNull(targetChapterIds[history.chapterId])
                    val imported = ReadingHistoryEntity(
                        chapterId = targetChapterId,
                        action = history.action.name,
                        origin = history.origin,
                        occurredAtEpochMillis = history.occurredAtEpochMillis,
                    )
                    imported.takeIf { existingHistory.add(it.fingerprint()) }
                }
            }
            if (historiesToInsert.isNotEmpty()) {
                readingHistoryDao.insertAllIfMissing(historiesToInsert)
            }

            val existingAttemptRows = checkAttemptDao.getAllForBackup()
            val existingAttemptFingerprints = existingAttemptRows
                .map(CheckAttemptEntity::fingerprint)
                .toMutableSet()
            val usedAttemptIds = existingAttemptRows.associate { it.id to it.fingerprint() }
                .toMutableMap()
            val attemptsToInsert = mutableListOf<CheckAttemptEntity>()
            snapshot.mangas.forEach { entry ->
                val targetMangaId = checkNotNull(targetMangaIds[entry.manga.id])
                entry.checkAttempts.forEach attemptLoop@{ attempt ->
                    val imported = attempt.copy(mangaId = targetMangaId)
                    val fingerprint = imported.fingerprint()
                    if (!existingAttemptFingerprints.add(fingerprint)) return@attemptLoop

                    val importedId = if (imported.id in usedAttemptIds) {
                        idGenerator().also { generatedId ->
                            check(generatedId !in usedAttemptIds) {
                                "A backup check-attempt ID conflicts with an existing attempt."
                            }
                        }
                    } else {
                        imported.id
                    }
                    usedAttemptIds[importedId] = fingerprint
                    attemptsToInsert += imported.copy(id = importedId).toEntity()
                }
            }
            if (attemptsToInsert.isNotEmpty()) {
                checkAttemptDao.insertAllIfMissing(attemptsToInsert)
            }

            snapshot.mangas.forEach { entry ->
                val targetMangaId = checkNotNull(targetMangaIds[entry.manga.id])
                entry.monitoringSettings?.let { settings ->
                    monitoringSettingsDao.insertIfMissing(
                        settings.copy(mangaId = targetMangaId).toEntity(),
                    )
                }
            }
            snapshot.siteConfigurations.forEach { configuration ->
                siteConfigurationDao.insertIfMissing(configuration.toEntity())
            }
        }
    }

    override fun observeLibrary(): Flow<List<MangaSummary>> =
        combine(
            mangaDao.observeLibraryRows(),
            checkAttemptDao.observeMonitoringProblemSignals(),
        ) { rows, problemSignals ->
            val signalsByMangaId = problemSignals.associateBy { it.mangaId }
            val nowEpochMillis = clock()
            rows.map { row ->
                MangaSummary(
                    id = row.id,
                    title = row.title,
                    originalCoverUrl = row.originalCoverUrl,
                    latestChapterLabel = row.latestChapterLabel,
                    latestChapterAtEpochMillis = row.latestChapterAtEpochMillis,
                    unreadChapterCount = row.unreadChapterCount,
                    newUnreadChapterCount = row.newUnreadChapterCount,
                    monitoringEnabled = row.monitoringEnabled,
                    lastAttemptAtEpochMillis = row.lastAttemptAtEpochMillis,
                    lastSuccessAtEpochMillis = row.lastSuccessAtEpochMillis,
                    lastOutcome = row.lastOutcome,
                    isMonitoringProblem = row.monitoringEnabled &&
                        signalsByMangaId[row.id]?.isProblem(nowEpochMillis) == true,
                )
            }
        }

    override fun observeManga(mangaId: String): Flow<Manga?> =
        mangaDao.observeById(mangaId).map { it?.toDomain() }

    override suspend fun saveManga(manga: Manga) {
        mangaDao.upsert(manga.toEntity())
    }

    override suspend fun fillMissingMangaMetadata(
        mangaId: String,
        metadata: MangaMetadataUpdate,
        updatedAtEpochMillis: Long,
    ): Boolean =
        mangaDao.fillMissingMetadata(
            mangaId = mangaId,
            title = metadata.title,
            originalCoverUrl = metadata.originalCoverUrl,
            seriesUrl = metadata.seriesUrl,
            updatedAtEpochMillis = updatedAtEpochMillis,
        ) > 0

    override suspend fun registerManga(
        manga: Manga,
        initialChapters: List<DiscoveredChapter>,
        detectedAtEpochMillis: Long,
        monitoringEnabled: Boolean,
    ): MangaRegistrationResult {
        require(initialChapters.isNotEmpty()) {
            "At least one known chapter is required to register a manga."
        }
        return database.withTransaction {
            val existing = findExistingManga(manga)
            if (existing != null) {
                saveDiscoveredChaptersInternal(existing.id, initialChapters, detectedAtEpochMillis)
                return@withTransaction MangaRegistrationResult.AlreadyTracked(existing.id)
            }

            val insertRowId = mangaDao.insertIfMissing(manga.toEntity())
            if (insertRowId == -1L) {
                val duplicate = findExistingManga(manga)
                    ?: error("Manga insert conflicted without a matching existing record.")
                saveDiscoveredChaptersInternal(duplicate.id, initialChapters, detectedAtEpochMillis)
                return@withTransaction MangaRegistrationResult.AlreadyTracked(duplicate.id)
            }

            monitoringSettingsDao.upsert(
                MonitoringSettingsEntity(
                    mangaId = manga.id,
                    isEnabled = monitoringEnabled,
                    lastAttemptAtEpochMillis = null,
                    lastSuccessAtEpochMillis = null,
                    lastOutcome = null,
                ),
            )
            val syncResult =
                saveDiscoveredChaptersInternal(manga.id, initialChapters, detectedAtEpochMillis)
            MangaRegistrationResult.Added(
                mangaId = manga.id,
                chapterCount = syncResult.newChapterIds.size,
            )
        }
    }

    override fun observeChapters(mangaId: String): Flow<List<TrackedChapter>> =
        chapterDao.observeForManga(mangaId).map { chapters ->
            chapters.map { row ->
                row.chapter.toDomain(isRead = row.isRead)
            }
        }

    override fun observeUnreadCount(mangaId: String): Flow<Int> =
        chapterDao.observeUnreadCount(mangaId)

    override suspend fun saveFoundChapters(
        mangaId: String,
        snapshot: ValidatedChapterSnapshot,
        detectedAtEpochMillis: Long,
    ): ChapterSyncResult =
        database.withTransaction {
            saveDiscoveredChaptersInternal(mangaId, snapshot.chapters, detectedAtEpochMillis)
        }

    override suspend fun recordSuccessfulCheck(
        mangaId: String,
        snapshot: ValidatedChapterSnapshot,
        attempt: CheckAttempt,
    ): ChapterSyncResult {
        require(attempt.mangaId == mangaId) {
            "The check attempt must belong to the manga being updated."
        }
        require(attempt.outcome == CHECK_OUTCOME_SUCCESS) {
            "Only successful checks can persist chapter snapshots."
        }
        val detectedAt = attempt.finishedAtEpochMillis ?: attempt.startedAtEpochMillis
        return database.withTransaction {
            val syncResult = saveDiscoveredChaptersInternal(
                mangaId = mangaId,
                chapters = snapshot.chapters,
                detectedAtEpochMillis = detectedAt,
                queueNotifications = true,
            )
            recordCheckAttemptInternal(attempt)
            syncResult
        }
    }

    override suspend fun setChapterReadState(
        chapterId: String,
        action: ReadingAction,
        origin: ReadingActionOrigin,
        occurredAtEpochMillis: Long,
    ): Boolean =
        database.withTransaction {
            setChapterReadStateInternal(
                chapterId = chapterId,
                action = action,
                origin = origin,
                occurredAtEpochMillis = occurredAtEpochMillis,
            )
        }

    override suspend fun setMangaReadState(
        mangaIds: Set<String>,
        action: ReadingAction,
        origin: ReadingActionOrigin,
        occurredAtEpochMillis: Long,
    ): Int {
        val ids = mangaIds.filter(String::isNotBlank).distinct()
        if (ids.isEmpty()) return 0

        return database.withTransaction {
            val chapterIds = chapterDao.getIdsForMangaIds(ids)
            if (chapterIds.isEmpty()) return@withTransaction 0

            val isRead = action == ReadingAction.READ
            val currentStates = readingStateDao.getForChapters(chapterIds)
                .associateBy(ReadingStateEntity::chapterId)
            val changedChapterIds = chapterIds.filter { chapterId ->
                (currentStates[chapterId]?.isRead ?: false) != isRead
            }
            if (changedChapterIds.isEmpty()) return@withTransaction 0

            readingStateDao.upsertAll(
                changedChapterIds.map { chapterId ->
                    ReadingStateEntity(
                        chapterId = chapterId,
                        isRead = isRead,
                        changedAtEpochMillis = occurredAtEpochMillis,
                    )
                },
            )
            readingHistoryDao.insertAll(
                changedChapterIds.map { chapterId ->
                    ReadingHistoryEntity(
                        chapterId = chapterId,
                        action = action.name,
                        origin = origin.name,
                        occurredAtEpochMillis = occurredAtEpochMillis,
                    )
                },
            )
            if (isRead) pendingChapterNotificationDao.deleteForChapterIds(changedChapterIds)
            changedChapterIds.size
        }
    }

    override suspend fun removeMangas(mangaIds: Set<String>): Int {
        val ids = mangaIds.filter(String::isNotBlank).distinct()
        if (ids.isEmpty()) return 0

        return database.withTransaction {
            readingHistoryDao.deleteForMangaIds(ids)
            readingStateDao.deleteForMangaIds(ids)
            chapterDao.deleteForMangaIds(ids)
            checkAttemptDao.deleteForMangaIds(ids)
            monitoringSettingsDao.deleteForMangaIds(ids)
            mangaDao.deleteByIds(ids)
        }
    }

    override fun observeReadingHistory(chapterId: String): Flow<List<ReadingHistoryEntry>> =
        readingHistoryDao.observeForChapter(chapterId).map { entries ->
            entries.map { entry ->
                ReadingHistoryEntry(
                    id = entry.id,
                    chapterId = entry.chapterId,
                    action = ReadingAction.valueOf(entry.action),
                    origin = entry.origin,
                    occurredAtEpochMillis = entry.occurredAtEpochMillis,
                )
            }
        }

    override suspend fun markChapterNotificationPosted(
        chapterId: String,
        postedAtEpochMillis: Long,
    ): Boolean = database.withTransaction {
        val marked = chapterDao.markNotificationPosted(chapterId, postedAtEpochMillis) > 0
        pendingChapterNotificationDao.deleteForChapterId(chapterId)
        marked
    }

    override suspend fun getPendingChapterNotifications(mangaId: String): List<TrackedChapter> =
        chapterDao.getPendingNotificationChapters(mangaId).map { row ->
            row.chapter.toDomain(isRead = row.isRead)
        }

    override suspend fun saveSiteConfiguration(configuration: SiteConfiguration) {
        siteConfigurationDao.upsert(configuration.toEntity())
    }

    override suspend fun getSiteConfiguration(normalizedHost: String): SiteConfiguration? =
        siteConfigurationDao.getByHost(normalizedHost)?.toDomain()

    override fun observeMonitoringSettings(mangaId: String): Flow<MonitoringSettings?> =
        monitoringSettingsDao.observe(mangaId).map { it?.toDomain() }

    override suspend fun saveMonitoringSettings(settings: MonitoringSettings) {
        check(mangaDao.getById(settings.mangaId) != null) {
            "Cannot save monitoring settings for unknown manga '${settings.mangaId}'."
        }
        monitoringSettingsDao.upsert(settings.toEntity())
    }

    override suspend fun setMonitoringEnabled(mangaId: String, isEnabled: Boolean) {
        database.withTransaction {
            check(mangaDao.getById(mangaId) != null) {
                "Cannot change monitoring settings for unknown manga '$mangaId'."
            }
            val current = monitoringSettingsDao.getByMangaId(mangaId)
            monitoringSettingsDao.upsert(
                current?.copy(isEnabled = isEnabled)
                    ?: MonitoringSettingsEntity(
                        mangaId = mangaId,
                        isEnabled = isEnabled,
                        lastAttemptAtEpochMillis = null,
                        lastSuccessAtEpochMillis = null,
                        lastOutcome = null,
                    ),
            )
        }
    }

    override fun observeCheckAttempts(mangaId: String): Flow<List<CheckAttempt>> =
        checkAttemptDao.observeForManga(mangaId).map { attempts ->
            attempts.map { attempt -> attempt.toDomain() }
        }

    override fun observeMonitoringProblem(mangaId: String): Flow<MonitoringProblemSignals> =
        checkAttemptDao.observeMonitoringProblemSignals(mangaId).map { signals ->
            signals.firstOrNull() ?: MonitoringProblemSignals.empty(mangaId)
        }

    override suspend fun recordCheckAttempt(attempt: CheckAttempt) {
        database.withTransaction {
            check(mangaDao.getById(attempt.mangaId) != null) {
                "Cannot record a check attempt for unknown manga '${attempt.mangaId}'."
            }
            recordCheckAttemptInternal(attempt)
        }
    }

    private suspend fun recordCheckAttemptInternal(attempt: CheckAttempt) {
        checkAttemptDao.insert(attempt.toEntity())
        val current = monitoringSettingsDao.getByMangaId(attempt.mangaId)
        val attemptAt = attempt.finishedAtEpochMillis ?: attempt.startedAtEpochMillis
        val lastAttemptAt = current?.lastAttemptAtEpochMillis
            ?.let { maxOf(it, attemptAt) }
            ?: attemptAt
        val lastSuccessAt = if (attempt.outcome == CHECK_OUTCOME_SUCCESS) {
            current?.lastSuccessAtEpochMillis?.let { maxOf(it, attemptAt) } ?: attemptAt
        } else {
            current?.lastSuccessAtEpochMillis
        }
        monitoringSettingsDao.upsert(
            if (current != null) {
                current.copy(
                    lastAttemptAtEpochMillis = lastAttemptAt,
                    lastSuccessAtEpochMillis = lastSuccessAt,
                    lastOutcome = attempt.outcome,
                )
            } else {
                MonitoringSettingsEntity(
                    mangaId = attempt.mangaId,
                    isEnabled = false,
                    lastAttemptAtEpochMillis = lastAttemptAt,
                    lastSuccessAtEpochMillis = lastSuccessAt,
                    lastOutcome = attempt.outcome,
                )
            },
        )
    }

    private suspend fun saveDiscoveredChaptersInternal(
        mangaId: String,
        chapters: List<DiscoveredChapter>,
        detectedAtEpochMillis: Long,
        queueNotifications: Boolean = false,
    ): ChapterSyncResult {
        check(mangaDao.getById(mangaId) != null) {
            "Cannot save chapters for unknown manga '$mangaId'."
        }

        val newChapterIds = mutableListOf<String>()
        chapters.forEach { discovered ->
            val existing = chapterDao.getBySourceKey(mangaId, discovered.sourceChapterKey)
            if (existing != null) {
                chapterDao.updateMetadata(
                    chapterId = existing.id,
                    label = discovered.label,
                    chapterKind = discovered.chapterKind,
                    normalizedNumber = discovered.normalizedNumber,
                    url = discovered.url,
                    publishedAtEpochMillis = discovered.publishedAtEpochMillis,
                )
            } else {
                val newChapter = discovered.toEntity(
                    id = idGenerator(),
                    mangaId = mangaId,
                    firstDetectedAtEpochMillis = detectedAtEpochMillis,
                )
                val insertedRowId = chapterDao.insertIfMissing(newChapter)
                val persisted =
                    chapterDao.getBySourceKey(mangaId, discovered.sourceChapterKey)
                        ?: error("Chapter insert did not produce a persisted row.")

                if (insertedRowId == -1L) {
                    chapterDao.updateMetadata(
                        chapterId = persisted.id,
                        label = discovered.label,
                        chapterKind = discovered.chapterKind,
                        normalizedNumber = discovered.normalizedNumber,
                        url = discovered.url,
                        publishedAtEpochMillis = discovered.publishedAtEpochMillis,
                    )
                } else {
                    readingStateDao.insertIfMissing(
                        ReadingStateEntity(
                            chapterId = persisted.id,
                            isRead = false,
                            changedAtEpochMillis = detectedAtEpochMillis,
                        ),
                    )
                    if (queueNotifications) {
                        pendingChapterNotificationDao.insertIfMissing(
                            PendingChapterNotificationEntity(persisted.id),
                        )
                    }
                    newChapterIds += persisted.id
                }
            }
        }
        return ChapterSyncResult(newChapterIds)
    }

    private suspend fun setChapterReadStateInternal(
        chapterId: String,
        action: ReadingAction,
        origin: ReadingActionOrigin,
        occurredAtEpochMillis: Long,
    ): Boolean {
        check(chapterDao.getById(chapterId) != null) {
            "Cannot change reading state for unknown chapter '$chapterId'."
        }

        val isRead = action == ReadingAction.READ
        val currentState = readingStateDao.get(chapterId)
        if ((currentState?.isRead ?: false) == isRead) return false

        readingStateDao.upsert(
            ReadingStateEntity(
                chapterId = chapterId,
                isRead = isRead,
                changedAtEpochMillis = occurredAtEpochMillis,
            ),
        )
        readingHistoryDao.insert(
            ReadingHistoryEntity(
                chapterId = chapterId,
                action = action.name,
                origin = origin.name,
                occurredAtEpochMillis = occurredAtEpochMillis,
            ),
        )
        if (isRead) pendingChapterNotificationDao.deleteForChapterId(chapterId)
        return true
    }

    private suspend fun findExistingManga(manga: Manga): MangaEntity? =
        manga.seriesUrl?.let { mangaDao.findBySeries(manga.normalizedDomain, it) }
            ?: mangaDao.findByEntryChapter(manga.normalizedDomain, manga.entryChapterUrl)

    private suspend fun resolveBackupMangaId(manga: Manga): String {
        findExistingManga(manga)?.let { return it.id }

        val existingById = mangaDao.getById(manga.id)
        if (existingById != null) {
            require(existingById.matchesBackupIdentity(manga)) {
                "A backup manga ID conflicts with an unrelated saved manga."
            }
            return existingById.id
        }

        if (mangaDao.insertIfMissing(manga.toEntity()) != -1L) return manga.id

        val conflict = mangaDao.getById(manga.id) ?: findExistingManga(manga)
            ?: error("A backup manga could not be merged.")
        require(conflict.matchesBackupIdentity(manga)) {
            "A backup manga ID conflicts with an unrelated saved manga."
        }
        return conflict.id
    }

    private fun validateBackupSnapshot(snapshot: MangaBackupSnapshot) {
        require(snapshot.formatVersion == MangaBackupSnapshot.CURRENT_FORMAT_VERSION) {
            "The backup format version is not supported."
        }
        val mangaIds = snapshot.mangas.map { it.manga.id }
        require(mangaIds.all(String::isNotBlank) && mangaIds.distinct().size == mangaIds.size) {
            "The backup contains invalid or duplicate manga IDs."
        }
        val chapterIds = snapshot.mangas.flatMap { entry -> entry.chapters.map { it.id } }
        require(chapterIds.all(String::isNotBlank) && chapterIds.distinct().size == chapterIds.size) {
            "The backup contains invalid or duplicate chapter IDs."
        }
        snapshot.mangas.forEach { entry ->
            require(entry.chapters.all { it.mangaId == entry.manga.id }) {
                "The backup contains a chapter assigned to the wrong manga."
            }
            require(entry.chapters.map { it.sourceChapterKey }.distinct().size == entry.chapters.size) {
                "The backup contains duplicate chapter keys."
            }
            val chapterIdsForManga = entry.chapters.mapTo(mutableSetOf()) { it.id }
            require(entry.readingHistory.all { it.chapterId in chapterIdsForManga }) {
                "The backup contains reading history for an unknown chapter."
            }
            val monitoringSettings = entry.monitoringSettings
            require(monitoringSettings == null || monitoringSettings.mangaId == entry.manga.id) {
                "The backup contains monitoring settings for the wrong manga."
            }
            require(entry.checkAttempts.all { it.mangaId == entry.manga.id }) {
                "The backup contains a check attempt for the wrong manga."
            }
        }
        val configurationHosts = snapshot.siteConfigurations.map { it.normalizedHost }
        require(configurationHosts.distinct().size == configurationHosts.size) {
            "The backup contains duplicate site configurations."
        }
    }
}

private fun Manga.toEntity() =
    MangaEntity(
        id = id,
        title = title,
        originalCoverUrl = originalCoverUrl,
        seriesUrl = seriesUrl,
        entryChapterUrl = entryChapterUrl,
        normalizedDomain = normalizedDomain,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

private fun MangaEntity.toDomain() =
    Manga(
        id = id,
        title = title,
        originalCoverUrl = originalCoverUrl,
        seriesUrl = seriesUrl,
        entryChapterUrl = entryChapterUrl,
        normalizedDomain = normalizedDomain,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

private fun DiscoveredChapter.toEntity(
    id: String,
    mangaId: String,
    firstDetectedAtEpochMillis: Long,
) = ChapterEntity(
    id = id,
    mangaId = mangaId,
    sourceChapterKey = sourceChapterKey,
    label = label,
    chapterKind = chapterKind,
    normalizedNumber = normalizedNumber,
    url = url,
    publishedAtEpochMillis = publishedAtEpochMillis,
    firstDetectedAtEpochMillis = firstDetectedAtEpochMillis,
    notificationPostedAtEpochMillis = null,
)

private fun ChapterEntity.toDomain(isRead: Boolean) =
    TrackedChapter(
        id = id,
        mangaId = mangaId,
        sourceChapterKey = sourceChapterKey,
        label = label,
        chapterKind = chapterKind,
        normalizedNumber = normalizedNumber,
        url = url,
        publishedAtEpochMillis = publishedAtEpochMillis,
        firstDetectedAtEpochMillis = firstDetectedAtEpochMillis,
        notificationPostedAtEpochMillis = notificationPostedAtEpochMillis,
        isRead = isRead,
    )

private fun TrackedChapter.toEntity() =
    ChapterEntity(
        id = id,
        mangaId = mangaId,
        sourceChapterKey = sourceChapterKey,
        label = label,
        chapterKind = chapterKind,
        normalizedNumber = normalizedNumber,
        url = url,
        publishedAtEpochMillis = publishedAtEpochMillis,
        firstDetectedAtEpochMillis = firstDetectedAtEpochMillis,
        notificationPostedAtEpochMillis = notificationPostedAtEpochMillis,
    )

private fun SiteConfiguration.toEntity() =
    SiteConfigurationEntity(
        normalizedHost = normalizedHost,
        parserId = parserId,
        formatVersion = formatVersion,
        configJson = configJson,
        updatedAtEpochMillis = updatedAtEpochMillis,
        validatedAtEpochMillis = validatedAtEpochMillis,
    )

private fun SiteConfigurationEntity.toDomain() =
    SiteConfiguration(
        normalizedHost = normalizedHost,
        parserId = parserId,
        formatVersion = formatVersion,
        configJson = configJson,
        updatedAtEpochMillis = updatedAtEpochMillis,
        validatedAtEpochMillis = validatedAtEpochMillis,
    )

private fun MonitoringSettings.toEntity() =
    MonitoringSettingsEntity(
        mangaId = mangaId,
        isEnabled = isEnabled,
        lastAttemptAtEpochMillis = lastAttemptAtEpochMillis,
        lastSuccessAtEpochMillis = lastSuccessAtEpochMillis,
        lastOutcome = lastOutcome,
    )

private fun MonitoringSettingsEntity.toDomain() =
    MonitoringSettings(
        mangaId = mangaId,
        isEnabled = isEnabled,
        lastAttemptAtEpochMillis = lastAttemptAtEpochMillis,
        lastSuccessAtEpochMillis = lastSuccessAtEpochMillis,
        lastOutcome = lastOutcome,
    )

private fun CheckAttempt.toEntity() =
    CheckAttemptEntity(
        id = id,
        mangaId = mangaId,
        trigger = trigger,
        startedAtEpochMillis = startedAtEpochMillis,
        finishedAtEpochMillis = finishedAtEpochMillis,
        outcome = outcome,
        errorCode = errorCode,
    )

private fun CheckAttemptEntity.toDomain() =
    CheckAttempt(
        id = id,
        mangaId = mangaId,
        trigger = trigger,
        startedAtEpochMillis = startedAtEpochMillis,
        finishedAtEpochMillis = finishedAtEpochMillis,
        outcome = outcome,
        errorCode = errorCode,
    )

private fun ReadingHistoryEntity.fingerprint() =
    ReadingHistoryFingerprint(chapterId, action, origin, occurredAtEpochMillis)

private fun CheckAttempt.fingerprint() =
    CheckAttemptFingerprint(
        mangaId = mangaId,
        trigger = trigger,
        startedAtEpochMillis = startedAtEpochMillis,
        finishedAtEpochMillis = finishedAtEpochMillis,
        outcome = outcome,
        errorCode = errorCode,
    )

private fun CheckAttemptEntity.fingerprint() =
    CheckAttemptFingerprint(
        mangaId = mangaId,
        trigger = trigger,
        startedAtEpochMillis = startedAtEpochMillis,
        finishedAtEpochMillis = finishedAtEpochMillis,
        outcome = outcome,
        errorCode = errorCode,
    )

private fun MangaEntity.matchesBackupIdentity(manga: Manga): Boolean =
    normalizedDomain == manga.normalizedDomain &&
        if (seriesUrl != null && manga.seriesUrl != null) {
            seriesUrl == manga.seriesUrl
        } else {
            entryChapterUrl == manga.entryChapterUrl
        }

private data class ReadingHistoryFingerprint(
    val chapterId: String,
    val action: String,
    val origin: String,
    val occurredAtEpochMillis: Long,
)

private data class CheckAttemptFingerprint(
    val mangaId: String,
    val trigger: String,
    val startedAtEpochMillis: Long,
    val finishedAtEpochMillis: Long?,
    val outcome: String,
    val errorCode: String?,
)

private const val CHECK_OUTCOME_SUCCESS = "SUCCESS"
