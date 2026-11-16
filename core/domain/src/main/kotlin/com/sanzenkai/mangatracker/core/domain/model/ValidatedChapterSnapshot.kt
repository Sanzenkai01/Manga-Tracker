package com.sanzenkai.mangatracker.core.domain.model

class ValidatedChapterSnapshot private constructor(
    val chapters: List<DiscoveredChapter>,
) {
    companion object {
        fun fromParserSuccess(
            chapters: List<DiscoveredChapter>,
            isComplete: Boolean,
        ): ValidatedChapterSnapshot {
            require(isComplete) {
                "An incomplete chapter parse cannot be persisted as a complete snapshot."
            }
            return ValidatedChapterSnapshot(chapters.toList())
        }

        fun fromVerifiedLatestChapterWindow(
            chapters: List<DiscoveredChapter>,
            isLatestChapterKnown: Boolean,
        ): ValidatedChapterSnapshot {
            require(isLatestChapterKnown) {
                "A partial chapter update requires a verified latest chapter."
            }
            return ValidatedChapterSnapshot(chapters.toList())
        }
    }
}
