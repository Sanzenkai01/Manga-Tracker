package com.sanzenkai.mangatracker.data

import com.sanzenkai.mangatracker.core.data.network.HtmlDocumentFetcher
import com.sanzenkai.mangatracker.core.data.network.HtmlFetchFailureCode
import com.sanzenkai.mangatracker.core.data.network.HtmlFetchResult
import com.sanzenkai.mangatracker.core.data.network.JsonDocumentFetcher
import com.sanzenkai.mangatracker.core.data.network.JsonFetchResult
import com.sanzenkai.mangatracker.core.parser.api.ParserDocument
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImperioDaBritanniaDocumentFetcherTest {
    @Test
    fun fetchesThePublicChapterApiForSeriesAndChapterUrls() = runBlocking {
        var apiUrl: String? = null
        val fetcher = ImperioDaBritanniaDocumentFetcher(
            htmlFetcher = htmlFetcher(),
            jsonFetcher = JsonDocumentFetcher { url ->
                apiUrl = url
                JsonFetchResult.Success("""{"sucesso":true}""")
            },
        )

        val result = fetcher.fetch(
            "https://imperiodabritannia.net/manga/eu-me-tornei-um-homem-casado-em-outro-mundo/capitulo/34",
        )

        assertTrue(result is HtmlFetchResult.Success)
        val document = (result as HtmlFetchResult.Success).document
        assertEquals(
            "https://api.imperiodabritannia.net/api/obras/by-slug/eu-me-tornei-um-homem-casado-em-outro-mundo",
            apiUrl,
        )
        assertEquals("application/json", document.contentType)
        assertEquals("https://imperiodabritannia.net/manga/eu-me-tornei-um-homem-casado-em-outro-mundo/capitulo/34", document.pageUrl)
    }

    @Test
    fun keepsChapterLinkRegistrationAvailableWhenTheApiIsUnavailable() = runBlocking {
        val fetcher = ImperioDaBritanniaDocumentFetcher(
            htmlFetcher = htmlFetcher(),
            jsonFetcher = JsonDocumentFetcher {
                JsonFetchResult.Failure("API unavailable", HtmlFetchFailureCode.SERVER_ERROR)
            },
        )

        val result = fetcher.fetch(
            "https://imperiodabritannia.net/manga/eu-me-tornei-um-homem-casado-em-outro-mundo/capitulo/34",
        )

        assertTrue(result is HtmlFetchResult.Success)
        assertEquals("text/html", (result as HtmlFetchResult.Success).document.contentType)
    }

    @Test
    fun reportsApiFailureForASeriesUrlInsteadOfReturningAnEmptySuccess() = runBlocking {
        val fetcher = ImperioDaBritanniaDocumentFetcher(
            htmlFetcher = htmlFetcher(),
            jsonFetcher = JsonDocumentFetcher {
                JsonFetchResult.Failure("API unavailable", HtmlFetchFailureCode.NETWORK_ERROR)
            },
        )

        val result = fetcher.fetch(
            "https://imperiodabritannia.net/manga/eu-me-tornei-um-homem-casado-em-outro-mundo/",
        )

        assertTrue(result is HtmlFetchResult.Failure)
        assertEquals(
            HtmlFetchFailureCode.NETWORK_ERROR,
            (result as HtmlFetchResult.Failure).code,
        )
    }

    private fun htmlFetcher() = HtmlDocumentFetcher { url ->
        HtmlFetchResult.Success(
            ParserDocument(
                pageUrl = url,
                contentType = "text/html",
                html = "<html><title>Imperio da Britannia</title></html>",
            ),
        )
    }
}
