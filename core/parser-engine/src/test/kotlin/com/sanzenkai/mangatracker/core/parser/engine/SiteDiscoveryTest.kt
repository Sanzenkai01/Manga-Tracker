package com.sanzenkai.mangatracker.core.parser.engine

import com.sanzenkai.mangatracker.core.parser.api.ParserDocument
import com.sanzenkai.mangatracker.core.parser.api.SiteParseIssue
import com.sanzenkai.mangatracker.core.parser.api.SiteParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteDiscoveryTest {
    @Test
    fun extractsOpenGraphAndSchemaOrgMetadataWithoutInventingAChapterList() {
        val document = ParserDocument(
            pageUrl = "https://example.test/series/sample/chapter-12.5",
            contentType = "text/html",
            html = """
                <html><head>
                  <meta property="og:title" content="Sample Manga Chapter 12.5">
                  <meta property="og:image" content="/covers/sample.webp">
                  <link rel="canonical" href="https://example.test/series/sample/chapter-12.5">
                  <script type="application/ld+json">
                    {"@context":"https://schema.org","@type":"Article",
                     "headline":"Sample Manga Chapter 12.5",
                     "datePublished":"2026-03-04T10:00:00Z",
                     "isPartOf":{"@type":"CreativeWorkSeries","name":"Sample Manga",
                                 "url":"https://example.test/series/sample"}}
                  </script>
                </head><body><main>Chapter page</main></body></html>
            """.trimIndent(),
        )

        val result = StructuredMetadataParser().parse(document)

        assertTrue(result is SiteParseResult.RequiresConfiguration)
        val preview = (result as SiteParseResult.RequiresConfiguration).preview
        assertNotNull(preview)
        assertEquals("Sample Manga", preview?.title)
        assertEquals("https://example.test/covers/sample.webp", preview?.originalCoverUrl)
        assertEquals("https://example.test/series/sample", preview?.seriesUrl)
        assertEquals("chapter 12.5", preview?.currentChapter?.label)
        assertNotNull(preview?.currentChapter?.publishedAtEpochMillis)
        assertTrue(preview?.metadata?.containsKey("og:title") == true)
        assertFalse(preview?.isChapterListComplete == true)
        assertEquals(SiteParseIssue.INCOMPLETE_CHAPTER_LIST, (result as SiteParseResult.RequiresConfiguration).issue)
    }

    @Test
    fun discoversWordPressAsAPlatformHintNotAsAThemeParser() {
        val document = ParserDocument(
            pageUrl = "https://example.test/manga/chapter-3",
            contentType = "text/html",
            html = """
                <html><head><title>WordPress Manga Chapter 3</title>
                  <meta name="generator" content="WordPress 6.7">
                </head><body><main>Chapter 3</main></body></html>
            """.trimIndent(),
        )
        val parser = WordPressStructureParser()

        assertNotNull(parser.match(document))
        val result = parser.parse(document)
        assertTrue(result is SiteParseResult.RequiresConfiguration)
        assertEquals(
            setOf("WordPress"),
            (result as SiteParseResult.RequiresConfiguration).preview?.platformHints,
        )
    }

    @Test
    fun acceptsOnlyExplicitSchemaItemListsAsACompleteChapterList() {
        val document = ParserDocument(
            pageUrl = "https://example.test/series/sample/chapter-2",
            contentType = "text/html",
            html = """
                <html><head>
                  <script type="application/ld+json">
                    {"@graph":[
                      {"@type":"Article","headline":"Sample Chapter 2",
                       "isPartOf":{"@type":"CreativeWorkSeries","name":"Sample"}},
                      {"@type":"ItemList","numberOfItems":2,"itemListElement":[
                        {"@type":"ListItem","position":1,
                         "item":{"@type":"CreativeWork","name":"Chapter 1",
                                 "url":"/series/sample/chapter-1"}},
                        {"@type":"ListItem","position":2,
                         "item":{"@type":"CreativeWork","name":"Chapter 2",
                                 "url":"/series/sample/chapter-2"}}
                      ]}
                    ]}
                  </script>
                </head><body></body></html>
            """.trimIndent(),
        )

        val result = StructuredMetadataParser().parse(document)

        assertTrue(result is SiteParseResult.Success)
        val page = (result as SiteParseResult.Success).page
        assertEquals("Sample", page.title)
        assertEquals(2, page.chapters.size)
        assertTrue(page.isChapterListComplete)
        assertEquals(
            "https://example.test/series/sample/chapter-1",
            page.chapters.first().url,
        )
    }

    @Test
    fun itemListWithoutAnExplicitMatchingCountIsNotAssumedComplete() {
        val document = ParserDocument(
            pageUrl = "https://example.test/series/sample/chapter-2",
            contentType = "text/html",
            html = """
                <html><head>
                  <title>Sample</title>
                  <script type="application/ld+json">
                    {"@type":"ItemList","itemListElement":[
                      {"@type":"ListItem","item":{"@type":"CreativeWork",
                        "name":"Chapter 1","url":"/series/sample/chapter-1"}},
                      {"@type":"ListItem","item":{"@type":"CreativeWork",
                        "name":"Chapter 2","url":"/series/sample/chapter-2"}}
                    ]}
                  </script>
                </head><body></body></html>
            """.trimIndent(),
        )

        val result = StructuredMetadataParser().parse(document)
        val incomplete = result as SiteParseResult.RequiresConfiguration

        assertEquals(2, incomplete.preview?.chapters?.size)
        assertFalse(incomplete.preview?.isChapterListComplete == true)
    }

    @Test
    fun discoversNewestChapterWindowFromGenericStaticLinks() {
        val document = ParserDocument(
            pageUrl = "https://generic.test/manga/solar-knights",
            contentType = "text/html",
            html = """
                <html><head>
                  <title>Solar Knights</title>
                  <meta property="og:image" content="/covers/solar-knights.webp">
                  <link rel="canonical" href="https://generic.test/manga/solar-knights">
                </head><body>
                  <h1>Solar Knights</h1>
                  <nav><a href="/manga/other/chapter-999">Chapter 999</a></nav>
                  <ul class="chapter-list">
                    <li><a href="/manga/solar-knights/chapter-104-the-final-battle">Ch. 104</a></li>
                    <li><a href="/manga/solar-knights/chapter/103">Chapter 103</a></li>
                    <li><a href="/manga/solar-knights/102">Chapter 102</a></li>
                    <li><a href="https://outside.test/manga/solar-knights/chapter-1000">
                      Chapter 1000
                    </a></li>
                  </ul>
                  <section class="related">
                    <a href="/manga/another-series/chapter-2000">Chapter 2000</a>
                  </section>
                </body></html>
            """.trimIndent(),
        )

        val result = HtmlMetadataParser().parse(document)

        assertTrue(result is SiteParseResult.Success)
        val page = (result as SiteParseResult.Success).page
        assertEquals("Solar Knights", page.title)
        assertEquals(
            "https://generic.test/covers/solar-knights.webp",
            page.originalCoverUrl,
        )
        assertEquals("https://generic.test/manga/solar-knights", page.seriesUrl)
        assertEquals("Ch 104", page.currentChapter?.label)
        assertEquals(
            listOf("Ch 104", "Chapter 103", "Chapter 102"),
            page.chapters.map { it.label },
        )
        assertFalse(page.isChapterListComplete)
        assertTrue(page.isLatestChapterKnown)
    }

    @Test
    fun groupsWordPressChapterPostSlugsBySeriesAndKeepsDecimalChapters() {
        val document = ParserDocument(
            pageUrl = "https://generic.test/",
            contentType = "text/html",
            html = """
                <html><head><title>Solar Knights</title></head><body>
                  <h1>Solar Knights</h1>
                  <ul>
                    <li><a href="/manga/solar-knights-chapter-104/">Chapter 104</a></li>
                    <li><a href="/manga/solar-knights-chapter-103-5/">Chapter 103.5</a></li>
                    <li><a href="/manga/solar-knights-chapter-103/">Chapter 103</a></li>
                  </ul>
                  <ul>
                    <li><a href="/manga/other-manga-chapter-900/">Chapter 900</a></li>
                    <li><a href="/manga/other-manga-chapter-899/">Chapter 899</a></li>
                    <li><a href="/manga/other-manga-chapter-898/">Chapter 898</a></li>
                  </ul>
                </body></html>
            """.trimIndent(),
        )

        val result = HtmlMetadataParser().parse(document)

        assertTrue(result is SiteParseResult.Success)
        val page = (result as SiteParseResult.Success).page
        assertEquals(
            listOf("Chapter 104", "Chapter 103.5", "Chapter 103"),
            page.chapters.map { it.label },
        )
        assertEquals("Chapter 104", page.currentChapter?.label)
        assertTrue(page.isLatestChapterKnown)
        assertFalse(page.isChapterListComplete)
    }

    @Test
    fun derivesSeriesUrlAndDecimalIdentityFromAWordPressChapterPostSlug() {
        val document = ParserDocument(
            pageUrl = "https://generic.test/manga/solar-knights-chapter-103-5/",
            contentType = "text/html",
            html = """
                <html><head>
                  <meta property="og:title" content="Solar Knights Chapter 103.5">
                </head><body><h1>Solar Knights Chapter 103.5</h1></body></html>
            """.trimIndent(),
        )

        val result = HtmlMetadataParser().parse(document)

        assertTrue(result is SiteParseResult.RequiresConfiguration)
        val page = (result as SiteParseResult.RequiresConfiguration).preview
        assertEquals("Solar Knights", page?.title)
        assertEquals(
            "https://generic.test/manga/solar-knights",
            page?.seriesUrl,
        )
        assertEquals("chapter 103.5", page?.currentChapter?.label)
    }

    @Test
    fun keepsHyphenatedSpecialChapterIdentifiersNonNumeric() {
        val document = ParserDocument(
            pageUrl = "https://generic.test/manga/solar-knights/chapter-special-1",
            contentType = "text/html",
            html = "<html><head><title>Solar Knights</title></head><body></body></html>",
        )

        val result = HtmlMetadataParser().parse(document)

        assertTrue(result is SiteParseResult.RequiresConfiguration)
        assertEquals(
            "chapter special-1",
            (result as SiteParseResult.RequiresConfiguration).preview?.currentChapter?.label,
        )
    }

    @Test
    fun doesNotTrustAnOutOfOrderGenericChapterWindow() {
        val document = ParserDocument(
            pageUrl = "https://generic.test/manga/solar-knights",
            contentType = "text/html",
            html = """
                <html><head><title>Solar Knights</title></head><body>
                  <ul>
                    <li><a href="/manga/solar-knights/chapter-104-the-final-battle">Chapter 104</a></li>
                    <li><a href="/manga/solar-knights/chapter-102">Chapter 102</a></li>
                    <li><a href="/manga/solar-knights/chapter-103">Chapter 103</a></li>
                  </ul>
                </body></html>
            """.trimIndent(),
        )

        val result = HtmlMetadataParser().parse(document)

        assertTrue(result is SiteParseResult.RequiresConfiguration)
        val preview = (result as SiteParseResult.RequiresConfiguration).preview
        assertFalse(preview?.isLatestChapterKnown == true)
        assertFalse(preview?.isChapterListComplete == true)
    }

    @Test
    fun recognizesPortugueseAccentedChapterRoutes() {
        val document = ParserDocument(
            pageUrl = "https://generic.test/manga/solar-knights/cap\u00edtulo/104",
            contentType = "text/html",
            html = """
                <html><head><title>Solar Knights</title></head>
                <body><h1>Solar Knights</h1></body></html>
            """.trimIndent(),
        )

        val result = HtmlMetadataParser().parse(document)

        assertTrue(result is SiteParseResult.RequiresConfiguration)
        val preview = (result as SiteParseResult.RequiresConfiguration).preview
        assertEquals("cap\u00edtulo 104", preview?.currentChapter?.label)
        assertEquals("https://generic.test/manga/solar-knights", preview?.seriesUrl)
    }

    @Test
    fun ignoresJsonLdWithExcessiveNestingOrScriptSize() {
        val nestedJson = "[".repeat(80) + "null" + "]".repeat(80)
        val oversizedJson = " ".repeat(128 * 1024 + 1)
        val document = ParserDocument(
            pageUrl = "https://example.test/series/sample/chapter-2",
            contentType = "text/html",
            html = """
                <html><head><title>Sample</title>
                  <script type="application/ld+json">$nestedJson</script>
                  <script type="application/ld+json">$oversizedJson</script>
                </head><body></body></html>
            """.trimIndent(),
        )

        val result = StructuredMetadataParser().parse(document)
        val incomplete = result as SiteParseResult.RequiresConfiguration

        assertFalse(incomplete.preview?.isChapterListComplete == true)
    }

    @Test
    fun reportsClientRenderedShellInsteadOfAnEmptySuccessfulResult() {
        val document = ParserDocument(
            pageUrl = "https://example.test/manga/sample/capitulo/185",
            contentType = "text/html",
            html = """
                <html><head><title>Sample Manga</title>
                  <meta property="og:title" content="Sample Manga">
                  <script type="module" src="/assets/app.js"></script>
                </head><body><div id="root"></div></body></html>
            """.trimIndent(),
        )
        val engine = SiteDiscoveryEngine(
            ParserRegistry(setOf(StructuredMetadataParser(), HtmlMetadataParser())),
        )

        val result = engine.discover(document)

        assertTrue(result is SiteParseResult.RequiresConfiguration)
        assertEquals(
            SiteParseIssue.UNSUPPORTED_DYNAMIC_CONTENT,
            (result as SiteParseResult.RequiresConfiguration).issue,
        )
        assertFalse(result.preview?.isChapterListComplete == true)
    }

    @Test
    fun manualSelectorsResolveRelativeLinksAndCanValidateACompleteExplicitList() {
        val document = ParserDocument(
            pageUrl = "https://example.test/manga/sample/chapter-12.5",
            contentType = "text/html",
            html = """
                <html><body>
                  <h1 class="title">Sample Manga</h1>
                  <img class="cover" src="/covers/sample.webp">
                  <a class="series" href="/manga/sample">Series</a>
                  <span class="chapter-number">Current 12.5</span>
                  <time class="published" datetime="2026-03-04T10:00:00Z"></time>
                  <a class="chapter-link" href="/manga/sample/chapter-12.5">Open</a>
                  <ul class="chapters">
                    <li><span class="chapter-number">Chapter 12</span>
                      <a class="chapter-link" href="/manga/sample/chapter-12">Chapter</a></li>
                    <li><span class="chapter-number">Chapter 12.5</span>
                      <a class="chapter-link" href="/manga/sample/chapter-12.5">Extra</a></li>
                  </ul>
                </body></html>
            """.trimIndent(),
        )
        val configuration = ManualSiteConfiguration(
            normalizedHost = "example.test",
            titleRule = ExtractionRule(".title"),
            coverRule = ExtractionRule(".cover", "src"),
            seriesUrlRule = ExtractionRule(".series", "href"),
            chapterNumberRule = ExtractionRule(
                selector = ".chapter-number",
                regex = "(?:Chapter|Current)\\s+([0-9]+(?:\\.[0-9]+)?)",
            ),
            publishedAtRule = ExtractionRule(".published", "datetime"),
            chapterLinkRule = ExtractionRule(".chapter-link", "href"),
            chapterListItemSelector = ".chapters > li",
        )

        val result = ManualCssRegexParser(configuration).parse(document)

        assertTrue(result is SiteParseResult.Success)
        val page = (result as SiteParseResult.Success).page
        assertEquals("Sample Manga", page.title)
        assertEquals("https://example.test/covers/sample.webp", page.originalCoverUrl)
        assertEquals("12.5", page.currentChapter?.label)
        assertEquals(2, page.chapters.size)
        assertTrue(page.isChapterListComplete)
    }

    @Test
    fun malformedCssAndRegexAreRejectedBeforeManualParsing() {
        val configuration = ManualSiteConfiguration(
            normalizedHost = "example.test",
            titleRule = ExtractionRule(selector = "["),
            chapterNumberRule = ExtractionRule(selector = ".chapter", regex = "("),
            chapterLinkRule = ExtractionRule(selector = "a[href]"),
        )

        val errors = ManualConfigurationValidator.validate(configuration)

        assertTrue(errors.any { it.contains("CSS selector") || it.contains("seletor CSS") })
        assertTrue(errors.any { it.contains("regular expression") || it.contains("expressão regular") })
    }

    @Test
    fun manualConfigurationCodecRoundTripsAndReportsInvalidSavedData() {
        val configuration = ManualSiteConfiguration(
            normalizedHost = "example.test",
            titleRule = ExtractionRule(".title"),
            chapterNumberRule = ExtractionRule(".number", regex = "(\\d+)"),
            chapterLinkRule = ExtractionRule("a.chapter", "href"),
        )

        val decoded = ManualSiteConfigurationCodec.decode(
            ManualSiteConfigurationCodec.encode(configuration),
        )

        assertTrue(decoded is ManualConfigurationDecodeResult.Success)
        assertEquals(
            configuration,
            (decoded as ManualConfigurationDecodeResult.Success).configuration,
        )
        assertTrue(
            ManualSiteConfigurationCodec.decode("{").let {
                it is ManualConfigurationDecodeResult.Failure
            },
        )
    }

    @Test
    fun normalizesInternationalizedHostsAndRejectsCredentialUrls() {
        assertEquals(
            "xn--bcher-kva.example",
            DomainNormalizer.normalizedHostFromUrl("https://Bücher.example./manga"),
        )
        assertEquals(null, DomainNormalizer.normalizedHostFromUrl("https://user:pass@example.test/manga"))
        assertEquals(null, DomainNormalizer.normalizedHostFromUrl("file:///etc/passwd"))
    }
}
