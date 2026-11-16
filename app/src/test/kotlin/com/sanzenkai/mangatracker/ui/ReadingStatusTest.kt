package com.sanzenkai.mangatracker.ui

import com.sanzenkai.mangatracker.core.domain.model.TrackedChapter
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingStatusTest {
    @Test
    fun readStateTakesPrecedenceOverRecentDetection() {
        assertEquals(
            ReadingStatus.READ,
            testChapter(isRead = true).readingStatusAt(lastSuccessfulCheckAtEpochMillis = 100L),
        )
    }

    @Test
    fun chapterDetectedByLatestSuccessfulCheckIsNew() {
        assertEquals(
            ReadingStatus.NEW,
            testChapter(detectedAt = 100L).readingStatusAt(lastSuccessfulCheckAtEpochMillis = 100L),
        )
    }

    @Test
    fun unreadChapterIsNotNewWhenThereIsNoMatchingSuccessfulCheck() {
        assertEquals(
            ReadingStatus.UNREAD,
            testChapter(detectedAt = 100L).readingStatusAt(lastSuccessfulCheckAtEpochMillis = 200L),
        )
        assertEquals(
            ReadingStatus.UNREAD,
            testChapter(detectedAt = 100L).readingStatusAt(lastSuccessfulCheckAtEpochMillis = null),
        )
    }

    private fun testChapter(
        detectedAt: Long = 50L,
        isRead: Boolean = false,
    ) = TrackedChapter(
        id = "chapter-1",
        mangaId = "manga-1",
        sourceChapterKey = "chapter-1",
        label = "Chapter 1",
        chapterKind = "NUMBERED",
        normalizedNumber = "1",
        url = "https://example.test/chapter-1",
        publishedAtEpochMillis = null,
        firstDetectedAtEpochMillis = detectedAt,
        notificationPostedAtEpochMillis = null,
        isRead = isRead,
    )
}
