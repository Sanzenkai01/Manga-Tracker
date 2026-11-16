package com.sanzenkai.mangatracker.core.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "pending_chapter_notification",
    primaryKeys = ["chapterId"],
    foreignKeys = [
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PendingChapterNotificationEntity(
    val chapterId: String,
)
