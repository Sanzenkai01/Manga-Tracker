package com.sanzenkai.mangatracker.data

import com.sanzenkai.mangatracker.core.data.network.HtmlDocumentFetcher
import com.sanzenkai.mangatracker.core.data.network.HtmlFetchFailureCode
import com.sanzenkai.mangatracker.core.data.network.HtmlFetchResult
import com.sanzenkai.mangatracker.core.data.network.JsonDocumentFetcher
import com.sanzenkai.mangatracker.core.data.network.JsonFetchResult
import com.sanzenkai.mangatracker.core.parser.api.ParserDocument
import com.sanzenkai.mangatracker.parsers.site.RoliaScanParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoliaScanDocumentFetcherTest {
    @Test
    fun chapterLinksFetchSeriesAndOneBoundedPublicChapterWindow() = runBlocking {
        val htmlUrls = mutableListOf<String>()
        var jsonUrl: String? = null
        val responseJson = """{"success":true,"has_more":false,"chapters":[]}"""
        val fetcher = RoliaScanDocumentFetcher(
            htmlFetcher = HtmlDocumentFetcher { url ->
                htmlUrls += url
                HtmlFetchResult.Success(
                    ParserDocument(
                        pageUrl = url,
                        contentType = "text/html",
                        html = """<div class="chapter-list" data-manga-id="10850"></div>""",
                    ),
                )
            },
            jsonFetcher = JsonDocumentFetcher { url ->
                jsonUrl = url
                JsonFetchResult.Success(responseJson)
            },
        )

        val result = fetcher.fetch(CHAPTER_URL)

        assertTrue(result is HtmlFetchResult.Success)
        assertEquals(listOf(SERIES_URL), htmlUrls)
        assertEquals(
            "https://roliascan.com/auth/manga-chapters?manga_id=10850&offset=0&limit=500&order=DESC",
            jsonUrl,
        )
        val document = (result as HtmlFetchResult.Success).document
        assertEquals(SERIES_URL, document.pageUrl)
        assertEquals(CHAPTER_URL, document.requestedUrl)
        assertEquals(responseJson, document.supplementalData[RoliaScanParser.CHAPTERS_RESPONSE_KEY])
    }

    @Test
    fun keepsSeriesMetadataAndExposesEndpointFailureForPreview() = runBlocking {
        val fetcher = RoliaScanDocumentFetcher(
            htmlFetcher = seriesHtmlFetcher(),
            jsonFetcher = JsonDocumentFetcher {
                JsonFetchResult.Failure(
                    "The API response is not JSON.",
                    HtmlFetchFailureCode.NOT_JSON,
                )
            },
        )

        val result = fetcher.fetch(CHAPTER_URL)

        assertTrue(result is HtmlFetchResult.Success)
        val document = (result as HtmlFetchResult.Success).document
        assertEquals(CHAPTER_URL, document.requestedUrl)
        assertEquals(
            "The API response is not JSON.",
            document.supplementalData[RoliaScanParser.CHAPTERS_FAILURE_KEY],
        )
    }

    @Test
    fun doesNotUseTheRoliaEndpointForLookalikeHostsOrUnrelatedPaths() = runBlocking {
        val fetchedUrls = mutableListOf<String>()
        var apiRequested = false
        val fetcher = RoliaScanDocumentFetcher(
            htmlFetcher = HtmlDocumentFetcher { url ->
                fetchedUrls += url
                HtmlFetchResult.Success(ParserDocument(url, "text/html", "<html></html>"))
            },
            jsonFetcher = JsonDocumentFetcher {
                apiRequested = true
                JsonFetchResult.Success("{}")
            },
        )

        fetcher.fetch(
            "https://roliascan.com.example.test/read/shangri-la-frontier/ch285-342785/",
        )
        fetcher.fetch("https://roliascan.com/manga/other-manga/")

        assertEquals(
            listOf(
                "https://roliascan.com.example.test/read/shangri-la-frontier/ch285-342785/",
                "https://roliascan.com/manga/other-manga/",
            ),
            fetchedUrls,
        )
        assertTrue(!apiRequested)
    }

    private fun seriesHtmlFetcher() = HtmlDocumentFetcher { url ->
        HtmlFetchResult.Success(
            ParserDocument(
                pageUrl = url,
                contentType = "text/html",
                html = """<div class="chapter-list" data-manga-id="10850"></div>""",
            ),
        )
    }

    private companion object {
        const val SERIES_URL =
            "https://roliascan.com/manga/shangri-la-frontier-kusoge-hunter-kamige-ni-idoman-to-su/"
        const val CHAPTER_URL =
            "https://roliascan.com/read/shangri-la-frontier-kusoge-hunter-kamige-ni-idoman-to-su/ch285-342785/"
    }
}
