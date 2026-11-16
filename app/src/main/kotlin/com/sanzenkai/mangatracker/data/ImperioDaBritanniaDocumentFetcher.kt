package com.sanzenkai.mangatracker.data

import com.sanzenkai.mangatracker.core.data.network.HtmlDocumentFetcher
import com.sanzenkai.mangatracker.core.data.network.HtmlFetchResult
import com.sanzenkai.mangatracker.core.data.network.JsonDocumentFetcher
import com.sanzenkai.mangatracker.core.data.network.JsonFetchResult
import com.sanzenkai.mangatracker.core.parser.api.ParserDocument
import com.sanzenkai.mangatracker.parsers.site.ImperioDaBritanniaParser
import java.net.URI
import java.net.URISyntaxException

class ImperioDaBritanniaDocumentFetcher(
    private val htmlFetcher: HtmlDocumentFetcher,
    private val jsonFetcher: JsonDocumentFetcher,
) : HtmlDocumentFetcher {
    override suspend fun fetch(rawUrl: String): HtmlFetchResult {
        val htmlResult = htmlFetcher.fetch(rawUrl)
        if (htmlResult !is HtmlFetchResult.Success) return htmlResult
        val document = htmlResult.document
        val apiUrl = ImperioDaBritanniaParser.apiUrlForPage(document.pageUrl) ?: return htmlResult

        return when (val apiResult = jsonFetcher.fetch(apiUrl)) {
            is JsonFetchResult.Success -> HtmlFetchResult.Success(
                ParserDocument(
                    pageUrl = document.pageUrl,
                    contentType = "application/json",
                    html = apiResult.json,
                    requestedUrl = document.requestedUrl,
                ),
            )
            is JsonFetchResult.Failure -> {
                if (document.isChapterPage()) {
                    htmlResult
                } else {
                    HtmlFetchResult.Failure(apiResult.reason, apiResult.code)
                }
            }
        }
    }

    private fun ParserDocument.isChapterPage(): Boolean =
        try {
            URI(pageUrl).rawPath.orEmpty().contains("/capitulo/", ignoreCase = true)
        } catch (_: URISyntaxException) {
            false
        }
}
