package com.sanzenkai.mangatracker.notifications

import com.sanzenkai.mangatracker.core.domain.model.ReadingAction
import com.sanzenkai.mangatracker.core.domain.model.ReadingActionOrigin

class ChapterNotificationActionHandler(
    private val updateChapterReadState: suspend (
        chapterId: String,
        action: ReadingAction,
        origin: ReadingActionOrigin,
        occurredAtEpochMillis: Long,
    ) -> Boolean,
    private val notificationPublisher: ChapterNotificationPublisher,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun markChapterAsRead(chapterId: String): Boolean =
        markChaptersAsRead(listOf(chapterId))

    suspend fun markChaptersAsRead(chapterIds: List<String>): Boolean {
        val validChapterIds = chapterIds
            .filter { it.isNotBlank() }
            .distinct()
        if (validChapterIds.isEmpty()) return false

        var anyChanged = false
        validChapterIds.forEach { chapterId ->
            val changed = updateChapterReadState(
                chapterId,
                ReadingAction.READ,
                ReadingActionOrigin.NOTIFICATION_ACTION,
                clock(),
            )
            anyChanged = changed || anyChanged
            notificationPublisher.cancelChapter(chapterId)
        }
        return anyChanged
    }
}
