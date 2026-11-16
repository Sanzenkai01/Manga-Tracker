package com.sanzenkai.mangatracker.ui.library

import com.sanzenkai.mangatracker.core.domain.model.MangaSummary
import com.sanzenkai.mangatracker.core.domain.model.LibraryLayout
import com.sanzenkai.mangatracker.ui.ReadingStatus

enum class LibraryReadFilter {
    ALL,
    READ,
    UNREAD,
}

internal fun MangaSummary.readingStatus(): ReadingStatus? =
    when {
        newUnreadChapterCount > 0 -> ReadingStatus.NEW
        unreadChapterCount > 0 -> ReadingStatus.UNREAD
        latestChapterLabel != null -> ReadingStatus.READ
        else -> null
    }

data class LibraryUiState(
    val isLoading: Boolean = true,
    val mangas: List<MangaSummary> = emptyList(),
    val layout: LibraryLayout = LibraryLayout.LIST,
    val readFilter: LibraryReadFilter = LibraryReadFilter.ALL,
    val isRefreshing: Boolean = false,
    val refreshSummary: LibraryRefreshSummary? = null,
    val refreshError: String? = null,
    val selectedMangaIds: Set<String> = emptySet(),
    val isActionInProgress: Boolean = false,
    val actionError: String? = null,
    val error: String? = null,
) {
    val isSelectionMode: Boolean
        get() = selectedMangaIds.isNotEmpty()

    val visibleMangas: List<MangaSummary>
        get() = mangas.filter { manga ->
            when (readFilter) {
                LibraryReadFilter.ALL -> true
                LibraryReadFilter.READ ->
                    manga.latestChapterLabel != null && manga.unreadChapterCount == 0
                LibraryReadFilter.UNREAD -> manga.unreadChapterCount > 0
            }
        }
}

data class LibraryRefreshSummary(
    val updatedCount: Int,
    val failedCount: Int,
)
