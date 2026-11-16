package com.sanzenkai.mangatracker.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanzenkai.mangatracker.core.data.local.entity.CheckAttemptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckAttemptDao {
    @Query(
        """
        SELECT * FROM check_attempt
        ORDER BY mangaId, startedAtEpochMillis ASC, id ASC
        """,
    )
    suspend fun getAllForBackup(): List<CheckAttemptEntity>

    @Query(
        """
        SELECT * FROM check_attempt
        WHERE mangaId = :mangaId
        ORDER BY startedAtEpochMillis DESC
        """,
    )
    fun observeForManga(mangaId: String): Flow<List<CheckAttemptEntity>>

    @Query("DELETE FROM check_attempt WHERE mangaId IN (:mangaIds)")
    suspend fun deleteForMangaIds(mangaIds: List<String>): Int

    @Insert
    suspend fun insert(attempt: CheckAttemptEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIfMissing(attempts: List<CheckAttemptEntity>)
}
