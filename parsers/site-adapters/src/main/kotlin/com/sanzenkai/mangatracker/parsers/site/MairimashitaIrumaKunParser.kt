package com.sanzenkai.mangatracker.parsers.site

import com.sanzenkai.mangatracker.core.localization.localizedMessage
import com.sanzenkai.mangatracker.core.parser.api.ParserDocument
import com.sanzenkai.mangatracker.core.parser.api.ParserMatch
import com.sanzenkai.mangatracker.core.parser.api.SiteParseIssue
import com.sanzenkai.mangatracker.core.parser.api.SiteParseResult
import com.sanzenkai.mangatracker.core.parser.api.SiteParser
import com.sanzenkai.mangatracker.core.parser.engine.DomainNormalizer
import com.sanzenkai.mangatracker.core.parser.engine.PageMetadataExtractor
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class MairimashitaIrumaKunParser : SiteParser {
    override val id: String = "mairimashita-iruma-kun"

    override fun match(document: ParserDocument): ParserMatch? {
        val uri = try {
            URI(document.pageUrl)
        } catch (_: URISyntaxException) {
            return null
        }
        val host = DomainNormalizer.normalizedHostFromUrl(document.pageUrl)
        if (host !in SUPPORTED_HOSTS || uri.path.orEmpty().trimEnd('/').isNotEmpty()) {
            return null
        }

        return ParserMatch(
            confidence = 100,
            evidence = localizedMessage(
                "The Mairimashita Iruma-Kun chapter list is available on its series page.",
                "A lista de capítulos do Mairimashita Iruma-Kun está disponível na página da série.",
            ),
        )
    }

    override fun parse(document: ParserDocument): SiteParseResult {
        val metadataPage = try {
            PageMetadataExtractor.extract(document)
        } catch (_: IllegalArgumentException) {
            return SiteParseResult.Failure(
                reason = localizedMessage(
                    "The Mairimashita Iruma-Kun page is invalid or too large.",
                    "A página do Mairimashita Iruma-Kun é inválida ou muito grande.",
                ),
                issue = SiteParseIssue.INVALID_HTML,
            )
        }
        val seriesUrl = seriesPageUrl(document.pageUrl)
            ?: return SiteParseResult.Failure(
                reason = localizedMessage(
                    "The Mairimashita Iruma-Kun series URL is invalid.",
                    "O link da série Mairimashita Iruma-Kun é inválido.",
                ),
                issue = SiteParseIssue.INVALID_HTML,
            )
        val parsed = Jsoup.parse(document.html, document.pageUrl)
        val title = parsed.selectFirst("h1")?.text()?.trim()?.takeIf(String::isNotEmpty)
            ?: metadataPage.title
        val coverUrl = title?.let { findSeriesCover(parsed, it, document.pageUrl) }
            ?: metadataPage.originalCoverUrl
        val latestChapterKnown = metadataPage.isLatestChapterKnown &&
            metadataPage.currentChapter != null
        val page = metadataPage.copy(
            title = title,
            originalCoverUrl = coverUrl,
            seriesUrl = seriesUrl,
            currentChapter = metadataPage.currentChapter.takeIf { latestChapterKnown },
            chapters = metadataPage.chapters,
            isChapterListComplete = metadataPage.isChapterListComplete,
            isLatestChapterKnown = latestChapterKnown,
        )

        return if (page.title != null && page.isLatestChapterKnown) {
            SiteParseResult.Success(page)
        } else {
            SiteParseResult.RequiresConfiguration(
                reason = localizedMessage(
                    "The series page did not expose a verifiable recent chapter list.",
                    "A página da série não apresentou uma lista recente de capítulos que pudesse ser validada.",
                ),
                preview = page,
                issue = SiteParseIssue.INCOMPLETE_CHAPTER_LIST,
            )
        }
    }

    private fun findSeriesCover(
        document: org.jsoup.nodes.Document,
        title: String,
        pageUrl: String,
    ): String? {
        val normalizedTitle = normalizeImageLabel(title)
        if (normalizedTitle.isEmpty()) return null

        return document.select("img[src], img[data-src], img[data-lazy-src], img[data-original], img[srcset]")
            .firstNotNullOfOrNull { image ->
                val imageLabel = normalizeImageLabel(
                    listOf(image.attr("alt"), image.attr("title")).joinToString(" "),
                )
                if (!imageLabel.contains(normalizedTitle)) {
                    null
                } else {
                    image.imageUrls()
                        .firstNotNullOfOrNull { PageMetadataExtractor.resolveHttpUrl(it, pageUrl) }
                }
            }
    }

    private fun Element.imageUrls(): Sequence<String> = sequenceOf(
        attr("src"),
        attr("data-src"),
        attr("data-lazy-src"),
        attr("data-original"),
        attr("srcset").substringBefore(',').trim().substringBefore(' '),
    ).filter(String::isNotBlank)

    private fun normalizeImageLabel(value: String): String =
        value.lowercase(Locale.ROOT).replace(NON_ALPHANUMERIC_PATTERN, " ").trim()

    private fun seriesPageUrl(pageUrl: String): String? = try {
        val uri = URI(pageUrl)
        if (uri.scheme !in setOf("http", "https") || uri.host == null) {
            null
        } else {
            URI(uri.scheme, null, uri.host, uri.port, "/", null, null).toASCIIString()
        }
    } catch (_: URISyntaxException) {
        null
    }

    private companion object {
        val SUPPORTED_HOSTS = setOf(
            "mairimashitairuma-kun.com",
            "www.mairimashitairuma-kun.com",
        )
        val NON_ALPHANUMERIC_PATTERN = Regex("[^a-z0-9]+")
    }
}
