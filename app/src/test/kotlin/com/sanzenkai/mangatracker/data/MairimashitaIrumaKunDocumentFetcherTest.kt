package com.sanzenkai.mangatracker.data

import com.sanzenkai.mangatracker.core.data.network.HtmlDocumentFetcher
import com.sanzenkai.mangatracker.core.data.network.HtmlFetchResult
import com.sanzenkai.mangatracker.core.parser.api.ParserDocument
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MairimashitaIrumaKunDocumentFetcherTest {
    @Test
    fun chapterLinksFetchTheSeriesPageAndPreserveTheOriginalRequest() = runBlocking {
        var fetchedUrl: String? = null
        val fetcher = MairimashitaIrumaKunDocumentFetcher(
            htmlFetcher = HtmlDocumentFetcher { url ->
                fetchedUrl = url
                HtmlFetchResult.Success(
                    ParserDocument(
                        pageUrl = url,
                        contentType = "text/html",
                        html = "<html><body>Series page</body></html>",
                    ),
                )
            },
        )
        val chapterUrl =
            "https://mairimashitairuma-kun.com/manga/mairimashita-iruma-kun-chapter-465/"

        val result = fetcher.fetch(chapterUrl)

        assertTrue(result is HtmlFetchResult.Success)
        assertEquals("https://mairimashitairuma-kun.com/", fetchedUrl)
        val document = (result as HtmlFetchResult.Success).document
        assertEquals("https://mairimashitairuma-kun.com/", document.pageUrl)
        assertEquals(chapterUrl, document.requestedUrl)
    }

    @Test
    fun doesNotRewriteLookalikeHostsOrNonChapterPages() = runBlocking {
        val fetchedUrls = mutableListOf<String>()
        val fetcher = MairimashitaIrumaKunDocumentFetcher(
            htmlFetcher = HtmlDocumentFetcher { url ->
                fetchedUrls += url
                HtmlFetchResult.Success(
                    ParserDocument(
                        pageUrl = url,
                        contentType = "text/html",
                        html = "<html><body>Page</body></html>",
                    ),
                )
            },
        )
        val lookalikeUrl =
            "https://mairimashitairuma-kun.com.example.test/manga/mairimashita-iruma-kun-chapter-465/"
        val unrelatedPageUrl =
            "https://mairimashitairuma-kun.com/manga/another-series-chapter-12/"

        fetcher.fetch(lookalikeUrl)
        fetcher.fetch(unrelatedPageUrl)

        assertEquals(listOf(lookalikeUrl, unrelatedPageUrl), fetchedUrls)
    }
}
