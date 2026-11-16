package com.sanzenkai.mangatracker.core.domain.model

data class Manga(
    val id: String,
    val title: String,
    val originalCoverUrl: String?,
    val seriesUrl: String?,
    val entryChapterUrl: String,
    val normalizedDomain: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
