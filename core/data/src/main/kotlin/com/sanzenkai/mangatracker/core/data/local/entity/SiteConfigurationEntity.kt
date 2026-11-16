package com.sanzenkai.mangatracker.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "site_configuration")
data class SiteConfigurationEntity(
    @PrimaryKey val normalizedHost: String,
    val parserId: String,
    val formatVersion: Int,
    val configJson: String,
    val updatedAtEpochMillis: Long,
    val validatedAtEpochMillis: Long?,
)
