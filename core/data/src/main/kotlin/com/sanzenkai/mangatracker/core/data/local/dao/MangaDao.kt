package com.sanzenkai.mangatracker.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.sanzenkai.mangatracker.core.data.local.entity.MangaEntity
import com.sanzenkai.mangatracker.core.data.local.entity.MangaLibraryRow
import kotlinx.coroutines.flow.Flow

@Dao
interface MangaDao {
    @Query(
        """
        SELECT
            manga.id AS id,
            manga.title AS title,
            manga.originalCoverUrl AS originalCoverUrl,
            (
                SELECT chapter.label
                FROM chapter
                WHERE chapter.mangaId = manga.id
                ORDER BY COALESCE(chapter.publishedAtEpochMillis, chapter.firstDetectedAtEpochMillis) DESC,
                         chapter.firstDetectedAtEpochMillis DESC,
                         CASE WHEN chapter.chapterKind = 'NUMBERED'
                              THEN CAST(chapter.normalizedNumber AS REAL) END DESC,
                         chapter.id ASC
                LIMIT 1
            ) AS latestChapterLabel,
            (
                SELECT COALESCE(chapter.publishedAtEpochMillis, chapter.firstDetectedAtEpochMillis)
                FROM chapter
                WHERE chapter.mangaId = manga.id
                ORDER BY COALESCE(chapter.publishedAtEpochMillis, chapter.firstDetectedAtEpochMillis) DESC,
                         chapter.firstDetectedAtEpochMillis DESC,
                         CASE WHEN chapter.chapterKind = 'NUMBERED'
                              THEN CAST(chapter.normalizedNumber AS REAL) END DESC,
                         chapter.id ASC
                LIMIT 1
            ) AS latestChapterAtEpochMillis,
            (
                SELECT COUNT(*)
                FROM chapter
                LEFT JOIN reading_state ON reading_state.chapterId = chapter.id
                WHERE chapter.mangaId = manga.id AND COALESCE(reading_state.isRead, 0) = 0
            ) AS unreadChapterCount,
            (
                SELECT COUNT(*)
                FROM chapter
                LEFT JOIN reading_state ON reading_state.chapterId = chapter.id
                WHERE chapter.mangaId = manga.id
                  AND COALESCE(reading_state.isRead, 0) = 0
                  AND chapter.firstDetectedAtEpochMillis = monitoring_settings.lastSuccessAtEpochMillis
            ) AS newUnreadChapterCount,
            COALESCE(monitoring_settings.isEnabled, 0) AS monitoringEnabled,
            monitoring_settings.lastAttemptAtEpochMillis AS lastAttemptAtEpochMillis,
            monitoring_settings.lastSuccessAtEpochMillis AS lastSuccessAtEpochMillis,
            monitoring_settings.lastOutcome AS lastOutcome
        FROM manga
        LEFT JOIN monitoring_settings ON monitoring_settings.mangaId = manga.id
        ORDER BY manga.title COLLATE NOCASE ASC
        """,
    )
    fun observeLibraryRows(): Flow<List<MangaLibraryRow>>

    @Query("SELECT * FROM manga ORDER BY title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<MangaEntity>>

    @Query("SELECT * FROM manga ORDER BY title COLLATE NOCASE ASC")
    suspend fun getAllForBackup(): List<MangaEntity>

    @Query("SELECT * FROM manga WHERE id = :mangaId")
    suspend fun getById(mangaId: String): MangaEntity?

    @Query("SELECT * FROM manga WHERE id = :mangaId")
    fun observeById(mangaId: String): Flow<MangaEntity?>

    @Query(
        """
        SELECT * FROM manga
        WHERE normalizedDomain = :normalizedDomain AND seriesUrl = :seriesUrl
        LIMIT 1
        """,
    )
    suspend fun findBySeries(normalizedDomain: String, seriesUrl: String): MangaEntity?

    @Query(
        """
        SELECT * FROM manga
        WHERE normalizedDomain = :normalizedDomain AND entryChapterUrl = :entryChapterUrl
        LIMIT 1
        """,
    )
    suspend fun findByEntryChapter(normalizedDomain: String, entryChapterUrl: String): MangaEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(manga: MangaEntity): Long

    @Upsert
    suspend fun upsert(manga: MangaEntity)

    @Query(
        """
        UPDATE manga
        SET title = COALESCE(NULLIF(TRIM(title), ''), NULLIF(TRIM(:title), ''), title),
            originalCoverUrl = COALESCE(
                NULLIF(TRIM(originalCoverUrl), ''),
                NULLIF(TRIM(:originalCoverUrl), '')
            ),
            seriesUrl = COALESCE(
                NULLIF(TRIM(seriesUrl), ''),
                NULLIF(TRIM(:seriesUrl), '')
            ),
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE id = :mangaId
          AND (
              (NULLIF(TRIM(title), '') IS NULL AND NULLIF(TRIM(:title), '') IS NOT NULL)
              OR (
                  NULLIF(TRIM(originalCoverUrl), '') IS NULL
                  AND NULLIF(TRIM(:originalCoverUrl), '') IS NOT NULL
              )
              OR (
                  NULLIF(TRIM(seriesUrl), '') IS NULL
                  AND NULLIF(TRIM(:seriesUrl), '') IS NOT NULL
              )
          )
        """,
    )
    suspend fun fillMissingMetadata(
        mangaId: String,
        title: String?,
        originalCoverUrl: String?,
        seriesUrl: String?,
        updatedAtEpochMillis: Long,
    ): Int

    @Query("DELETE FROM manga WHERE id IN (:mangaIds)")
    suspend fun deleteByIds(mangaIds: List<String>): Int
}
