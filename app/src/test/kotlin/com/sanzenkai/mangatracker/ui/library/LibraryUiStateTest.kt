package com.sanzenkai.mangatracker.ui.library

import com.sanzenkai.mangatracker.core.domain.model.MangaSummary
import com.sanzenkai.mangatracker.ui.ReadingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryUiStateTest {
    @Test
    fun readFilterShowsOnlyMangaWithSavedChaptersAndNoUnreadChapters() {
        val read = manga("read", unread = 0)
        val unread = manga("unread", unread = 2)
        val noChapters = manga("empty", unread = 0, latestChapter = null)

        val state = LibraryUiState(
            isLoading = false,
            mangas = listOf(read, unread, noChapters),
            readFilter = LibraryReadFilter.READ,
        )

        assertEquals(listOf("read"), state.visibleMangas.map(MangaSummary::id))
    }

    @Test
    fun unreadFilterIncludesNewAndPreviouslyUnreadChapters() {
        val newlyFound = manga("new", unread = 1, newUnread = 1)
        val previouslyUnread = manga("unread", unread = 2)
        val read = manga("read", unread = 0)

        val state = LibraryUiState(
            isLoading = false,
            mangas = listOf(newlyFound, previouslyUnread, read),
            readFilter = LibraryReadFilter.UNREAD,
        )

        assertEquals(
            listOf("new", "unread"),
            state.visibleMangas.map(MangaSummary::id),
        )
    }

    @Test
    fun allFilterKeepsEveryMangaVisible() {
        val mangas = listOf(
            manga("read", unread = 0),
            manga("unread", unread = 1),
            manga("empty", unread = 0, latestChapter = null),
        )

        assertEquals(
            mangas,
            LibraryUiState(
                isLoading = false,
                mangas = mangas,
                readFilter = LibraryReadFilter.ALL,
            ).visibleMangas,
        )
    }

    @Test
    fun badgeStatusUsesNewUnreadThenUnreadThenReadPriority() {
        assertEquals(
            ReadingStatus.NEW,
            manga("new", unread = 2, newUnread = 1).readingStatus(),
        )
        assertEquals(ReadingStatus.UNREAD, manga("unread", unread = 1).readingStatus())
        assertEquals(ReadingStatus.READ, manga("read", unread = 0).readingStatus())
    }

    @Test
    fun mangaWithoutAnySavedChapterHasNoReadingBadge() {
        assertNull(manga("empty", unread = 0, latestChapter = null).readingStatus())
    }

    private fun manga(
        id: String,
        unread: Int,
        newUnread: Int = 0,
        latestChapter: String? = "Chapter 1",
    ) = MangaSummary(
        id = id,
        title = id,
        originalCoverUrl = null,
        latestChapterLabel = latestChapter,
        unreadChapterCount = unread,
        newUnreadChapterCount = newUnread,
    )
}
