package com.sanzenkai.mangatracker.parsers.site

import com.sanzenkai.mangatracker.core.localization.localizedMessage
import com.sanzenkai.mangatracker.core.parser.api.ParsedChapter
import com.sanzenkai.mangatracker.core.parser.api.ParsedSitePage
import com.sanzenkai.mangatracker.core.parser.api.ParserDocument
import com.sanzenkai.mangatracker.core.parser.api.ParserMatch
import com.sanzenkai.mangatracker.core.parser.api.SiteParseIssue
import com.sanzenkai.mangatracker.core.parser.api.SiteParseResult
import com.sanzenkai.mangatracker.core.parser.api.SiteParser
import com.sanzenkai.mangatracker.core.parser.engine.DomainNormalizer
import com.sanzenkai.mangatracker.core.parser.engine.PageMetadataExtractor
import java.math.BigDecimal
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.jsoup.Jsoup

class RoliaScanParser : SiteParser {
    override val id: String = "roliascan-public-chapters"

    override fun match(document: ParserDocument): ParserMatch? {
        val route = routeFor(document.pageUrl) ?: routeFor(document.requestedUrl) ?: return null
        if (route.host !in SUPPORTED_HOSTS) return null
        return ParserMatch(
            confidence = 100,
            evidence = localizedMessage(
                "RoliaScan exposes a public, paginated chapter list.",
                "A RoliaScan disponibiliza uma lista pública e paginada de capítulos.",
            ),
        )
    }

    override fun parse(document: ParserDocument): SiteParseResult {
        val route = routeFor(document.pageUrl) ?: routeFor(document.requestedUrl)
            ?: return SiteParseResult.RequiresConfiguration(
                reason = localizedMessage(
                    "The RoliaScan manga or chapter URL could not be recognized.",
                    "Não foi possível reconhecer o endereço do mangá ou capítulo da RoliaScan.",
                ),
                issue = SiteParseIssue.NO_MATCH,
            )
        if (route.host !in SUPPORTED_HOSTS) {
            return SiteParseResult.RequiresConfiguration(
                reason = localizedMessage(
                    "This RoliaScan page is not hosted on an allowed domain.",
                    "Esta página da RoliaScan não está em um domínio permitido.",
                ),
                issue = SiteParseIssue.NO_MATCH,
            )
        }

        val html = try {
            Jsoup.parse(document.html, document.pageUrl)
        } catch (_: IllegalArgumentException) {
            return invalidPage()
        }
        val metadata = try {
            PageMetadataExtractor.extract(document)
        } catch (_: IllegalArgumentException) {
            return invalidPage()
        }
        val seriesUrl = seriesUrlFor(document.pageUrl, route.slug)
            ?: return invalidPage()
        val title = html.selectFirst("h1")?.text()?.trim()?.takeIf(String::isNotEmpty)
            ?: metadata.title
        val coverCandidate = if (chapterFromUrl(document.pageUrl) != null) {
            breadcrumbCoverUrl(html, seriesUrl) ?: metadata.originalCoverUrl
        } else {
            metadata.originalCoverUrl ?: breadcrumbCoverUrl(html, seriesUrl)
        }
        val coverUrl = coverCandidate
            ?.let { PageMetadataExtractor.resolveHttpUrl(it, document.pageUrl) }
            ?.takeIf { DomainNormalizer.normalizedHostFromUrl(it) == route.host }
        val mangaId = document.supplementalData[MANGA_ID_KEY]
            ?: html.selectFirst(".chapter-list[data-manga-id]")?.attr("data-manga-id")
                ?.takeIf(String::isNotBlank)
        val basePage = metadata.copy(
            title = title,
            originalCoverUrl = coverUrl,
            seriesUrl = seriesUrl,
            currentChapter = chapterFromUrl(document.requestedUrl)
                ?: chapterFromUrl(document.pageUrl)
                ?: metadata.currentChapter,
            chapters = emptyList(),
            isChapterListComplete = false,
            isLatestChapterKnown = false,
        )
        val apiResponse = document.supplementalData[CHAPTERS_RESPONSE_KEY]
            ?: return unavailableChapterList(
                page = basePage,
                mangaId = mangaId,
                reason = document.supplementalData[CHAPTERS_FAILURE_KEY],
            )
        if (title.isNullOrBlank() || mangaId.isNullOrBlank()) {
            return unavailableChapterList(
                page = basePage,
                mangaId = mangaId,
                reason = localizedMessage(
                    "The series title or public manga identifier is missing.",
                    "O título da série ou o identificador público do mangá não foi encontrado.",
                ),
            )
        }

        val root = try {
            JSONObject(apiResponse)
        } catch (_: JSONException) {
            return unavailableChapterList(
                page = basePage,
                mangaId = mangaId,
                reason = localizedMessage(
                    "The public chapter endpoint returned invalid JSON.",
                    "O endpoint público de capítulos retornou um JSON inválido.",
                ),
            )
        }
        if (root.opt("success") != true || root.opt("has_more") !is Boolean) {
            return unavailableChapterList(
                page = basePage,
                mangaId = mangaId,
                reason = localizedMessage(
                    "The public chapter endpoint did not return a verifiable chapter list.",
                    "O endpoint público não retornou uma lista de capítulos verificável.",
                ),
            )
        }
        val chapterItems = root.optJSONArray("chapters")
            ?.takeIf { it.length() in 1..MAX_CHAPTERS_PER_RESPONSE }
            ?: return unavailableChapterList(
                page = basePage,
                mangaId = mangaId,
                reason = localizedMessage(
                    "The public chapter endpoint returned no chapters or exceeded the request limit.",
                    "O endpoint público não retornou capítulos ou excedeu o limite da solicitação.",
                ),
            )
        val chapters = parseChapters(chapterItems, route.slug)
            ?: return unavailableChapterList(
                page = basePage,
                mangaId = mangaId,
                reason = localizedMessage(
                    "The chapter list contains invalid, mismatched, or out-of-order entries.",
                    "A lista contém capítulos inválidos, incompatíveis ou fora de ordem.",
                ),
            )
        val hasMore = root.getBoolean("has_more")
        val parsedPage = basePage.copy(
            currentChapter = chapters.first(),
            chapters = chapters,
            isChapterListComplete = !hasMore,
            isLatestChapterKnown = true,
            metadata = basePage.metadata + mapOf(
                "roliascan:manga_id" to mangaId,
                "roliascan:returned_chapters" to chapters.size.toString(),
            ),
            warnings = emptyList(),
        )
        return SiteParseResult.Success(parsedPage)
    }

    private fun parseChapters(items: JSONArray, slug: String): List<ParsedChapter>? {
        val chapters = ArrayList<ParsedChapter>(items.length())
        val chapterIds = mutableSetOf<Long>()
        val chapterUrls = mutableSetOf<String>()
        var previousNumber: BigDecimal? = null

        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: return null
            val id = item.readString("id")?.toLongOrNull()?.takeIf { it > 0L } ?: return null
            if (!chapterIds.add(id)) return null
            val rawNumber = item.readString("chapter")
                ?.takeIf(CHAPTER_NUMBER_PATTERN::matches)
                ?: return null
            val number = rawNumber.toBigDecimalOrNull()
                ?.stripTrailingZeros()
                ?.takeIf { it >= BigDecimal.ZERO }
                ?: return null
            if (previousNumber != null && previousNumber < number) return null
            previousNumber = number
            val url = item.readString("url")?.takeIf { isChapterUrl(it, slug, number, id) }
                ?: return null
            if (!chapterUrls.add(url)) return null
            val label = item.readString("title")
                ?.takeIf { !it.equals("N/A", ignoreCase = true) }
                ?: localizedMessage(
                    "Chapter ${number.toPlainString()}",
                    "Capítulo ${number.toPlainString()}",
                )
            chapters += ParsedChapter(
                sourceKey = "roliascan:chapter:$id",
                label = label,
                url = url,
                publishedAtEpochMillis = item.readString("date")
                    ?.let(PageMetadataExtractor::parseEpochMillis),
            )
        }
        return chapters
    }

    private fun isChapterUrl(
        rawUrl: String,
        slug: String,
        number: BigDecimal,
        id: Long,
    ): Boolean {
        return try {
            val uri = URI(rawUrl)
            val match = CHAPTER_PATH_PATTERN.matchEntire(uri.rawPath.orEmpty()) ?: return false
            val normalizedHost = DomainNormalizer.normalizedHostFromUrl(rawUrl)
            val urlNumber = match.groupValues[2].toBigDecimalOrNull()?.stripTrailingZeros()
            uri.scheme.equals("https", ignoreCase = true) &&
                normalizedHost in SUPPORTED_HOSTS &&
                uri.rawUserInfo == null &&
                match.groupValues[1].equals(slug, ignoreCase = true) &&
                urlNumber == number &&
                match.groupValues[3].toLongOrNull() == id
        } catch (_: URISyntaxException) {
            false
        }
    }

    private fun unavailableChapterList(
        page: ParsedSitePage,
        mangaId: String?,
        reason: String?,
    ) = SiteParseResult.RequiresConfiguration(
        reason = reason ?: localizedMessage(
            "The chapter list is dynamically loaded and could not be verified. No chapter data was changed.",
            "A lista de capítulos é carregada dinamicamente e não pôde ser validada. Nenhum capítulo foi alterado.",
        ),
        preview = page.copy(
            metadata = page.metadata + listOfNotNull(mangaId?.let { "roliascan:manga_id" to it }).toMap(),
        ),
        issue = SiteParseIssue.UNSUPPORTED_DYNAMIC_CONTENT,
    )

    private fun chapterFromUrl(rawUrl: String): ParsedChapter? {
        return try {
            val uri = URI(rawUrl)
            val match = CHAPTER_PATH_PATTERN.matchEntire(uri.rawPath.orEmpty()) ?: return null
            if (DomainNormalizer.normalizedHostFromUrl(rawUrl) !in SUPPORTED_HOSTS ||
                !uri.scheme.equals("https", ignoreCase = true) ||
                uri.rawUserInfo != null
            ) {
                return null
            }
            val number = match.groupValues[2].toBigDecimalOrNull()
                ?.stripTrailingZeros()
                ?.takeIf { it >= BigDecimal.ZERO }
                ?: return null
            val chapterId = match.groupValues[3].toLongOrNull()?.takeIf { it > 0L } ?: return null
            val label = number.toPlainString()
            ParsedChapter(
                sourceKey = "roliascan:chapter:$chapterId",
                label = localizedMessage("Chapter $label", "Capítulo $label"),
                url = uri.toASCIIString(),
                publishedAtEpochMillis = null,
            )
        } catch (_: URISyntaxException) {
            null
        }
    }

    private fun breadcrumbCoverUrl(document: org.jsoup.nodes.Document, seriesUrl: String): String? {
        for (script in document.select("script[type=application/ld+json]")) {
            val root = try {
                JSONObject(script.data())
            } catch (_: JSONException) {
                continue
            }
            if (!root.typeContains("BreadcrumbList")) continue
            val items = root.optJSONArray("itemListElement") ?: continue
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val itemUrl = item.readString("item")
                    ?: item.optJSONObject("item")?.readString("url")
                    ?: continue
                if (PageMetadataExtractor.resolveHttpUrl(itemUrl, seriesUrl) != seriesUrl) continue
                val image = item.readString("image")
                    ?: item.optJSONObject("image")?.readString("url")
                if (image != null) return image
            }
        }
        return null
    }

    private fun seriesUrlFor(pageUrl: String, slug: String): String? = try {
        val uri = URI(pageUrl)
        if (uri.scheme !in setOf("http", "https") || uri.host == null || uri.rawUserInfo != null) {
            null
        } else {
            URI(uri.scheme, null, uri.host, uri.port, "/manga/$slug/", null, null).toASCIIString()
        }
    } catch (_: URISyntaxException) {
        null
    }

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

    private fun JSONObject.readString(key: String): String? =
        when (val value = opt(key)) {
            is String -> value.trim().takeIf(String::isNotEmpty)
            is Number -> value.toString()
            else -> null
        }

    private fun JSONObject.typeContains(expected: String): Boolean {
        val type = opt("@type")
        return when (type) {
            is String -> type.equals(expected, ignoreCase = true)
            is JSONArray -> (0 until type.length()).any {
                type.optString(it).equals(expected, ignoreCase = true)
            }
            else -> false
        }
    }

    private data class MangaRoute(
        val host: String,
        val slug: String,
    )

    private fun invalidPage() = SiteParseResult.Failure(
        reason = localizedMessage(
            "The RoliaScan page is invalid or exceeds the parsing size limit.",
            "A página da RoliaScan é inválida ou excede o limite de análise.",
        ),
        issue = SiteParseIssue.INVALID_HTML,
    )

    companion object {
        const val CHAPTERS_RESPONSE_KEY = "roliascan:chapters"
        const val CHAPTERS_FAILURE_KEY = "roliascan:chapters_error"
        const val MANGA_ID_KEY = "roliascan:manga_id"
        const val MAX_CHAPTERS_PER_RESPONSE = 500
        val CHAPTER_NUMBER_PATTERN = Regex("""[0-9]+(?:\.[0-9]+)?""")
        private val SUPPORTED_HOSTS = setOf("roliascan.com", "www.roliascan.com")
        private val SERIES_PATH_PATTERN = Regex("""(?i)^/manga/([a-z0-9-]+)/?$""")
        private val CHAPTER_PATH_PATTERN =
            Regex("""(?i)^/read/([a-z0-9-]+)/ch([0-9]+(?:\.[0-9]+)?)-([0-9]+)/?$""")

        fun mangaIdFrom(document: ParserDocument): String? =
            Jsoup.parse(document.html, document.pageUrl)
                .selectFirst(".chapter-list[data-manga-id]")
                ?.attr("data-manga-id")
                ?.takeIf { it.toLongOrNull()?.let { id -> id > 0L } == true }
    }
}
