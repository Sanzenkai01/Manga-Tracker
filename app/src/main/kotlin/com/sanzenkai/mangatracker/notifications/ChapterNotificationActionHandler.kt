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
    suspend fun markChapterAsRead(chapterId: String): Boolean {
        val changed = updateChapterReadState(
            chapterId,
            ReadingAction.READ,
            ReadingActionOrigin.NOTIFICATION_ACTION,
            clock(),
        )
        notificationPublisher.cancelChapter(chapterId)
        return changed
    }
}
