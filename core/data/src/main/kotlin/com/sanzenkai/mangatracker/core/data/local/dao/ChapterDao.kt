package com.sanzenkai.mangatracker.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.sanzenkai.mangatracker.core.data.local.entity.ChapterEntity
import com.sanzenkai.mangatracker.core.data.local.entity.ChapterWithReadingState
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {
    @Query(
        """
        SELECT chapter.*, COALESCE(reading_state.isRead, 0) AS isRead
        FROM chapter
        LEFT JOIN reading_state ON reading_state.chapterId = chapter.id
        ORDER BY chapter.mangaId,
                 chapter.firstDetectedAtEpochMillis DESC,
                 chapter.id ASC
        """,
    )
    suspend fun getAllForBackup(): List<ChapterWithReadingState>

    @Query(
        """
        SELECT chapter.*, COALESCE(reading_state.isRead, 0) AS isRead
        FROM chapter
        LEFT JOIN reading_state ON reading_state.chapterId = chapter.id
        WHERE chapter.mangaId = :mangaId
        ORDER BY chapter.firstDetectedAtEpochMillis DESC,
                 CASE WHEN chapter.chapterKind = 'NUMBERED'
                      THEN CAST(chapter.normalizedNumber AS REAL) END DESC,
                 chapter.id ASC
        """,
    )
    fun observeForManga(mangaId: String): Flow<List<ChapterWithReadingState>>

    @Query(
        """
        SELECT COUNT(*)
        FROM chapter
        LEFT JOIN reading_state ON reading_state.chapterId = chapter.id
        WHERE chapter.mangaId = :mangaId AND COALESCE(reading_state.isRead, 0) = 0
        """,
    )
    fun observeUnreadCount(mangaId: String): Flow<Int>

    @Query("SELECT * FROM chapter WHERE id = :chapterId")
    suspend fun getById(chapterId: String): ChapterEntity?

    @Query(
        """
        SELECT chapter.*, COALESCE(reading_state.isRead, 0) AS isRead
        FROM chapter
        INNER JOIN pending_chapter_notification
            ON pending_chapter_notification.chapterId = chapter.id
        LEFT JOIN reading_state ON reading_state.chapterId = chapter.id
        WHERE chapter.mangaId = :mangaId
            AND chapter.notificationPostedAtEpochMillis IS NULL
            AND COALESCE(reading_state.isRead, 0) = 0
        ORDER BY chapter.firstDetectedAtEpochMillis ASC, chapter.id ASC
        """,
    )
    suspend fun getPendingNotificationChapters(mangaId: String): List<ChapterWithReadingState>

    @Query("SELECT id FROM chapter WHERE mangaId IN (:mangaIds)")
    suspend fun getIdsForMangaIds(mangaIds: List<String>): List<String>

    @Query("DELETE FROM chapter WHERE mangaId IN (:mangaIds)")
    suspend fun deleteForMangaIds(mangaIds: List<String>): Int

    @Query(
        """
        UPDATE chapter
        SET notificationPostedAtEpochMillis = :postedAtEpochMillis
        WHERE id = :chapterId AND notificationPostedAtEpochMillis IS NULL
        """,
    )
    suspend fun markNotificationPosted(chapterId: String, postedAtEpochMillis: Long): Int

    @Query(
        """
        SELECT * FROM chapter
        WHERE mangaId = :mangaId AND sourceChapterKey = :sourceChapterKey
        LIMIT 1
        """,
    )
    suspend fun getBySourceKey(mangaId: String, sourceChapterKey: String): ChapterEntity?

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(chapter: ChapterEntity): Long

    @Query(
        """
        UPDATE chapter
        SET label = :label,
            chapterKind = :chapterKind,
            normalizedNumber = :normalizedNumber,
            url = :url,
            publishedAtEpochMillis = COALESCE(:publishedAtEpochMillis, publishedAtEpochMillis)
        WHERE id = :chapterId
        """,
    )
    suspend fun updateMetadata(
        chapterId: String,
        label: String,
        chapterKind: String,
        normalizedNumber: String?,
        url: String,
        publishedAtEpochMillis: Long?,
    )
}
