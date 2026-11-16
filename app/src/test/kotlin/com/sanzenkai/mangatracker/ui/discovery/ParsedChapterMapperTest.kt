package com.sanzenkai.mangatracker.ui.discovery

import com.sanzenkai.mangatracker.core.parser.api.ParsedChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParsedChapterMapperTest {
    @Test
    fun prioritizesChapterMarkersAndRejectsLetterSuffixes() {
        val cases = listOf(
            "Solo Leveling 2 - Chapter 45" to "45",
            "Chapter 5v2" to "5",
            "Capítulo 100 END" to "100",
        )

        cases.forEach { (label, expected) ->
            val chapter = ParsedChapter(
                sourceKey = label,
                label = label,
                url = "https://example.test/chapter",
                publishedAtEpochMillis = null,
            ).toDiscoveredChapter()

            assertEquals(expected, chapter.normalizedNumber)
        }
    }

    @Test
    fun preservesDecimalNumberAndClassifiesSpecialAndExtraLabels() {
        val decimal = ParsedChapter(
            sourceKey = "decimal",
            label = "Chapter 12.5",
            url = "https://example.test/12-5",
            publishedAtEpochMillis = null,
        ).toDiscoveredChapter()
        val special = ParsedChapter(
            sourceKey = "special",
            label = "Chapter Special 4",
            url = "https://example.test/special",
            publishedAtEpochMillis = null,
        ).toDiscoveredChapter()
        val extra = ParsedChapter(
            sourceKey = "extra",
            label = "Extra 1",
            url = "https://example.test/extra",
            publishedAtEpochMillis = null,
        ).toDiscoveredChapter()

        assertEquals("NUMBERED", decimal.chapterKind)
        assertEquals("12.5", decimal.normalizedNumber)
        assertEquals("SPECIAL", special.chapterKind)
        assertNull(special.normalizedNumber)
        assertEquals("EXTRA", extra.chapterKind)
        assertNull(extra.normalizedNumber)
    }
}
