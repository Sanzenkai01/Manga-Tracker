package com.sanzenkai.mangatracker.core.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reading_history",
    foreignKeys = [
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["chapterId", "occurredAtEpochMillis"])],
)
data class ReadingHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chapterId: String,
    val action: String,
    val origin: String,
    val occurredAtEpochMillis: Long,
)
