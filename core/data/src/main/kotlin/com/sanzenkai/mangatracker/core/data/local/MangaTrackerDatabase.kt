package com.sanzenkai.mangatracker.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sanzenkai.mangatracker.core.data.local.dao.ChapterDao
import com.sanzenkai.mangatracker.core.data.local.dao.CheckAttemptDao
import com.sanzenkai.mangatracker.core.data.local.dao.MangaDao
import com.sanzenkai.mangatracker.core.data.local.dao.MonitoringSettingsDao
import com.sanzenkai.mangatracker.core.data.local.dao.PendingChapterNotificationDao
import com.sanzenkai.mangatracker.core.data.local.dao.ReadingHistoryDao
import com.sanzenkai.mangatracker.core.data.local.dao.ReadingStateDao
import com.sanzenkai.mangatracker.core.data.local.dao.SiteConfigurationDao
import com.sanzenkai.mangatracker.core.data.local.entity.ChapterEntity
import com.sanzenkai.mangatracker.core.data.local.entity.CheckAttemptEntity
import com.sanzenkai.mangatracker.core.data.local.entity.MangaEntity
import com.sanzenkai.mangatracker.core.data.local.entity.MonitoringSettingsEntity
import com.sanzenkai.mangatracker.core.data.local.entity.PendingChapterNotificationEntity
import com.sanzenkai.mangatracker.core.data.local.entity.ReadingHistoryEntity
import com.sanzenkai.mangatracker.core.data.local.entity.ReadingStateEntity
import com.sanzenkai.mangatracker.core.data.local.entity.SiteConfigurationEntity

@Database(
    entities = [
        MangaEntity::class,
        ChapterEntity::class,
        ReadingStateEntity::class,
        ReadingHistoryEntity::class,
        SiteConfigurationEntity::class,
        MonitoringSettingsEntity::class,
        CheckAttemptEntity::class,
        PendingChapterNotificationEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class MangaTrackerDatabase : RoomDatabase() {
    abstract fun mangaDao(): MangaDao

    abstract fun chapterDao(): ChapterDao

    abstract fun readingStateDao(): ReadingStateDao

    abstract fun readingHistoryDao(): ReadingHistoryDao

    abstract fun siteConfigurationDao(): SiteConfigurationDao

    abstract fun monitoringSettingsDao(): MonitoringSettingsDao

    abstract fun checkAttemptDao(): CheckAttemptDao

    abstract fun pendingChapterNotificationDao(): PendingChapterNotificationDao
}
