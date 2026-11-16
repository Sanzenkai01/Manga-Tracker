package com.sanzenkai.mangatracker.core.domain.model

private val chapterMarkerNumberPattern = Regex(
    """(?iu)\b(?:chapters?\b|cap[ií]tulos?\b|cap\.|episodes?\b|epis[oó]dios?\b|ep\.)\s*[:#-]?\s*([0-9]+(?:[.,][0-9]+)?)""",
)
private val isolatedNumberPattern = Regex(
    """(?<![\p{L}\p{N}])([0-9]+(?:[.,][0-9]+)?)(?![\p{L}\p{N}])""",
)

fun normalizeChapterNumber(label: String): String? {
    val markedNumber = chapterMarkerNumberPattern.findAll(label).lastOrNull()
        ?.groupValues
        ?.getOrNull(1)
    val number = markedNumber ?: isolatedNumberPattern.findAll(label)
        .lastOrNull()
        ?.groupValues
        ?.getOrNull(1)
        ?: return null
    return number.replace(',', '.')
}

fun calculateLatestChapterDelta(
    observedChapters: List<DiscoveredChapter>,
    savedChapters: List<TrackedChapter>,
): List<DiscoveredChapter>? {
    val distinctObservedChapters = observedChapters.distinctBy { it.sourceChapterKey }
    if (distinctObservedChapters.isEmpty()) return null

    val savedKeys = savedChapters.mapTo(mutableSetOf()) { it.sourceChapterKey }
    val newestSavedIndex = distinctObservedChapters.indexOfFirst { it.sourceChapterKey in savedKeys }
    if (newestSavedIndex >= 0) return distinctObservedChapters.take(newestSavedIndex)

    val latestSavedNumber = savedChapters.asSequence()
        .mapNotNull { it.normalizedNumber?.toBigDecimalOrNull() }
        .maxOrNull()
        ?: return null

    return distinctObservedChapters.filter { chapter ->
        chapter.normalizedNumber
            ?.toBigDecimalOrNull()
            ?.let { it > latestSavedNumber }
            ?: false
    }
}
