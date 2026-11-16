package com.sanzenkai.mangatracker.ui

import com.sanzenkai.mangatracker.core.domain.model.TrackedChapter

internal enum class ReadingStatus {
    READ,
    NEW,
    UNREAD,
}

internal fun TrackedChapter.readingStatusAt(
    lastSuccessfulCheckAtEpochMillis: Long?,
): ReadingStatus =
    when {
        isRead -> ReadingStatus.READ
        lastSuccessfulCheckAtEpochMillis != null &&
            firstDetectedAtEpochMillis == lastSuccessfulCheckAtEpochMillis -> ReadingStatus.NEW
        else -> ReadingStatus.UNREAD
    }
