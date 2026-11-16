package com.sanzenkai.mangatracker.parsers.site

import com.sanzenkai.mangatracker.core.parser.api.ParserDocument
import com.sanzenkai.mangatracker.core.parser.api.SiteParseIssue
import com.sanzenkai.mangatracker.core.parser.api.SiteParseResult
import com.sanzenkai.mangatracker.core.parser.engine.ParserRegistry
import com.sanzenkai.mangatracker.core.parser.engine.SiteDiscoveryEngine
import com.sanzenkai.mangatracker.core.parser.engine.SyndicationFeedParser
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteSampleParserTest {
    @Test
    fun defaultRegistryReportsAnUnfetchedSyndicationFeedAsIncomplete() {
        val result = SiteDiscoveryEngine(
            ParserRegistry(MangaSiteParsers.default()),
        ).discover(
            ParserDocument(
                pageUrl = "https://fiction.test/manga/northwind-chronicles/",
                contentType = "text/html",
                html = """
                    <html><head>
                      <title>Northwind Chronicles</title>
                      <link rel="alternate" type="application/rss+xml"
                            href="/manga/northwind-chronicles/feed/">
                    </head><body></body></html>
                """.trimIndent(),
            ),
        )

        assertTrue(result is SiteParseResult.RequiresConfiguration)
        val preview = (result as SiteParseResult.RequiresConfiguration).preview
        assertEquals(
            "https://fiction.test/manga/northwind-chronicles/feed/",
            preview?.metadata?.get(SyndicationFeedParser.FEED_URL_METADATA_KEY),
        )
        assertFalse(preview?.isLatestChapterKnown == true)
        assertFalse(preview?.isChapterListComplete == true)
    }

    @Test
    fun mairimashitaHomepageFindsTheOriginalCoverAndNewestChapterWindow() {
        val result = SiteDiscoveryEngine(
            ParserRegistry(MangaSiteParsers.default()),
        ).discover(
            ParserDocument(
                pageUrl = "https://mairimashitairuma-kun.com/",
                contentType = "text/html",
                html = fixture("fixtures/mairimashita-home.html"),
            ),
        )

        assertTrue(result is SiteParseResult.Success)
        val page = (result as SiteParseResult.Success).page
        assertEquals("Mairimashita Iruma-Kun", page.title)
        assertEquals("https://mairimashitairuma-kun.com/", page.seriesUrl)
        assertEquals(
            "https://mairimashitairuma-kun.com/wp-content/uploads/2024/10/Mairimashita-Iruma-Kun-Manga-Volume-1-663x1024.webp",
            page.originalCoverUrl,
        )
        assertEquals("Chapter 467", page.currentChapter?.label)
        assertEquals(
            listOf("Chapter 467", "Chapter 466", "Chapter 465", "Chapter 443.5", "Chapter 443"),
            page.chapters.map { it.label },
        )
        assertTrue(page.isLatestChapterKnown)
        assertFalse(page.isChapterListComplete)
    }

    @Test
    fun hivetoonsSampleExtractsRealStructuredMetadataButDoesNotClaimACompleteList() {
        val result = HiveToonsArticleParser().parse(
            ParserDocument(
                pageUrl = "https://hivetoons.org/series/eleceed/chapter-426",
                contentType = "text/html",
                html = fixture("fixtures/hivetoons-chapter-426.html"),
            ),
        )

        assertTrue(result is SiteParseResult.RequiresConfiguration)
        val needsConfiguration = result as SiteParseResult.RequiresConfiguration
        assertEquals(SiteParseIssue.INCOMPLETE_CHAPTER_LIST, needsConfiguration.issue)
        assertEquals("Eleceed", needsConfiguration.preview?.title)
        assertEquals(
            "https://hivetoons.org/series/eleceed",
            needsConfiguration.preview?.seriesUrl,
        )
        assertEquals("chapter 426", needsConfiguration.preview?.currentChapter?.label)
        assertEquals(
            "https://storage.hivetoon.com/public//upload/2024/11/20/cover_1732118474124.webp",
            needsConfiguration.preview?.originalCoverUrl,
        )
        assertNotNull(needsConfiguration.preview?.currentChapter?.publishedAtEpochMillis)
        assertFalse(needsConfiguration.preview?.isChapterListComplete == true)
        assertTrue(needsConfiguration.preview?.chapters.isNullOrEmpty())
    }

    @Test
    fun hivetoonsSeriesSampleParsesTheNewestChaptersWithoutTheFullArchive() {
        val result = HiveToonsSeriesParser().parse(
            ParserDocument(
                pageUrl = "https://hivetoons.org/series/eleceed",
                contentType = "text/html",
                html = fixture("fixtures/hivetoons-series-partial.html"),
            ),
        )

        assertTrue(result is SiteParseResult.Success)
        val page = (result as SiteParseResult.Success).page
        assertEquals("Eleceed", page.title)
        assertEquals(
            "https://hivetoons.org/series/eleceed",
            page.seriesUrl,
        )
        assertEquals(
            "https://storage.hivetoon.com/public/cover.webp",
            page.originalCoverUrl,
        )
        assertEquals(3, page.chapters.size)
        assertEquals("Chapter 426", page.currentChapter?.label)
        assertNotNull(page.chapters.first().publishedAtEpochMillis)
        assertFalse(page.isChapterListComplete)
        assertTrue(
            "Parsed chapter order: ${page.chapters.map { it.url }}",
            page.isLatestChapterKnown,
        )
    }

    @Test
    fun hivetoonsSeriesParserCanTrackTheLatestChapterWithoutACompleteArchive() {
        val html = fixture("fixtures/hivetoons-series-partial.html")
            .replace("Chapters (427)", "Chapters (3)")
        val result = HiveToonsSeriesParser().parse(
            ParserDocument(
                pageUrl = "https://hivetoons.org/series/eleceed",
                contentType = "text/html",
                html = html,
            ),
        )

        assertTrue(result is SiteParseResult.Success)
        val page = (result as SiteParseResult.Success).page
        assertFalse(page.isChapterListComplete)
        assertTrue(page.isLatestChapterKnown)
        assertEquals("Chapter 426", page.currentChapter?.label)
        assertEquals(3, page.chapters.size)
    }

    @Test
    fun hivetoonsSeriesParserAcceptsAnOrderedRecentWindowWithoutCountOrOnlyChapterLinks() {
        val html = fixture("fixtures/hivetoons-series-partial.html")
            .replace("Chapters (427)", "Chapters")
            .replace(
                "</section>\n</body>",
                """<a href="/series/eleceed">Series page</a></section>
</body>""",
            )
        val result = HiveToonsSeriesParser().parse(
            ParserDocument(
                pageUrl = "https://hivetoons.org/series/eleceed",
                contentType = "text/html",
                html = html,
            ),
        )

        assertTrue(result is SiteParseResult.Success)
        val page = (result as SiteParseResult.Success).page
        assertFalse(page.isChapterListComplete)
        assertTrue(page.isLatestChapterKnown)
        assertTrue(page.warnings.isEmpty())
        assertEquals("Chapter 426", page.currentChapter?.label)
        assertEquals(3, page.chapters.size)
    }

    @Test
    fun hivetoonsSeriesParserDoesNotAssumeOneUndeclaredChapterIsTheLatest() {
        val result = HiveToonsSeriesParser().parse(
            ParserDocument(
                pageUrl = "https://hivetoons.org/series/eleceed",
                contentType = "text/html",
                html = """
                    <html><head>
                      <title>Eleceed</title>
                      <link rel="canonical" href="https://hivetoons.org/series/eleceed">
                    </head><body>
                      <div data-series-tab-button="chapters">Chapters</div>
                      <section data-series-tab-panel="chapters">
                        <a href="/series/eleceed/chapter-426">Chapter 426</a>
                      </section>
                    </body></html>
                """.trimIndent(),
            ),
        )

        assertTrue(result is SiteParseResult.RequiresConfiguration)
        val page = (result as SiteParseResult.RequiresConfiguration).preview
        assertFalse(page?.isLatestChapterKnown == true)
        assertEquals(1, page?.chapters?.size)
    }

    @Test
    fun hivetoonsSeriesParserDoesNotTrustAnOutOfOrderRecentWindow() {
        val html = fixture("fixtures/hivetoons-series-partial.html")
            .replace("chapter-425", "chapter-427")
        val result = HiveToonsSeriesParser().parse(
            ParserDocument(
                pageUrl = "https://hivetoons.org/series/eleceed",
                contentType = "text/html",
                html = html,
            ),
        )

        assertTrue(result is SiteParseResult.RequiresConfiguration)
        val incomplete = result as SiteParseResult.RequiresConfiguration
        assertEquals(SiteParseIssue.INCOMPLETE_CHAPTER_LIST, incomplete.issue)
        assertFalse(incomplete.preview?.isLatestChapterKnown == true)
    }

    @Test
    fun hivetoonsSeriesParserCombinesVisibleRowsWithValidatedHydratedRecords() {
        val result = HiveToonsSeriesParser().parse(
            ParserDocument(
                pageUrl = "https://hivetoons.org/series/eleceed",
                contentType = "text/html",
                html = fixture("fixtures/hivetoons-series-hydrated.html"),
            ),
        )

        assertTrue(result is SiteParseResult.Success)
        val page = (result as SiteParseResult.Success).page
        assertTrue(page.isChapterListComplete)
        assertTrue(
            "Parsed chapter order: ${page.chapters.map { it.url }}",
            page.isLatestChapterKnown,
        )
        assertEquals(3, page.chapters.size)
        assertEquals(
            listOf("Chapter 3", "Chapter 2", "Chapter 1"),
            page.chapters.map { it.label },
        )
        assertEquals(
            "https://hivetoons.org/series/eleceed/chapter-1",
            page.chapters.last().url,
        )
        assertNotNull(page.chapters.last().publishedAtEpochMillis)
    }

    @Test
    fun hivetoonsSeriesParserReconcilesA427ChapterHydrationPayload() {
        val chapterNumbers = (426 downTo 409).map { it.toString() } +
            "408.5" +
            (408 downTo 1).map { it.toString() }
        val hydrationRecords = chapterNumbers.mapIndexed { index, number ->
            """${'$'}R[$index]={id:${index + 1},slug:"chapter-$number",number:$number,title:"",featuredImage:null,createdAt:"2026-11-11T17:15:30.276Z"}"""
        }.joinToString(",")
        val html = fixture("fixtures/hivetoons-series-partial.html").replace(
            "</body>",
            """<script data-tsr-stream-part="">$hydrationRecords,totalChapterCount:427</script></body>""",
        )

        val result = HiveToonsSeriesParser().parse(
            ParserDocument(
                pageUrl = "https://hivetoons.org/series/eleceed",
                contentType = "text/html",
                html = html,
            ),
        )

        assertTrue(result is SiteParseResult.Success)
        val page = (result as SiteParseResult.Success).page
        assertTrue(page.isChapterListComplete)
        assertTrue(page.isLatestChapterKnown)
        assertEquals(427, page.chapters.size)
        assertEquals(
            "https://hivetoons.org/series/eleceed/chapter-408.5",
            page.chapters.single { it.label == "Chapter 408.5" }.url,
        )
    }

    @Test
    fun hivetoonsSeriesParserRejectsHydrationRecordsWhoseNumberDoesNotMatchSlug() {
        val html = fixture("fixtures/hivetoons-series-hydrated.html")
            .replace("slug:\"chapter-2\",number:2", "slug:\"chapter-2\",number:20")
        val result = HiveToonsSeriesParser().parse(
            ParserDocument(
                pageUrl = "https://hivetoons.org/series/eleceed",
                contentType = "text/html",
                html = html,
            ),
        )

        assertTrue(result is SiteParseResult.RequiresConfiguration)
        val incomplete = result as SiteParseResult.RequiresConfiguration
        assertEquals(SiteParseIssue.INCOMPLETE_CHAPTER_LIST, incomplete.issue)
        assertFalse(incomplete.preview?.isChapterListComplete == true)
    }

    @Test
    fun imperioSampleIsReportedAsClientRenderedAndItsMetadataIsOnlyAPreview() {
        val document = ParserDocument(
            pageUrl = "https://imperiodabritannia.net/manga/o-exilado-cavaleiro-pesado-reencarnado-e-incomparavel-com-o-conhecimento-do-jogo/capitulo/185",
            contentType = "text/html",
            html = fixture("fixtures/imperiodabritannia-chapter-185.html"),
        )
        val result = SiteDiscoveryEngine(
            ParserRegistry(MangaSiteParsers.default()),
        ).discover(document)

        assertTrue(result is SiteParseResult.RequiresConfiguration)
        val needsConfiguration = result as SiteParseResult.RequiresConfiguration
        assertEquals(SiteParseIssue.UNSUPPORTED_DYNAMIC_CONTENT, needsConfiguration.issue)
        assertEquals("185", needsConfiguration.preview?.currentChapter?.label?.substringAfterLast(' '))
        assertFalse(needsConfiguration.preview?.isChapterListComplete == true)
        assertTrue(needsConfiguration.preview?.chapters.isNullOrEmpty())
    }

    @Test
    fun imperioSeriesPageShowsItsTitleAndRequestsAChapterLink() {
        val document = ParserDocument(
            pageUrl = "https://imperiodabritannia.net/manga/eu-me-tornei-um-homem-casado-em-outro-mundo/",
            contentType = "text/html",
            html = fixture("fixtures/imperiodabritannia-eu-me-casei-shell.html"),
        )
        val result = SiteDiscoveryEngine(
            ParserRegistry(MangaSiteParsers.default()),
        ).discover(document)

        assertTrue(result is SiteParseResult.RequiresConfiguration)
        val needsConfiguration = result as SiteParseResult.RequiresConfiguration
        assertEquals(SiteParseIssue.UNSUPPORTED_DYNAMIC_CONTENT, needsConfiguration.issue)
        assertEquals("Eu Me Tornei Um Homem Casado Em Outro Mundo", needsConfiguration.preview?.title)
        assertEquals(
            "https://imperiodabritannia.net/manga/eu-me-tornei-um-homem-casado-em-outro-mundo/",
            needsConfiguration.preview?.seriesUrl,
        )
        assertEquals(null, needsConfiguration.preview?.currentChapter)
    }

    @Test
    fun imperioPublicApiProvidesVerifiedLatestChapterWindow() {
        val result = SiteDiscoveryEngine(
            ParserRegistry(MangaSiteParsers.default()),
        ).discover(
            ParserDocument(
                pageUrl = "https://imperiodabritannia.net/manga/eu-me-tornei-um-homem-casado-em-outro-mundo/",
                contentType = "application/json",
                html = fixture("fixtures/imperiodabritannia-api.json"),
            ),
        )

        assertTrue(result is SiteParseResult.Success)
        val page = (result as SiteParseResult.Success).page
        assertEquals("Eu Me Tornei Um Homem Casado Em Outro Mundo", page.title)
        assertEquals(
            "https://cdn.imperiodabritannia.net/obras/2461/cover.webp",
            page.originalCoverUrl,
        )
        assertEquals(
            "https://imperiodabritannia.net/manga/eu-me-tornei-um-homem-casado-em-outro-mundo/",
            page.seriesUrl,
        )
        assertEquals("Capítulo 34", page.currentChapter?.label)
        assertEquals(
            listOf("Capítulo 34", "Capítulo 33", "Capítulo 32"),
            page.chapters.map { it.label },
        )
        assertTrue(page.isLatestChapterKnown)
        assertFalse(page.isChapterListComplete)
    }

    @Test
    fun imperioPublicApiAcceptsRecentChapterDataWhenItsDeclaredTotalIsStale() {
        val result = ImperioDaBritanniaParser().parse(
            ParserDocument(
                pageUrl = "https://imperiodabritannia.net/manga/o-exilado-cavaleiro-pesado-reencarnado-e-incomparavel-com-o-conhecimento-do-jogo/capitulo/185",
                contentType = "application/json",
                html = """
                    {
                      "sucesso": true,
                      "obra": {
                        "id": 1513,
                        "nome": "O Exilado Cavaleiro Pesado Reencarnado",
                        "imagem": "obras/1513/cover.png",
                        "total_capitulos": 148,
                        "slug": "o-exilado-cavaleiro-pesado-reencarnado-e-incomparavel-com-o-conhecimento-do-jogo",
                        "capitulos": [
                          {"id": 18501, "obra_id": 1513, "numero": "185.00", "nome": "Capítulo 185"},
                          {"id": 18601, "obra_id": 1513, "numero": "186.00", "nome": "Capítulo 186"}
                        ]
                      }
                    }
                """.trimIndent(),
            ),
        )

        assertTrue(result is SiteParseResult.Success)
        val page = (result as SiteParseResult.Success).page
        assertEquals(
            "https://cdn.imperiodabritannia.net/obras/1513/cover.png",
            page.originalCoverUrl,
        )
        assertEquals("Capítulo 186", page.currentChapter?.label)
        assertTrue(page.isLatestChapterKnown)
        assertFalse(page.isChapterListComplete)
    }

    @Test
    fun imperioParserRejectsApiDataForAnotherManga() {
        val wrongManga = fixture("fixtures/imperiodabritannia-api.json")
            .replace(
                "eu-me-tornei-um-homem-casado-em-outro-mundo",
                "outro-manga",
            )

        val result = ImperioDaBritanniaParser().parse(
            ParserDocument(
                pageUrl = "https://imperiodabritannia.net/manga/eu-me-tornei-um-homem-casado-em-outro-mundo/",
                contentType = "application/json",
                html = wrongManga,
            ),
        )

        assertTrue(result is SiteParseResult.Failure)
    }

    @Test
    fun imperioParserRejectsMalformedChapterIds() {
        val malformedId = fixture("fixtures/imperiodabritannia-api.json")
            .replace("\"id\": 92945", "\"id\": \"invalid\"")

        val result = ImperioDaBritanniaParser().parse(
            ParserDocument(
                pageUrl = "https://imperiodabritannia.net/manga/eu-me-tornei-um-homem-casado-em-outro-mundo/",
                contentType = "application/json",
                html = malformedId,
            ),
        )

        assertTrue(result is SiteParseResult.Failure)
    }

    @Test
    fun imperioChapterLinkProvidesALocalRegistrationPreviewWithoutClaimingUpdates() {
        val document = ParserDocument(
            pageUrl = "https://imperiodabritannia.net/manga/eu-me-tornei-um-homem-casado-em-outro-mundo/capitulo/185",
            contentType = "text/html",
            html = fixture("fixtures/imperiodabritannia-eu-me-casei-shell.html"),
        )
        val result = SiteDiscoveryEngine(
            ParserRegistry(MangaSiteParsers.default()),
        ).discover(document)

        assertTrue(result is SiteParseResult.RequiresConfiguration)
        val page = (result as SiteParseResult.RequiresConfiguration).preview
        assertEquals("Eu Me Tornei Um Homem Casado Em Outro Mundo", page?.title)
        assertEquals("https://imperiodabritannia.net/manga/eu-me-tornei-um-homem-casado-em-outro-mundo/", page?.seriesUrl)
        assertEquals("Capítulo 185", page?.currentChapter?.label)
        assertFalse(page?.isLatestChapterKnown == true)
        assertTrue(page?.chapters.isNullOrEmpty())
    }

    @Test
    fun imperioApiEndpointIsBuiltOnlyForRecognizedMangaPages() {
        assertEquals(
            "https://api.imperiodabritannia.net/api/obras/by-slug/eu-me-tornei-um-homem-casado-em-outro-mundo",
            ImperioDaBritanniaParser.apiUrlForPage(
                "https://imperiodabritannia.net/manga/eu-me-tornei-um-homem-casado-em-outro-mundo/capitulo/34",
            ),
        )
        assertEquals(
            null,
            ImperioDaBritanniaParser.apiUrlForPage("https://not-imperiodabritannia.net/manga/slug/"),
        )
    }

    private fun fixture(path: String): String {
        val stream = javaClass.classLoader!!.getResourceAsStream(path)
            ?: error("Missing test fixture: $path")
        return stream.use { input -> String(input.readBytes(), StandardCharsets.UTF_8) }
    }
}
