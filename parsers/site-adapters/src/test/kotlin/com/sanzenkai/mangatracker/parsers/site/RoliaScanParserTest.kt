package com.sanzenkai.mangatracker.parsers.site

import com.sanzenkai.mangatracker.core.parser.api.ParserDocument
import com.sanzenkai.mangatracker.core.parser.api.SiteParseResult
import com.sanzenkai.mangatracker.core.parser.engine.ParserRegistry
import com.sanzenkai.mangatracker.core.parser.engine.SiteDiscoveryEngine
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoliaScanParserTest {
    @Test
    fun parsesPublicChapterWindowAndSeriesCover() {
        val result = SiteDiscoveryEngine(
            ParserRegistry(MangaSiteParsers.default()),
        ).discover(document(apiResponse = fixture("fixtures/roliascan-chapters.json")))

        assertTrue(result is SiteParseResult.Success)
        val page = (result as SiteParseResult.Success).page
        assertEquals("Shangri-La Frontier ~ Kusoge Hunter, Kamige ni Idoman to su~", page.title)
        assertEquals(
            "https://roliascan.com/content/media/manga-10850-cover-1775133468.jpg",
            page.originalCoverUrl,
        )
        assertEquals(
            "https://roliascan.com/manga/shangri-la-frontier-kusoge-hunter-kamige-ni-idoman-to-su/",
            page.seriesUrl,
        )
        assertEquals("Capítulo 286", page.currentChapter?.label)
        assertEquals(
            listOf("Capítulo 286", "Capítulo 285", "Capítulo 284"),
            page.chapters.map { it.label },
        )
        assertTrue(page.isLatestChapterKnown)
        assertTrue(page.isChapterListComplete)
    }

    @Test
    fun reportsDynamicListWithoutClaimingRecentChaptersWhenEndpointIsUnavailable() {
        val result = RoliaScanParser().parse(
            document(
                apiResponse = null,
                requestedUrl = "https://roliascan.com/read/shangri-la-frontier-kusoge-hunter-kamige-ni-idoman-to-su/ch285-342785/",
            ),
        )

        assertTrue(result is SiteParseResult.RequiresConfiguration)
        val page = (result as SiteParseResult.RequiresConfiguration).preview
        assertEquals("Capítulo 285", page?.currentChapter?.label)
        assertEquals(
            "https://roliascan.com/content/media/manga-10850-cover-1775133468.jpg",
            page?.originalCoverUrl,
        )
        assertFalse(page?.isLatestChapterKnown == true)
    }

    @Test
    fun keepsAValidatedNewestWindowPartialWhenTheEndpointHasMorePages() {
        val response = fixture("fixtures/roliascan-chapters.json")
            .replace("\"has_more\": false", "\"has_more\": true")

        val result = RoliaScanParser().parse(document(apiResponse = response))

        assertTrue(result is SiteParseResult.Success)
        val page = (result as SiteParseResult.Success).page
        assertTrue(page.isLatestChapterKnown)
        assertFalse(page.isChapterListComplete)
        assertEquals("Capítulo 286", page.currentChapter?.label)
    }

    @Test
    fun rejectsChapterRowsWithMismatchedUrlsOrUnverifiedOrdering() {
        val fixture = fixture("fixtures/roliascan-chapters.json")
        val mismatchedUrl = fixture.replace(
            "https://roliascan.com/read/shangri-la-frontier-kusoge-hunter-kamige-ni-idoman-to-su/ch286-372236/",
            "https://other.example/read/shangri-la-frontier-kusoge-hunter-kamige-ni-idoman-to-su/ch286-372236/",
        )
        val outOfOrder = fixture.replace(
            "\"chapter\": \"285\"",
            "\"chapter\": \"287\"",
        )

        for (response in listOf(mismatchedUrl, outOfOrder)) {
            val result = RoliaScanParser().parse(document(apiResponse = response))
            assertTrue(result is SiteParseResult.RequiresConfiguration)
            assertFalse((result as SiteParseResult.RequiresConfiguration).preview?.isLatestChapterKnown == true)
        }
    }

    private fun document(
        apiResponse: String?,
        requestedUrl: String = SERIES_URL,
    ): ParserDocument = ParserDocument(
        pageUrl = SERIES_URL,
        contentType = "text/html",
        html = fixture("fixtures/roliascan-series.html"),
        requestedUrl = requestedUrl,
        supplementalData = buildMap {
            put(RoliaScanParser.MANGA_ID_KEY, "10850")
            if (apiResponse != null) put(RoliaScanParser.CHAPTERS_RESPONSE_KEY, apiResponse)
        },
    )

    private fun fixture(path: String): String {
        val stream = javaClass.classLoader!!.getResourceAsStream(path)
            ?: error("Missing test fixture: $path")
        return stream.use { input -> String(input.readBytes(), StandardCharsets.UTF_8) }
    }

    private companion object {
        const val SERIES_URL =
            "https://roliascan.com/manga/shangri-la-frontier-kusoge-hunter-kamige-ni-idoman-to-su/"
    }
}
