package com.sanzenkai.mangatracker.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.sanzenkai.mangatracker.core.data.local.entity.SiteConfigurationEntity

@Dao
interface SiteConfigurationDao {
    @Query("SELECT * FROM site_configuration ORDER BY normalizedHost ASC")
    suspend fun getAllForBackup(): List<SiteConfigurationEntity>

    @Query("SELECT * FROM site_configuration WHERE normalizedHost = :host")
    suspend fun getByHost(host: String): SiteConfigurationEntity?

    @Upsert
    suspend fun upsert(configuration: SiteConfigurationEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(configuration: SiteConfigurationEntity): Long
}
