package com.sanzenkai.mangatracker.core.domain.model

enum class ReadingAction {
    READ,
    UNREAD,
}

enum class ReadingActionOrigin {
    DETAIL_SCREEN,
    LIBRARY_BULK_ACTION,
    NOTIFICATION_ACTION,
}

data class ReadingHistoryEntry(
    val id: Long,
    val chapterId: String,
    val action: ReadingAction,
    val origin: String,
    val occurredAtEpochMillis: Long,
)
