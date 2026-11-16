package com.sanzenkai.mangatracker.data

import com.sanzenkai.mangatracker.core.data.network.HtmlDocumentFetcher
import com.sanzenkai.mangatracker.core.data.network.HtmlFetchResult
import com.sanzenkai.mangatracker.core.parser.engine.DomainNormalizer
import java.net.URI
import java.net.URISyntaxException

class MairimashitaIrumaKunDocumentFetcher(
    private val htmlFetcher: HtmlDocumentFetcher,
) : HtmlDocumentFetcher {
    override suspend fun fetch(rawUrl: String): HtmlFetchResult {
        val seriesUrl = seriesPageUrlForChapter(rawUrl)
            ?: return htmlFetcher.fetch(rawUrl)
        return when (val result = htmlFetcher.fetch(seriesUrl)) {
            is HtmlFetchResult.Success -> HtmlFetchResult.Success(
                result.document.copy(requestedUrl = rawUrl),
            )
            is HtmlFetchResult.Failure -> result
        }
    }

    private fun seriesPageUrlForChapter(rawUrl: String): String? {
        val uri = try {
            URI(rawUrl)
        } catch (_: URISyntaxException) {
            return null
        }
        val host = DomainNormalizer.normalizedHostFromUrl(rawUrl)
        if (host !in SUPPORTED_HOSTS || !CHAPTER_PATH_PATTERN.matches(uri.path.orEmpty())) {
            return null
        }
        if (uri.scheme !in setOf("http", "https") || uri.rawUserInfo != null) return null

        return try {
            URI(uri.scheme, null, uri.host, uri.port, "/", null, null).toASCIIString()
        } catch (_: URISyntaxException) {
            null
        }
    }

    private companion object {
        val SUPPORTED_HOSTS = setOf(
            "mairimashitairuma-kun.com",
            "www.mairimashitairuma-kun.com",
        )
        val CHAPTER_PATH_PATTERN = Regex(
            """(?i)/manga/mairimashita-iruma-kun-chapter-[0-9]+(?:-[0-9]+)?[a-z]?/?""",
        )
    }
}
