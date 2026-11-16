package com.sanzenkai.mangatracker.core.domain.model

data class MangaBackupSnapshot(
    val exportedAtEpochMillis: Long,
    val mangas: List<MangaBackupEntry>,
    val siteConfigurations: List<SiteConfiguration>,
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
) {
    companion object {
        const val CURRENT_FORMAT_VERSION = 1
    }
}

data class MangaBackupEntry(
    val manga: Manga,
    val chapters: List<TrackedChapter>,
    val readingHistory: List<ReadingHistoryEntry>,
    val monitoringSettings: MonitoringSettings?,
    val checkAttempts: List<CheckAttempt>,
)
