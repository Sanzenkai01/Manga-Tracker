package com.sanzenkai.mangatracker.core.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "check_attempt",
    foreignKeys = [
        ForeignKey(
            entity = MangaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mangaId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["mangaId", "startedAtEpochMillis"])],
)
data class CheckAttemptEntity(
    @PrimaryKey val id: String,
    val mangaId: String,
    val trigger: String,
    val startedAtEpochMillis: Long,
    val finishedAtEpochMillis: Long?,
    val outcome: String,
    val errorCode: String?,
)
