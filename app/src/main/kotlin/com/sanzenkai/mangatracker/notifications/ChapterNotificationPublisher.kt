package com.sanzenkai.mangatracker.notifications

import com.sanzenkai.mangatracker.core.domain.model.Manga
import com.sanzenkai.mangatracker.core.domain.model.TrackedChapter

interface ChapterNotificationPublisher {
    suspend fun publishNewChapters(
        manga: Manga,
        chapters: List<TrackedChapter>,
    ): Set<String>

    fun ensureChannel()

    fun cancelChapter(chapterId: String)

    fun cancelMangaNotifications(mangaIds: Set<String>)
}

object NoOpChapterNotificationPublisher : ChapterNotificationPublisher {
    override suspend fun publishNewChapters(
        manga: Manga,
        chapters: List<TrackedChapter>,
    ): Set<String> = emptySet()

    override fun ensureChannel() = Unit

    override fun cancelChapter(chapterId: String) = Unit

    override fun cancelMangaNotifications(mangaIds: Set<String>) = Unit
}

fun interface CoverBitmapLoader {
    suspend fun load(url: String): android.graphics.Bitmap?
}
