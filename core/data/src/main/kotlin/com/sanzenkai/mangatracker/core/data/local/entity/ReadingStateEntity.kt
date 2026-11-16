package com.sanzenkai.mangatracker.core.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reading_state",
    foreignKeys = [
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("chapterId")],
)
data class ReadingStateEntity(
    @PrimaryKey val chapterId: String,
    val isRead: Boolean,
    val changedAtEpochMillis: Long,
)
