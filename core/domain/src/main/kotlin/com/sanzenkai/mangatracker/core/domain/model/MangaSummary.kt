package com.sanzenkai.mangatracker.core.domain.model

data class MangaSummary(
    val id: String,
    val title: String,
    val originalCoverUrl: String?,
    val latestChapterLabel: String? = null,
    val latestChapterAtEpochMillis: Long? = null,
    val unreadChapterCount: Int = 0,
    val newUnreadChapterCount: Int = 0,
    val monitoringEnabled: Boolean = false,
    val lastAttemptAtEpochMillis: Long? = null,
    val lastSuccessAtEpochMillis: Long? = null,
    val lastOutcome: String? = null,
)
