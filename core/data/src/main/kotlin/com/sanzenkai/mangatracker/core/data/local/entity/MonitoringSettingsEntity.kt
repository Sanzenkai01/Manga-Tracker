package com.sanzenkai.mangatracker.core.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "monitoring_settings",
    foreignKeys = [
        ForeignKey(
            entity = MangaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mangaId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
)
data class MonitoringSettingsEntity(
    @PrimaryKey val mangaId: String,
    val isEnabled: Boolean,
    val lastAttemptAtEpochMillis: Long?,
    val lastSuccessAtEpochMillis: Long?,
    val lastOutcome: String?,
)
