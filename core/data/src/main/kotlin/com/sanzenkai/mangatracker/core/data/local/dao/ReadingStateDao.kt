package com.sanzenkai.mangatracker.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.sanzenkai.mangatracker.core.data.local.entity.ReadingStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingStateDao {
    @Query("SELECT * FROM reading_state WHERE chapterId = :chapterId")
    fun observe(chapterId: String): Flow<ReadingStateEntity?>

    @Query("SELECT * FROM reading_state WHERE chapterId = :chapterId")
    suspend fun get(chapterId: String): ReadingStateEntity?

    @Query("SELECT * FROM reading_state WHERE chapterId IN (:chapterIds)")
    suspend fun getForChapters(chapterIds: List<String>): List<ReadingStateEntity>

    @Query(
        """
        DELETE FROM reading_state
        WHERE chapterId IN (
            SELECT id FROM chapter WHERE mangaId IN (:mangaIds)
        )
        """,
    )
    suspend fun deleteForMangaIds(mangaIds: List<String>): Int

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(state: ReadingStateEntity): Long

    @Upsert
    suspend fun upsert(state: ReadingStateEntity)

    @Upsert
    suspend fun upsertAll(states: List<ReadingStateEntity>)
}
