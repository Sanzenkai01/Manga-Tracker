package com.sanzenkai.mangatracker.parsers.site

import com.sanzenkai.mangatracker.core.localization.localizedMessage
import com.sanzenkai.mangatracker.core.parser.api.ParsedChapter
import com.sanzenkai.mangatracker.core.parser.api.ParserDocument
import com.sanzenkai.mangatracker.core.parser.api.ParserMatch
import com.sanzenkai.mangatracker.core.parser.api.ParsedSitePage
import com.sanzenkai.mangatracker.core.parser.api.SiteParseIssue
import com.sanzenkai.mangatracker.core.parser.api.SiteParseResult
import com.sanzenkai.mangatracker.core.parser.api.SiteParser
import com.sanzenkai.mangatracker.core.parser.engine.DomainNormalizer
import com.sanzenkai.mangatracker.core.parser.engine.PageMetadataExtractor
import java.math.BigDecimal
import java.net.URI
import java.net.URISyntaxException
import org.json.JSONException
import org.json.JSONObject

class ImperioDaBritanniaParser : SiteParser {
    override val id: String = "imperio-da-britannia-public-api"

    override fun match(document: ParserDocument): ParserMatch? {
        val host = DomainNormalizer.normalizedHostFromUrl(document.pageUrl)
        if (host != "imperiodabritannia.net" && host != "www.imperiodabritannia.net") return null
        if (mangaPath(document.pageUrl) == null) return null

        return ParserMatch(
            confidence = 95,
            evidence = localizedMessage(
                "Imperio da Britannia provides chapter data through its public API.",
                "O Império da Britannia fornece os capítulos por uma API pública.",
            ),
        )
    }

    override fun parse(document: ParserDocument): SiteParseResult {
        if (document.contentType?.substringBefore(';')?.equals("application/json", ignoreCase = true) == true) {
            return parseApiResponse(document)
        }
        val metadataPage = try {
            PageMetadataExtractor.extract(document)
        } catch (_: IllegalArgumentException) {
            return SiteParseResult.Failure(
                reason = localizedMessage(
                    "The Imperio da Britannia page is invalid or too large.",
                    "A página do Império da Britannia é inválida ou muito grande.",
                ),
                issue = SiteParseIssue.INVALID_HTML,
            )
        }
        val path = mangaPath(document.pageUrl)
            ?: return SiteParseResult.RequiresConfiguration(
                reason = localizedMessage(
                    "The manga page address could not be recognized.",
                    "Não foi possível reconhecer o endereço da página do mangá.",
                ),
                issue = SiteParseIssue.NO_MATCH,
            )
        val seriesUrl = PageMetadataExtractor.resolveHttpUrl(
            "/manga/${path.identifier}/",
            document.pageUrl,
        )
        val chapter = path.chapter?.let { number ->
            val label = "Capítulo $number"
            ParsedChapter(
                sourceKey = document.pageUrl,
                label = label,
                url = document.pageUrl,
                publishedAtEpochMillis = metadataPage.currentChapter?.publishedAtEpochMillis,
            )
        } ?: metadataPage.currentChapter
        val page = metadataPage.copy(
            title = metadataPage.title
                ?.replace(SITE_NAME_SUFFIX, "")
                ?.trim()
                ?.takeIf(String::isNotEmpty),
            seriesUrl = seriesUrl,
            currentChapter = chapter,
            chapters = emptyList(),
            isChapterListComplete = false,
            isLatestChapterKnown = false,
            warnings = listOf(
                localizedMessage(
                    "Chapter content is rendered in the browser and is not available in the received page.",
                    "Os capítulos são carregados no navegador e não estão disponíveis na página recebida.",
                ),
            ),
        )
        val reason = if (chapter == null) {
            localizedMessage(
                "This manga page does not include a chapter link. Enter the URL of a specific chapter to add it.",
                "Esta página do mangá não inclui um capítulo. Informe o link de um capítulo específico para adicioná-lo.",
            )
        } else {
            localizedMessage(
                "This site loads chapter data in the browser. The linked chapter can be saved, but automatic updates are unavailable.",
                "Este site carrega os capítulos no navegador. O capítulo informado pode ser salvo, mas a atualização automática não está disponível.",
            )
        }

        return SiteParseResult.RequiresConfiguration(
            reason = reason,
            preview = page,
            issue = SiteParseIssue.UNSUPPORTED_DYNAMIC_CONTENT,
        )
    }

    private fun parseApiResponse(document: ParserDocument): SiteParseResult {
        val root = try {
            JSONObject(document.html)
        } catch (_: JSONException) {
            return invalidApiResponse()
        }
        if (!root.optBoolean("sucesso")) return invalidApiResponse()
        val work = root.optJSONObject("obra") ?: return invalidApiResponse()
        val mangaPath = mangaPath(document.pageUrl) ?: return invalidApiResponse()
        val title = work.readString("nome") ?: return invalidApiResponse()
        val workId = work.readString("id")?.toLongOrNull()?.takeIf { it > 0L }
            ?: return invalidApiResponse()
        val requestedId = mangaPath.identifier.toLongOrNull()
        if (requestedId != null) {
            if (workId != requestedId) return invalidApiResponse()
        } else {
            val responseSlug = work.readString("slug") ?: return invalidApiResponse()
            if (!responseSlug.equals(mangaPath.identifier, ignoreCase = true)) return invalidApiResponse()
        }

        val chapterItems = work.optJSONArray("capitulos") ?: return invalidApiResponse()
        if (chapterItems.length() !in 1..MAX_API_CHAPTERS) return invalidApiResponse()
        val parsed = mutableListOf<Pair<BigDecimal, ParsedChapter>>()
        val chapterNumbers = mutableSetOf<BigDecimal>()
        val chapterKeys = mutableSetOf<String>()
        for (index in 0 until chapterItems.length()) {
            val item = chapterItems.optJSONObject(index) ?: return invalidApiResponse()
            val rawChapterWorkId = item.readString("obra_id")
            val chapterWorkId = rawChapterWorkId?.toLongOrNull()
                ?: if (rawChapterWorkId == null) workId else return invalidApiResponse()
            if (chapterWorkId != workId) return invalidApiResponse()
            val number = item.readString("numero")
                ?.takeIf(CHAPTER_NUMBER_PATTERN::matches)
                ?.replace(',', '.')
                ?.toBigDecimalOrNull()
                ?.stripTrailingZeros()
                ?.takeIf { it >= BigDecimal.ZERO }
                ?: return invalidApiResponse()
            if (!chapterNumbers.add(number)) return invalidApiResponse()
            val rawChapterId = item.readString("id")
            val chapterId = when {
                rawChapterId == null -> -1L
                else -> rawChapterId.toLongOrNull()
                    ?.takeIf { it > 0L }
                    ?: return invalidApiResponse()
            }
            val normalizedNumber = number.toPlainString()
            val sourceKey = if (chapterId > 0) "imperio-chapter:$chapterId" else {
                "imperio:${mangaPath.identifier}:$normalizedNumber"
            }
            if (!chapterKeys.add(sourceKey)) return invalidApiResponse()
            val chapterUrl = PageMetadataExtractor.resolveHttpUrl(
                "/manga/${mangaPath.identifier}/capitulo/$normalizedNumber",
                document.pageUrl,
            ) ?: return invalidApiResponse()
            val chapterLabel = item.readString("nome") ?: localizedMessage(
                "Chapter $normalizedNumber",
                "Capítulo $normalizedNumber",
            )
            parsed += number to ParsedChapter(
                sourceKey = sourceKey,
                label = chapterLabel,
                url = chapterUrl,
                publishedAtEpochMillis = item.readString("criado_em")
                    ?.let(PageMetadataExtractor::parseEpochMillis),
            )
        }

        val chapters = parsed.sortedByDescending { it.first }.map { it.second }
        val declaredCount = work.optInt("total_capitulos", -1)
        val isComplete = declaredCount > 0 && declaredCount == chapters.size
        val seriesUrl = PageMetadataExtractor.resolveHttpUrl(
            "/manga/${mangaPath.identifier}/",
            document.pageUrl,
        ) ?: return invalidApiResponse()
        val latestChapter = chapters.firstOrNull() ?: return invalidApiResponse()

        return SiteParseResult.Success(
            ParsedSitePage(
                title = title,
                originalCoverUrl = work.readString("imagem")?.let(::coverUrl),
                seriesUrl = seriesUrl,
                currentChapter = latestChapter,
                chapters = chapters,
                isChapterListComplete = isComplete,
                metadata = mapOf(
                    "api:work_id" to workId.toString(),
                    "api:returned_chapters" to chapters.size.toString(),
                ),
                isLatestChapterKnown = true,
            ),
        )
    }

    private fun coverUrl(rawImage: String): String? {
        val candidate = if (rawImage.startsWith("https://", ignoreCase = true)) {
            rawImage
        } else {
            "https://$CDN_HOST/${rawImage.trimStart('/')}"
        }
        return try {
            URI(candidate).takeIf {
                it.scheme.equals("https", ignoreCase = true) &&
                    it.host.equals(CDN_HOST, ignoreCase = true) &&
                    it.userInfo == null &&
                    it.path.orEmpty().startsWith("/obras/")
            }?.toASCIIString()
        } catch (_: URISyntaxException) {
            null
        }
    }

    private fun invalidApiResponse() = SiteParseResult.Failure(
        reason = localizedMessage(
            "The chapter API response was invalid or did not match this manga.",
            "A resposta da lista de capítulos é inválida ou não corresponde a este mangá.",
        ),
        issue = SiteParseIssue.INVALID_HTML,
    )

    private fun mangaPath(url: String): MangaPath? {
        val path = try {
            URI(url).rawPath?.trimEnd('/') ?: return null
        } catch (_: URISyntaxException) {
            return null
        }
        val match = MANGA_PATH_PATTERN.matchEntire(path) ?: return null
        return MangaPath(
            identifier = match.groupValues[1],
            chapter = match.groupValues[2].takeIf(String::isNotEmpty),
        )
    }

    private fun JSONObject.readString(key: String): String? {
        val value = opt(key)
        return when (value) {
            is String -> value.trim().takeIf(String::isNotEmpty)
            is Number -> value.toString()
            else -> null
        }
    }

    private data class MangaPath(
        val identifier: String,
        val chapter: String?,
    )

    companion object {
        const val CDN_HOST = "cdn.imperiodabritannia.net"
        const val MAX_API_CHAPTERS = 2_000
        val CHAPTER_NUMBER_PATTERN = Regex("""[0-9]+(?:[.,][0-9]+)?""")
        val API_MANGA_PATH_PATTERN = Regex(
            pattern = "^/manga/([a-z0-9-]+)(?:/capitulo/[0-9]+(?:[.,][0-9]+)?[a-z]?)?/?$",
            option = RegexOption.IGNORE_CASE,
        )
        val MANGA_PATH_PATTERN = Regex(
            pattern = "^/manga/([^/]+)(?:/capitulo/([0-9]+(?:[.,][0-9]+)?[a-z]?))?$",
            option = RegexOption.IGNORE_CASE,
        )
        val SITE_NAME_SUFFIX = Regex("(?i)\\s+-\\s+Imperio da Britannia\\s*$")

        fun apiUrlForPage(pageUrl: String): String? {
            val host = DomainNormalizer.normalizedHostFromUrl(pageUrl)
            if (host != "imperiodabritannia.net" && host != "www.imperiodabritannia.net") {
                return null
            }
            val path = try {
                URI(pageUrl).rawPath ?: return null
            } catch (_: URISyntaxException) {
                return null
            }
            val identifier = API_MANGA_PATH_PATTERN.matchEntire(path)?.groupValues?.get(1) ?: return null
            val workId = identifier.toLongOrNull()
            return if (workId != null) {
                if (workId <= 0L) return null
                "https://api.imperiodabritannia.net/api/obras/$workId"
            } else {
                "https://api.imperiodabritannia.net/api/obras/by-slug/$identifier"
            }
        }
    }
}
