package com.sanzenkai.mangatracker.core.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chapter",
    foreignKeys = [
        ForeignKey(
            entity = MangaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mangaId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["mangaId", "sourceChapterKey"], unique = true),
        Index(value = ["mangaId", "firstDetectedAtEpochMillis"]),
    ],
)
data class ChapterEntity(
    @PrimaryKey val id: String,
    val mangaId: String,
    val sourceChapterKey: String,
    val label: String,
    val chapterKind: String,
    val normalizedNumber: String?,
    val url: String,
    val publishedAtEpochMillis: Long?,
    val firstDetectedAtEpochMillis: Long,
    val notificationPostedAtEpochMillis: Long?,
)
