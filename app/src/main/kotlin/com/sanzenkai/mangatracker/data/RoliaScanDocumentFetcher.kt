package com.sanzenkai.mangatracker.data

import com.sanzenkai.mangatracker.core.data.network.HtmlDocumentFetcher
import com.sanzenkai.mangatracker.core.data.network.HtmlFetchFailureCode
import com.sanzenkai.mangatracker.core.data.network.HtmlFetchResult
import com.sanzenkai.mangatracker.core.data.network.JsonDocumentFetcher
import com.sanzenkai.mangatracker.core.data.network.JsonFetchResult
import com.sanzenkai.mangatracker.core.localization.localizedMessage
import com.sanzenkai.mangatracker.core.parser.api.ParserDocument
import com.sanzenkai.mangatracker.core.parser.engine.DomainNormalizer
import com.sanzenkai.mangatracker.parsers.site.RoliaScanParser
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

class RoliaScanDocumentFetcher(
    private val htmlFetcher: HtmlDocumentFetcher,
    private val jsonFetcher: JsonDocumentFetcher,
) : HtmlDocumentFetcher {
    override suspend fun fetch(rawUrl: String): HtmlFetchResult {
        val route = routeFor(rawUrl) ?: return htmlFetcher.fetch(rawUrl)
        val seriesUrl = seriesUrlFor(rawUrl, route.slug) ?: return htmlFetcher.fetch(rawUrl)
        val fetched = htmlFetcher.fetch(seriesUrl)
        if (fetched !is HtmlFetchResult.Success) return fetched
        val document = fetched.document
        val actualRoute = routeFor(document.pageUrl)
        if (actualRoute == null ||
            actualRoute.slug != route.slug ||
            actualRoute.host !in SUPPORTED_HOSTS
        ) {
            return HtmlFetchResult.Failure(
                reason = localizedMessage(
                    "The RoliaScan series page redirected to an unexpected domain or manga.",
                    "A página da série RoliaScan redirecionou para um domínio ou mangá inesperado.",
                ),
                code = HtmlFetchFailureCode.BLOCKED_HOST,
            )
        }

        val mangaId = RoliaScanParser.mangaIdFrom(document)
            ?: return HtmlFetchResult.Success(
                document.withSupplementalData(
                    requestedUrl = rawUrl,
                    values = mapOf(
                        RoliaScanParser.CHAPTERS_FAILURE_KEY to localizedMessage(
                            "The series page did not expose a public manga identifier.",
                            "A página da série não informou um identificador público do mangá.",
                        ),
                    ),
                ),
            )
        val requestUrl =
            "https://roliascan.com/auth/manga-chapters?manga_id=$mangaId&offset=0&limit=${RoliaScanParser.MAX_CHAPTERS_PER_RESPONSE}&order=DESC"
        return when (val result = jsonFetcher.fetch(requestUrl)) {
            is JsonFetchResult.Success -> HtmlFetchResult.Success(
                document.withSupplementalData(
                    requestedUrl = rawUrl,
                    values = mapOf(
                        RoliaScanParser.MANGA_ID_KEY to mangaId,
                        RoliaScanParser.CHAPTERS_RESPONSE_KEY to result.json,
                    ),
                ),
            )
            is JsonFetchResult.Failure -> HtmlFetchResult.Success(
                document.withSupplementalData(
                    requestedUrl = rawUrl,
                    values = mapOf(
                        RoliaScanParser.MANGA_ID_KEY to mangaId,
                        RoliaScanParser.CHAPTERS_FAILURE_KEY to result.reason.take(MAX_FAILURE_REASON_LENGTH),
                    ),
                ),
            )
        }
    }

    private fun ParserDocument.withSupplementalData(
        requestedUrl: String,
        values: Map<String, String>,
    ) = copy(
        requestedUrl = requestedUrl,
        supplementalData = supplementalData + values,
    )

    private fun routeFor(rawUrl: String): MangaRoute? {
        return try {
            val uri = URI(rawUrl)
            val host = DomainNormalizer.normalizedHostFromUrl(rawUrl) ?: return null
            if (host !in SUPPORTED_HOSTS ||
                uri.scheme !in setOf("http", "https") ||
                uri.rawUserInfo != null
            ) {
                return null
            }
            val slug = SERIES_PATH_PATTERN.matchEntire(uri.rawPath.orEmpty())?.groupValues?.get(1)
                ?: CHAPTER_PATH_PATTERN.matchEntire(uri.rawPath.orEmpty())?.groupValues?.get(1)
                ?: return null
            MangaRoute(host, slug.lowercase(Locale.ROOT))
        } catch (_: URISyntaxException) {
            null
        }
    }

    private fun seriesUrlFor(rawUrl: String, slug: String): String? = try {
        val uri = URI(rawUrl)
        URI(uri.scheme, null, uri.host, uri.port, "/manga/$slug/", null, null).toASCIIString()
    } catch (_: URISyntaxException) {
        null
    }

    private data class MangaRoute(
        val host: String,
        val slug: String,
    )

    private companion object {
        const val MAX_FAILURE_REASON_LENGTH = 300
        val SUPPORTED_HOSTS = setOf("roliascan.com", "www.roliascan.com")
        val SERIES_PATH_PATTERN = Regex("""(?i)^/manga/([a-z0-9-]+)/?$""")
        val CHAPTER_PATH_PATTERN =
            Regex("""(?i)^/read/([a-z0-9-]+)/ch[0-9]+(?:\.[0-9]+)?-[0-9]+/?$""")
    }
}
