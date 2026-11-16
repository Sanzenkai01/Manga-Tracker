package com.sanzenkai.mangatracker.core.parser.engine

import com.sanzenkai.mangatracker.core.localization.localizedMessage
import com.sanzenkai.mangatracker.core.parser.api.ParsedChapter
import com.sanzenkai.mangatracker.core.parser.api.ParserDocument
import com.sanzenkai.mangatracker.core.parser.api.ParserMatch
import com.sanzenkai.mangatracker.core.parser.api.ParsedSitePage
import com.sanzenkai.mangatracker.core.parser.api.SiteParseIssue
import com.sanzenkai.mangatracker.core.parser.api.SiteParseResult
import com.sanzenkai.mangatracker.core.parser.api.SiteParser
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.math.BigDecimal
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

class SyndicationFeedParser : SiteParser {
    override val id: String = "syndication-feed"

    override fun match(document: ParserDocument): ParserMatch? {
        if (document.html.length > MAX_DOCUMENT_CHARACTERS) return null
        val feedLink = findAlternateFeed(document)
        if (feedLink != null) {
            return ParserMatch(
                confidence = ALTERNATE_FEED_CONFIDENCE,
                evidence = localizedMessage(
                    "The series page advertises a same-site RSS or Atom feed.",
                    "A página da série anuncia um feed RSS ou Atom do mesmo site.",
                ),
            )
        }
        if (PageMetadataExtractor.wordpressHints(document).contains("WordPress")) {
            return ParserMatch(
                confidence = WORDPRESS_FEED_CONFIDENCE,
                evidence = localizedMessage(
                    "WordPress markers allow checking its conventional /feed/ path.",
                    "Indícios de WordPress permitem verificar o caminho convencional /feed/.",
                ),
            )
        }
        return null
    }

    override fun parse(document: ParserDocument): SiteParseResult {
        if (document.html.length > MAX_DOCUMENT_CHARACTERS) {
            return failure(
                "The series page or feed exceeds the parsing size limit.",
                "A página da série ou o feed excede o limite de tamanho para análise.",
            )
        }

        val page = try {
            PageMetadataExtractor.extract(document)
        } catch (_: IllegalArgumentException) {
            return failure(
                "The series page is invalid or exceeds the parsing size limit.",
                "A página da série é inválida ou excede o limite de tamanho para análise.",
            )
        }
        val feedUrl = findAlternateFeed(document)
            ?: wordpressFeedUrl(document, page.seriesUrl ?: document.pageUrl)
            ?: return incomplete(
                page,
                null,
                emptyList(),
                localizedMessage(
                    "No same-site RSS or Atom feed could be identified.",
                    "Não foi possível identificar um feed RSS ou Atom do mesmo site.",
                ),
            )

        val feedContent = document.supplementalData[supplementalDataKey(feedUrl)]
            ?: return incomplete(
                page,
                feedUrl,
                emptyList(),
                localizedMessage(
                    "A feed was identified, but its contents were not supplied for parsing.",
                    "Um feed foi identificado, mas seu conteúdo não foi fornecido para análise.",
                ),
            )
        if (feedContent.length > MAX_DOCUMENT_CHARACTERS) {
            return failure(
                "The syndication feed exceeds the parsing size limit.",
                "O feed excede o limite de tamanho para análise.",
            )
        }

        val parsedFeed = parseFeed(feedContent, feedUrl)
            ?: return failure(
                "The advertised resource is not a valid RSS or Atom feed.",
                "O recurso anunciado não é um feed RSS ou Atom válido.",
            )

        val seriesUrl = page.seriesUrl ?: document.pageUrl
        val seriesKey = PageMetadataExtractor.chapterSeriesKey(seriesUrl)
            ?: return incomplete(
                page,
                feedUrl,
                emptyList(),
                localizedMessage(
                    "The series URL could not be matched safely to feed entries.",
                    "Não foi possível associar com segurança a URL da série às entradas do feed.",
                ),
            )
        val relevant = mutableListOf<ParsedChapter>()
        var hasUnclassifiedEntry = parsedFeed.isTruncated
        parsedFeed.entries.forEach { entry ->
            val entryUrl = entry.url
            val entrySeriesKey = entryUrl?.let(PageMetadataExtractor::chapterSeriesKey)
            when {
                entryUrl == null || entrySeriesKey == null -> hasUnclassifiedEntry = true
                entrySeriesKey != seriesKey -> {
                    if (isWithinSeriesPath(entryUrl, seriesUrl)) hasUnclassifiedEntry = true
                }
                else -> {
                    val identity = entry.title?.let(PageMetadataExtractor::findChapterIdentity)
                        ?: PageMetadataExtractor.findChapterIdentity(entryUrl)
                    if (identity == null) {
                        hasUnclassifiedEntry = true
                    } else {
                        val title = entry.title.orEmpty()
                        val label = when {
                            title.isBlank() -> identity.first
                            title.contains(identity.first, ignoreCase = true) -> title
                            else -> "${identity.first} — $title"
                        }
                        relevant += ParsedChapter(
                            sourceKey = entryUrl,
                            label = label,
                            url = entryUrl,
                            publishedAtEpochMillis = entry.publishedAtEpochMillis,
                        )
                    }
                }
            }
        }

        val uniqueChapters = relevant.distinctBy(ParsedChapter::sourceKey)
        val orderedChapters = orderNewestFirst(uniqueChapters)
        if (hasUnclassifiedEntry || uniqueChapters.size != relevant.size || orderedChapters == null) {
            return incomplete(
                page = page,
                feedUrl = feedUrl,
                chapters = orderedChapters ?: uniqueChapters,
                warning = localizedMessage(
                    "The feed is empty, partially parsed, truncated, or does not establish a reliable newest-first chapter window.",
                    "O feed está vazio, foi interpretado parcialmente, foi truncado ou não confirma uma janela confiável do capítulo mais recente.",
                ),
                feedTitle = parsedFeed.title,
            )
        }

        val title = page.title ?: parsedFeed.title
        if (title.isNullOrBlank()) {
            return incomplete(
                page = page,
                feedUrl = feedUrl,
                chapters = orderedChapters,
                warning = localizedMessage(
                    "The feed contains ordered chapters but does not identify the series title.",
                    "O feed contém capítulos ordenados, mas não identifica o título da série.",
                ),
                feedTitle = parsedFeed.title,
            )
        }

        return SiteParseResult.Success(
            ParsedSitePage(
                title = title,
                originalCoverUrl = page.originalCoverUrl,
                seriesUrl = seriesUrl,
                currentChapter = orderedChapters.first(),
                chapters = orderedChapters,
                isChapterListComplete = false,
                metadata = page.metadata + mapOf(FEED_URL_METADATA_KEY to feedUrl),
                platformHints = page.platformHints,
                isLatestChapterKnown = true,
            ),
        )
    }

    companion object {
        const val FEED_URL_METADATA_KEY: String = "syndication:feed-url"

        private const val SUPPLEMENTAL_DATA_PREFIX = "syndication:feed-content:"
        private const val MAX_DOCUMENT_CHARACTERS = 2 * 1024 * 1024
        private const val ALTERNATE_FEED_CONFIDENCE = 94
        private const val WORDPRESS_FEED_CONFIDENCE = 86
        private const val MAX_FEED_ENTRIES = 800
        private val ALLOWED_MEDIA_TYPES = setOf(
            "application/rss+xml",
            "application/atom+xml",
        )

        fun supplementalDataKey(feedUrl: String): String = "$SUPPLEMENTAL_DATA_PREFIX$feedUrl"

        private fun failure(english: String, portuguese: String) =
            SiteParseResult.Failure(
                reason = localizedMessage(english, portuguese),
                issue = SiteParseIssue.INVALID_HTML,
            )

        private fun findAlternateFeed(document: ParserDocument): String? {
            val parsed = Jsoup.parse(document.html, document.pageUrl)
            val pageHost = DomainNormalizer.normalizedHostFromUrl(document.pageUrl)
                ?: return null
            return parsed.select("link[rel][type][href]").firstNotNullOfOrNull { link ->
                val relValues = link.attr("rel").split(REL_SEPARATOR)
                    .map { it.trim().lowercase(Locale.ROOT) }
                val mediaType = link.attr("type")
                    .substringBefore(';')
                    .trim()
                    .lowercase(Locale.ROOT)
                if ("alternate" !in relValues || mediaType !in ALLOWED_MEDIA_TYPES) {
                    return@firstNotNullOfOrNull null
                }
                PageMetadataExtractor.resolveHttpUrl(link.attr("href"), document.pageUrl)
                    ?.takeIf { DomainNormalizer.normalizedHostFromUrl(it) == pageHost }
            }
        }

        private fun wordpressFeedUrl(document: ParserDocument, rawSeriesUrl: String): String? {
            val host = DomainNormalizer.normalizedHostFromUrl(rawSeriesUrl) ?: return null
            if (!PageMetadataExtractor.wordpressHints(document).contains("WordPress")) return null
            return try {
                val pageUri = URI(rawSeriesUrl)
                val path = pageUri.path.orEmpty().ifEmpty { "/" }
                val feedPath = when {
                    path.trimEnd('/').endsWith("/feed", ignoreCase = true) -> path
                    path.endsWith('/') -> "${path}feed/"
                    else -> "$path/feed/"
                }
                val feedUrl = URI(
                    pageUri.scheme,
                    null,
                    pageUri.host,
                    pageUri.port,
                    feedPath,
                    null,
                    null,
                ).toASCIIString()
                feedUrl.takeIf { DomainNormalizer.normalizedHostFromUrl(it) == host }
            } catch (_: URISyntaxException) {
                null
            }
        }

        private fun parseFeed(rawFeed: String, feedUrl: String): ParsedFeed? {
            val document = Jsoup.parse(rawFeed, feedUrl, Parser.xmlParser())
            val root = document.children().firstOrNull() ?: return null
            return when (root.normalName().lowercase(Locale.ROOT)) {
                "rss" -> {
                    val channel = root.children().firstOrNull {
                        it.localName() == "channel"
                    } ?: return null
                    val items = channel.children().filter { it.localName() == "item" }
                    ParsedFeed(
                        title = channel.children().firstOrNull { it.localName() == "title" }
                            ?.text()?.trim()?.takeIf(String::isNotEmpty),
                        entries = items.take(MAX_FEED_ENTRIES).map(::parseRssEntry),
                        isTruncated = items.size > MAX_FEED_ENTRIES,
                    )
                }
                "feed" -> {
                    val entries = root.children().filter { it.localName() == "entry" }
                    ParsedFeed(
                        title = root.children().firstOrNull { it.localName() == "title" }
                            ?.text()?.trim()?.takeIf(String::isNotEmpty),
                        entries = entries.take(MAX_FEED_ENTRIES).map(::parseAtomEntry),
                        isTruncated = entries.size > MAX_FEED_ENTRIES,
                    )
                }
                else -> null
            }
        }

        private fun parseRssEntry(element: Element): FeedEntry {
            val title = element.children().firstOrNull { it.localName() == "title" }
                ?.text()?.trim()?.takeIf(String::isNotEmpty)
            val url = element.children().firstOrNull { it.localName() == "link" }
                ?.text()?.trim()?.takeIf(String::isNotEmpty)
                ?.let { PageMetadataExtractor.resolveHttpUrl(it, element.baseUri()) }
            val date = element.children().firstOrNull {
                it.localName() in setOf("pubdate", "date", "published", "updated")
            }?.text()?.trim()?.takeIf(String::isNotEmpty)
                ?.let(PageMetadataExtractor::parseEpochMillis)
            return FeedEntry(title, url, date)
        }

        private fun parseAtomEntry(element: Element): FeedEntry {
            val title = element.children().firstOrNull { it.localName() == "title" }
                ?.text()?.trim()?.takeIf(String::isNotEmpty)
            val link = element.children().firstOrNull {
                it.localName() == "link" &&
                    (it.attr("rel").isBlank() || it.attr("rel").equals("alternate", ignoreCase = true))
            }
            val url = link?.let {
                PageMetadataExtractor.resolveHttpUrl(it.attr("href"), element.baseUri())
            }
            val date = element.children().firstOrNull {
                it.localName() == "published" || it.localName() == "updated"
            }?.text()?.trim()?.takeIf(String::isNotEmpty)
                ?.let(PageMetadataExtractor::parseEpochMillis)
            return FeedEntry(title, url, date)
        }

        private fun orderNewestFirst(chapters: List<ParsedChapter>): List<ParsedChapter>? {
            if (chapters.size < MIN_ORDERED_ENTRIES) return null

            val numbers = chapters.map { chapter ->
                PageMetadataExtractor.findChapterIdentity(chapter.label)
                    ?.second
                    ?.toBigDecimalOrNull()
            }
            val orderedByNumber = if (numbers.all { it != null }) {
                val values = numbers.filterNotNull()
                if (values.map(BigDecimal::stripTrailingZeros).distinct().size == values.size &&
                    isMonotonic(values)
                ) {
                    chapters.zip(values)
                        .sortedByDescending { it.second }
                        .map { it.first }
                } else {
                    null
                }
            } else {
                null
            }

            val dates = chapters.map { it.publishedAtEpochMillis }
            val orderedByDate = if (dates.all { it != null }) {
                val values = dates.filterNotNull()
                if (values.distinct().size == values.size && isMonotonic(values)) {
                    chapters.zip(values)
                        .sortedByDescending { it.second }
                        .map { it.first }
                } else {
                    null
                }
            } else {
                null
            }

            if (orderedByNumber != null && orderedByDate != null &&
                orderedByNumber.map(ParsedChapter::sourceKey) !=
                    orderedByDate.map(ParsedChapter::sourceKey)
            ) {
                return null
            }
            return orderedByNumber ?: orderedByDate
        }

        private fun <T : Comparable<T>> isMonotonic(values: List<T>): Boolean =
            values.zipWithNext().all { (first, second) -> first > second } ||
                values.zipWithNext().all { (first, second) -> first < second }

        private fun isWithinSeriesPath(entryUrl: String, seriesUrl: String): Boolean =
            try {
                val entryUri = URI(entryUrl)
                val seriesUri = URI(seriesUrl)
                val seriesPath = seriesUri.path.orEmpty().trimEnd('/')
                DomainNormalizer.normalizedHostFromUrl(entryUrl) ==
                    DomainNormalizer.normalizedHostFromUrl(seriesUrl) &&
                    seriesPath.isNotEmpty() &&
                    seriesPath != "/" &&
                    entryUri.path.orEmpty().startsWith("$seriesPath/")
            } catch (_: URISyntaxException) {
                false
            }

        private fun Element.localName(): String =
            normalName().substringAfter(':').lowercase(Locale.ROOT)

        private data class ParsedFeed(
            val title: String?,
            val entries: List<FeedEntry>,
            val isTruncated: Boolean,
        )

        private data class FeedEntry(
            val title: String?,
            val url: String?,
            val publishedAtEpochMillis: Long?,
        )

        private const val MIN_ORDERED_ENTRIES = 2
        private val REL_SEPARATOR = Regex("\\s+")
    }

    private fun incomplete(
        page: ParsedSitePage,
        feedUrl: String?,
        chapters: List<ParsedChapter>,
        warning: String,
        feedTitle: String? = null,
    ): SiteParseResult.RequiresConfiguration {
        val metadata = if (feedUrl == null) {
            page.metadata
        } else {
            page.metadata + mapOf(FEED_URL_METADATA_KEY to feedUrl)
        }
        return SiteParseResult.RequiresConfiguration(
            reason = localizedMessage(
                "The feed did not provide a complete, ordered recent chapter window.",
                "O feed não forneceu uma janela recente de capítulos completa e ordenada.",
            ),
            preview = page.copy(
                title = page.title ?: feedTitle,
                currentChapter = null,
                chapters = chapters,
                isChapterListComplete = false,
                metadata = metadata,
                warnings = page.warnings + warning,
                isLatestChapterKnown = false,
            ),
            issue = SiteParseIssue.INCOMPLETE_CHAPTER_LIST,
        )
    }
}
