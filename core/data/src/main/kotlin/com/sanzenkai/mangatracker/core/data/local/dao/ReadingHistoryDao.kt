package com.sanzenkai.mangatracker.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanzenkai.mangatracker.core.data.local.entity.ReadingHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingHistoryDao {
    @Query(
        """
        SELECT * FROM reading_history
        ORDER BY chapterId, occurredAtEpochMillis ASC, id ASC
        """,
    )
    suspend fun getAllForBackup(): List<ReadingHistoryEntity>

    @Query(
        """
        SELECT * FROM reading_history
        WHERE chapterId = :chapterId
        ORDER BY occurredAtEpochMillis DESC, id DESC
        """,
    )
    fun observeForChapter(chapterId: String): Flow<List<ReadingHistoryEntity>>

    @Query(
        """
        DELETE FROM reading_history
        WHERE chapterId IN (
            SELECT id FROM chapter WHERE mangaId IN (:mangaIds)
        )
        """,
    )
    suspend fun deleteForMangaIds(mangaIds: List<String>): Int

    @Insert
    suspend fun insert(entry: ReadingHistoryEntity)

    @Insert
    suspend fun insertAll(entries: List<ReadingHistoryEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIfMissing(entries: List<ReadingHistoryEntity>)
}
