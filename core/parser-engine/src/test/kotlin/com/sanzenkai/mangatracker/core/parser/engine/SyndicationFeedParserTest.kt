package com.sanzenkai.mangatracker.core.parser.engine

import com.sanzenkai.mangatracker.core.parser.api.ParserDocument
import com.sanzenkai.mangatracker.core.parser.api.SiteParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyndicationFeedParserTest {
    private val parser = SyndicationFeedParser()

    @Test
    fun parsesSameSiteRssItemsAsANonCompleteNewestChapterWindow() {
        val feedUrl = "https://fiction.test/manga/northwind-chronicles/feed/"
        val result = parser.parse(
            seriesDocument(
                feedUrl = feedUrl,
                feedFixture = "series-feed.xml",
            ),
        )

        assertTrue(result is SiteParseResult.Success)
        val page = (result as SiteParseResult.Success).page
        assertEquals("Northwind Chronicles", page.title)
        assertEquals(
            listOf("Chapter 45: The Crown", "Chapter 44: The Crossing", "Chapter 43: The Map"),
            page.chapters.map { it.label },
        )
        assertEquals(page.chapters.first(), page.currentChapter)
        assertEquals("https://fiction.test/covers/northwind.webp", page.originalCoverUrl)
        assertEquals(feedUrl, page.metadata[SyndicationFeedParser.FEED_URL_METADATA_KEY])
        assertNotNull(page.chapters.first().publishedAtEpochMillis)
        assertFalse(page.isChapterListComplete)
        assertTrue(page.isLatestChapterKnown)
    }

    @Test
    fun parsesAtomEntriesAndResolvesTheSeriesFromCanonicalMetadata() {
        val result = parser.parse(
            seriesDocument(
                feedUrl = "https://fiction.test/manga/northwind-chronicles/feed/",
                feedFixture = "series-feed.atom",
            ),
        )

        assertTrue(result is SiteParseResult.Success)
        val page = (result as SiteParseResult.Success).page
        assertEquals("https://fiction.test/manga/northwind-chronicles/", page.seriesUrl)
        assertEquals(listOf("Chapter 2: Winter", "Chapter 1: Arrival"), page.chapters.map { it.label })
        assertTrue(page.isLatestChapterKnown)
        assertFalse(page.isChapterListComplete)
    }

    @Test
    fun derivesTheWordPressFeedPathOnlyAsAFeedHint() {
        val feedUrl = "https://fiction.test/manga/northwind-chronicles/feed/"
        val document = ParserDocument(
            pageUrl = "https://fiction.test/manga/northwind-chronicles/",
            contentType = "text/html",
            html = fixture("wordpress-series-page.html"),
            supplementalData = mapOf(
                SyndicationFeedParser.supplementalDataKey(feedUrl) to fixture("series-feed.xml"),
            ),
        )

        assertNotNull(parser.match(document))
        val result = parser.parse(document)

        assertTrue(result is SiteParseResult.Success)
        assertEquals(
            feedUrl,
            (result as SiteParseResult.Success).page.metadata[SyndicationFeedParser.FEED_URL_METADATA_KEY],
        )
    }

    @Test
    fun missingFeedBodyIsNotReportedAsAnEmptyChapterWindow() {
        val document = ParserDocument(
            pageUrl = "https://fiction.test/manga/northwind-chronicles/",
            contentType = "text/html",
            html = fixture("series-page.html"),
        )

        val result = parser.parse(document)

        assertTrue(result is SiteParseResult.RequiresConfiguration)
        val preview = (result as SiteParseResult.RequiresConfiguration).preview
        assertTrue(preview?.chapters.isNullOrEmpty())
        assertFalse(preview?.isLatestChapterKnown == true)
        assertFalse(preview?.isChapterListComplete == true)
        assertNotNull(preview?.metadata?.get(SyndicationFeedParser.FEED_URL_METADATA_KEY))
    }

    @Test
    fun emptyFeedIsNotReportedAsAValidNoUpdatesResult() {
        val result = parser.parse(
            seriesDocument(
                feedUrl = "https://fiction.test/manga/northwind-chronicles/feed/",
                feedFixture = "empty-feed.xml",
            ),
        )

        assertTrue(result is SiteParseResult.RequiresConfiguration)
        val preview = (result as SiteParseResult.RequiresConfiguration).preview
        assertTrue(preview?.chapters.isNullOrEmpty())
        assertFalse(preview?.isLatestChapterKnown == true)
        assertFalse(preview?.isChapterListComplete == true)
    }

    @Test
    fun partiallyParseableFeedDoesNotClaimItsFirstItemAsTheLatest() {
        val result = parser.parse(
            seriesDocument(
                feedUrl = "https://fiction.test/manga/northwind-chronicles/feed/",
                feedFixture = "partial-feed.xml",
            ),
        )

        assertTrue(result is SiteParseResult.RequiresConfiguration)
        val preview = (result as SiteParseResult.RequiresConfiguration).preview
        assertEquals(listOf("Chapter 45", "Chapter 44"), preview?.chapters?.map { it.label })
        assertEquals(null, preview?.currentChapter)
        assertFalse(preview?.isLatestChapterKnown == true)
        assertFalse(preview?.isChapterListComplete == true)
    }

    @Test
    fun conflictingChapterAndDateOrderDoesNotEstablishTheLatestChapter() {
        val result = parser.parse(
            seriesDocument(
                feedUrl = "https://fiction.test/manga/northwind-chronicles/feed/",
                feedFixture = "conflicting-feed.xml",
            ),
        )

        assertTrue(result is SiteParseResult.RequiresConfiguration)
        val preview = (result as SiteParseResult.RequiresConfiguration).preview
        assertFalse(preview?.isLatestChapterKnown == true)
        assertFalse(preview?.isChapterListComplete == true)
    }

    @Test
    fun doesNotAcceptAnAlternateFeedFromAnotherHost() {
        val document = ParserDocument(
            pageUrl = "https://fiction.test/manga/northwind-chronicles/",
            contentType = "text/html",
            html = """
                <html><head>
                  <link rel="alternate" type="application/rss+xml"
                        href="https://other.test/feed.xml">
                </head><body></body></html>
            """.trimIndent(),
        )

        assertEquals(null, parser.match(document))
    }

    private fun seriesDocument(feedUrl: String, feedFixture: String): ParserDocument =
        ParserDocument(
            pageUrl = "https://fiction.test/manga/northwind-chronicles/",
            contentType = "text/html",
            html = fixture("series-page.html"),
            supplementalData = mapOf(
                SyndicationFeedParser.supplementalDataKey(feedUrl) to fixture(feedFixture),
            ),
        )

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/fixtures/syndication/$name")) {
            "Missing syndication parser fixture: $name"
        }.readText()
}
