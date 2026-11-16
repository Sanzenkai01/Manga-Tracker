package com.sanzenkai.mangatracker.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterTrackingRulesTest {
    @Test
    fun normalizesChapterNumbersFromMarkersBeforeUsingTheLastIsolatedNumber() {
        val cases = listOf(
            "Solo Leveling 2 - Chapter 45" to "45",
            "Chapter 5v2" to "5",
            "Chapter 3.5" to "3.5",
            "Capítulo 3,5" to "3.5",
            "Cap. 8" to "8",
            "Ep. 9" to "9",
            "Capítulo 100 END" to "100",
            "Solo Leveling 2 - 45" to "45",
        )

        cases.forEach { (label, expected) ->
            assertEquals(label, expected, normalizeChapterNumber(label))
        }
        assertNull(normalizeChapterNumber("No chapter number"))
    }

    @Test
    fun usesTheSavedChapterKeyAsTheBaselineInAShiftedWindow() {
        val observed = listOf(
            discovered("chapter-428", "Chapter 428", "428"),
            discovered("chapter-427", "Chapter 427", "427"),
            discovered("chapter-426", "Chapter 426", "426"),
            discovered("chapter-425", "Chapter 425", "425"),
        )
        val saved = listOf(
            tracked("chapter-426", "Chapter 426", "426"),
            tracked("chapter-425", "Chapter 425", "425"),
        )

        val delta = calculateLatestChapterDelta(observed, saved)

        assertEquals(listOf("chapter-428", "chapter-427"), delta?.map { it.sourceChapterKey })
    }

    @Test
    fun numericFallbackHandlesChangedKeysAndIgnoresUnnumberedExtras() {
        val observed = listOf(
            discovered("site-chapter-4", "Solo Leveling 2 - Chapter 4", normalizeChapterNumber("Solo Leveling 2 - Chapter 4")),
            discovered("site-extra", "Extra 3", normalizedNumber = null, chapterKind = "EXTRA"),
            discovered("site-chapter-3-5", "Chapter 3,5", normalizeChapterNumber("Chapter 3,5")),
            discovered("site-chapter-3", "Chapter 3", "3"),
        )
        val saved = listOf(tracked("old-site-key", "Chapter 3.5", "3.5"))

        val delta = calculateLatestChapterDelta(observed, saved)

        assertEquals(listOf("site-chapter-4"), delta?.map { it.sourceChapterKey })
    }

    @Test
    fun sourceKeyComparisonRetainsUnnumberedChaptersBeforeTheSavedBaseline() {
        val observed = listOf(
            discovered("chapter-11", "Chapter 11", "11"),
            discovered("extra-before-baseline", "Extra chapter", normalizedNumber = null, chapterKind = "EXTRA"),
            discovered("saved-special", "Special", normalizedNumber = null, chapterKind = "SPECIAL"),
            discovered("chapter-9", "Chapter 9", "9"),
        )
        val saved = listOf(tracked("saved-special", "Special", normalizedNumber = null))

        val delta = calculateLatestChapterDelta(observed, saved)

        assertEquals(
            listOf("chapter-11", "extra-before-baseline"),
            delta?.map { it.sourceChapterKey },
        )
    }

    @Test
    fun anUnnumberedWindowDoesNotFailWhenANumericBaselineExists() {
        val observed = listOf(
            discovered("new-extra", "Extra chapter", normalizedNumber = null, chapterKind = "EXTRA"),
        )
        val saved = listOf(tracked("saved-chapter", "Chapter 3", "3"))

        val delta = calculateLatestChapterDelta(observed, saved)

        assertEquals(emptyList<String>(), delta?.map { it.sourceChapterKey })
    }

    @Test
    fun returnsNullWhenNoMatchingKeyOrSavedNumberCanEstablishABaseline() {
        val observed = listOf(discovered("new-extra", "Extra chapter", normalizedNumber = null, chapterKind = "EXTRA"))
        val saved = listOf(tracked("old-extra", "Extra chapter", normalizedNumber = null))

        assertNull(calculateLatestChapterDelta(observed, saved))
    }

    private fun discovered(
        key: String,
        label: String,
        normalizedNumber: String? = normalizeChapterNumber(label),
        chapterKind: String = "NUMBERED",
    ) = DiscoveredChapter(
        sourceChapterKey = key,
        label = label,
        chapterKind = chapterKind,
        normalizedNumber = normalizedNumber,
        url = "https://example.test/$key",
        publishedAtEpochMillis = null,
    )

    private fun tracked(
        key: String,
        label: String,
        normalizedNumber: String?,
    ) = TrackedChapter(
        id = "id-$key",
        mangaId = "manga",
        sourceChapterKey = key,
        label = label,
        chapterKind = "NUMBERED",
        normalizedNumber = normalizedNumber,
        url = "https://example.test/$key",
        publishedAtEpochMillis = null,
        firstDetectedAtEpochMillis = 0L,
        notificationPostedAtEpochMillis = null,
        isRead = false,
    )
}
