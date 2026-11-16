package com.sanzenkai.mangatracker.core.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "manga",
    indices = [Index(value = ["normalizedDomain", "seriesUrl"], unique = true)],
)
data class MangaEntity(
    @PrimaryKey val id: String,
    val title: String,
    val originalCoverUrl: String?,
    val seriesUrl: String?,
    val entryChapterUrl: String,
    val normalizedDomain: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
