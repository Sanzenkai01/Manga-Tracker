package com.sanzenkai.mangatracker.ui.discovery

import com.sanzenkai.mangatracker.core.domain.model.DiscoveredChapter
import com.sanzenkai.mangatracker.core.domain.model.normalizeChapterNumber
import com.sanzenkai.mangatracker.core.parser.api.ParsedChapter

private val specialChapterPattern = Regex("(?i)\\b(special|one-shot|oneshot)\\b")
private val extraChapterPattern = Regex("(?i)\\b(extra|bonus|side[-_ ]?story)\\b")

internal fun ParsedChapter.toDiscoveredChapter(): DiscoveredChapter {
    val chapterKind = when {
        specialChapterPattern.containsMatchIn(label) -> "SPECIAL"
        extraChapterPattern.containsMatchIn(label) -> "EXTRA"
        else -> "NUMBERED"
    }
    val normalizedNumber = if (chapterKind == "NUMBERED") {
        normalizeChapterNumber(label)
    } else {
        null
    }
    return DiscoveredChapter(
        sourceChapterKey = sourceKey,
        label = label,
        chapterKind = chapterKind,
        normalizedNumber = normalizedNumber,
        url = url,
        publishedAtEpochMillis = publishedAtEpochMillis,
    )
}
