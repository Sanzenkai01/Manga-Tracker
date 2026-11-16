package com.sanzenkai.mangatracker.core.data.local.entity

data class MangaLibraryRow(
    val id: String,
    val title: String,
    val originalCoverUrl: String?,
    val latestChapterLabel: String?,
    val latestChapterAtEpochMillis: Long?,
    val unreadChapterCount: Int,
    val newUnreadChapterCount: Int,
    val monitoringEnabled: Boolean,
    val lastAttemptAtEpochMillis: Long?,
    val lastSuccessAtEpochMillis: Long?,
    val lastOutcome: String?,
)
