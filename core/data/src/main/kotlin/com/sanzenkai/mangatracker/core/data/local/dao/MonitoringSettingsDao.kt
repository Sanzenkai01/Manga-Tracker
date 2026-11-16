package com.sanzenkai.mangatracker.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.sanzenkai.mangatracker.core.data.local.entity.MonitoringSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MonitoringSettingsDao {
    @Query("SELECT * FROM monitoring_settings ORDER BY mangaId ASC")
    suspend fun getAllForBackup(): List<MonitoringSettingsEntity>

    @Query("SELECT * FROM monitoring_settings WHERE mangaId = :mangaId")
    fun observe(mangaId: String): Flow<MonitoringSettingsEntity?>

    @Query("SELECT * FROM monitoring_settings WHERE mangaId = :mangaId")
    suspend fun getByMangaId(mangaId: String): MonitoringSettingsEntity?

    @Query("DELETE FROM monitoring_settings WHERE mangaId IN (:mangaIds)")
    suspend fun deleteForMangaIds(mangaIds: List<String>): Int

    @Upsert
    suspend fun upsert(settings: MonitoringSettingsEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(settings: MonitoringSettingsEntity): Long
}
