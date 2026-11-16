package com.sanzenkai.mangatracker.core.domain.model

data class DiscoveredChapter(
    val sourceChapterKey: String,
    val label: String,
    val chapterKind: String,
    val normalizedNumber: String?,
    val url: String,
    val publishedAtEpochMillis: Long?,
)

data class TrackedChapter(
    val id: String,
    val mangaId: String,
    val sourceChapterKey: String,
    val label: String,
    val chapterKind: String,
    val normalizedNumber: String?,
    val url: String,
    val publishedAtEpochMillis: Long?,
    val firstDetectedAtEpochMillis: Long,
    val notificationPostedAtEpochMillis: Long?,
    val isRead: Boolean,
)

data class ChapterSyncResult(
    val newChapterIds: List<String>,
)
