package com.sanzenkai.mangatracker.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanzenkai.mangatracker.core.data.local.entity.PendingChapterNotificationEntity

@Dao
interface PendingChapterNotificationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(notification: PendingChapterNotificationEntity)

    @Query("DELETE FROM pending_chapter_notification WHERE chapterId = :chapterId")
    suspend fun deleteForChapterId(chapterId: String)

    @Query("DELETE FROM pending_chapter_notification WHERE chapterId IN (:chapterIds)")
    suspend fun deleteForChapterIds(chapterIds: List<String>)
}
