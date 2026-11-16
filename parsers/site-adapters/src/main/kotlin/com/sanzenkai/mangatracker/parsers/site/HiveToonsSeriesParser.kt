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
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URISyntaxException

class HiveToonsSeriesParser : SiteParser {
    override val id: String = "hivetoons-series-page"

    override fun match(document: ParserDocument): ParserMatch? {
        val host = DomainNormalizer.normalizedHostFromUrl(document.pageUrl)
        if (!host.isHiveToonsHost()) return null
        if (seriesPath(document.pageUrl) == null) return null

        return ParserMatch(
            confidence = 100,
            evidence = localizedMessage(
                "The HiveToons series page exposes a chapter list in its rendered HTML.",
                "A página da série do HiveToons apresenta uma lista de capítulos no HTML recebido.",
            ),
        )
    }

    override fun parse(document: ParserDocument): SiteParseResult {
        val metadataPage = try {
            PageMetadataExtractor.extract(document)
        } catch (_: IllegalArgumentException) {
            return SiteParseResult.Failure(
                reason = localizedMessage(
                    "The HiveToons HTML document is invalid or too large.",
                    "O documento HTML do HiveToons é inválido ou muito grande.",
                ),
                issue = SiteParseIssue.INVALID_HTML,
            )
        }

        val parsed = Jsoup.parse(document.html, document.pageUrl)
        val pageHost = DomainNormalizer.normalizedHostFromUrl(document.pageUrl)
        val seriesUrl = sequenceOf(
            metadataPage.metadata["og:url"],
            metadataPage.metadata["link:canonical"],
            document.pageUrl,
        )
            .filterNotNull()
            .mapNotNull { PageMetadataExtractor.resolveHttpUrl(it, document.pageUrl) }
            .firstOrNull {
                DomainNormalizer.normalizedHostFromUrl(it) == pageHost && seriesPath(it) != null
            }
        val pageSeriesPath = seriesUrl?.let(::seriesPath)
        val panel = parsed.selectFirst("[data-series-tab-panel=chapters]")
        val chapterAnchors = if (panel != null && pageSeriesPath != null) {
            panel.select("a[href]").filter { anchor ->
                chapterUrl(anchor, document.pageUrl, pageHost, pageSeriesPath) != null
            }
        } else {
            emptyList()
        }
        val visibleChapters = pageSeriesPath?.let { seriesPath ->
            chapterAnchors.mapNotNull { anchor ->
                parseChapter(anchor, document.pageUrl, pageHost, seriesPath)
            }
        }.orEmpty()
        val declaredChapterCount = parsed.selectFirst("[data-series-tab-button=chapters]")
            ?.text()
            ?.let(CHAPTER_COUNT_PATTERN::find)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        val hydratedChapterList = if (seriesUrl != null && pageSeriesPath != null) {
            parseHydratedChapterList(
                parsed = parsed,
                seriesUrl = seriesUrl,
                expectedHost = pageHost,
                seriesPath = pageSeriesPath,
            )
        } else {
            HydratedChapterList.EMPTY
        }
        val visibleBySlug = visibleChapters.mapNotNull { chapter ->
            chapterIdentifier(chapter.url, pageSeriesPath.orEmpty())
                ?.lowercase()
                ?.let { "chapter-$it" to chapter }
        }.toMap()
        val mergedChapters = linkedMapOf<String, ParsedChapter>()
        hydratedChapterList.chapters.forEach { hydrated ->
            val identifier = hydrated.slug.lowercase()
            val visible = visibleBySlug[identifier]
            mergedChapters[identifier] = if (visible == null) {
                hydrated.chapter
            } else {
                visible.copy(
                    publishedAtEpochMillis = visible.publishedAtEpochMillis
                        ?: hydrated.chapter.publishedAtEpochMillis,
                )
            }
        }
        visibleChapters.forEach { chapter ->
            val identifier = chapterIdentifier(chapter.url, pageSeriesPath.orEmpty())
                ?.lowercase()
                ?: return@forEach
            mergedChapters.putIfAbsent("chapter-$identifier", chapter)
        }
        val chapters = mergedChapters.values.toList()
        val allChapterLinksParsed = visibleChapters.size == chapterAnchors.size &&
            visibleChapters.mapNotNull { chapterIdentifier(it.url, pageSeriesPath.orEmpty()) }
                .distinctBy(String::lowercase)
                .size == visibleChapters.size
        val completeHydration = declaredChapterCount != null &&
            hydratedChapterList.declaredCount == declaredChapterCount &&
            hydratedChapterList.recordCount == hydratedChapterList.chapters.size &&
            hydratedChapterList.chapters.size == declaredChapterCount &&
            hydratedChapterList.chapters.map(HydratedChapter::slug)
                .distinctBy(String::lowercase)
                .size == hydratedChapterList.chapters.size
        val isComplete = declaredChapterCount != null &&
            completeHydration &&
            declaredChapterCount == chapters.size &&
            allChapterLinksParsed &&
            chapters.isNotEmpty()
        val recentWindowIsVerifiable = chapters.size > 1 ||
            (declaredChapterCount == 1 && chapters.size == 1)
        val declaredCountMatchesWindow = declaredChapterCount == null ||
            declaredChapterCount >= visibleChapters.size
        val isLatestChapterKnown = recentWindowIsVerifiable &&
            declaredCountMatchesWindow &&
            allChapterLinksParsed &&
            hydratedChapterList.recordCount == hydratedChapterList.chapters.size &&
            visibleChapters.isNotEmpty() &&
            chapters.firstOrNull()?.sourceKey == visibleChapters.firstOrNull()?.sourceKey &&
            isNewestFirst(chapters, pageSeriesPath.orEmpty())
        val reason = incompleteListReason(chapters.size, declaredChapterCount)
        val page = ParsedSitePage(
            title = metadataPage.title,
            originalCoverUrl = originalCoverUrl(parsed, document.pageUrl)
                ?: metadataPage.originalCoverUrl,
            seriesUrl = seriesUrl,
            currentChapter = chapters.firstOrNull(),
            chapters = chapters,
            isChapterListComplete = isComplete,
            metadata = metadataPage.metadata,
            platformHints = metadataPage.platformHints,
            warnings = if (isComplete || isLatestChapterKnown) emptyList() else listOf(reason),
            isLatestChapterKnown = isLatestChapterKnown,
        )

        return if (isComplete || isLatestChapterKnown) {
            SiteParseResult.Success(page)
        } else {
            SiteParseResult.RequiresConfiguration(
                reason = reason,
                preview = page,
                issue = SiteParseIssue.INCOMPLETE_CHAPTER_LIST,
            )
        }
    }

    private fun originalCoverUrl(document: org.jsoup.nodes.Document, pageUrl: String): String? {
        val seriesSection = document.select("section[itemtype]").firstOrNull {
            it.attr("itemtype").contains("CreativeWorkSeries", ignoreCase = true)
        } ?: return null
        val rawUrl = seriesSection.selectFirst("img[itemprop=image][src]")?.attr("src")
            ?: return null
        return PageMetadataExtractor.resolveHttpUrl(rawUrl, pageUrl)
    }

    private fun chapterUrl(
        anchor: Element,
        pageUrl: String,
        expectedHost: String?,
        seriesPath: String,
    ): String? {
        val url = PageMetadataExtractor.resolveHttpUrl(anchor.attr("href"), pageUrl) ?: return null
        if (DomainNormalizer.normalizedHostFromUrl(url) != expectedHost) return null
        val path = pathFromUrl(url)?.trimEnd('/') ?: return null
        return url.takeIf { path.startsWith("$seriesPath/chapter-", ignoreCase = true) }
    }

    private fun parseChapter(
        anchor: Element,
        pageUrl: String,
        expectedHost: String?,
        seriesPath: String,
    ): ParsedChapter? {
        val url = chapterUrl(anchor, pageUrl, expectedHost, seriesPath) ?: return null
        val identifier = chapterIdentifier(url, seriesPath) ?: return null
        val chapterLabel = anchor.select("span").asSequence()
            .map { it.text().trim() }
            .firstOrNull { label -> label.contains(identifier, ignoreCase = true) }
            ?: "Chapter $identifier"
        val publishedAt = anchor.selectFirst("time[datetime]")?.attr("datetime")

        return ParsedChapter(
            sourceKey = url,
            label = chapterLabel,
            url = url,
            publishedAtEpochMillis = publishedAt?.let(PageMetadataExtractor::parseEpochMillis),
        )
    }

    private fun parseHydratedChapterList(
        parsed: org.jsoup.nodes.Document,
        seriesUrl: String,
        expectedHost: String?,
        seriesPath: String,
    ): HydratedChapterList {
        val scripts = parsed.select("script").map(Element::data)
        val recordCount = scripts.sumOf { script ->
            SERIALIZED_CHAPTER_START_PATTERN.findAll(script).count()
        }
        val chapters = scripts.flatMap { script ->
            SERIALIZED_CHAPTER_PATTERN.findAll(script).mapNotNull { match ->
                val slug = match.groups["slug"]?.value ?: return@mapNotNull null
                val number = match.groups["number"]?.value ?: return@mapNotNull null
                val identifier = CHAPTER_PATH_PATTERN.matchEntire(slug)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?: return@mapNotNull null
                val identifierNumber = CHAPTER_NUMBER_PATTERN.find(identifier)
                    ?.value
                    ?.replace(',', '.')
                    ?.toBigDecimalOrNull()
                    ?: return@mapNotNull null
                val recordNumber = number.toBigDecimalOrNull() ?: return@mapNotNull null
                if (identifierNumber.compareTo(recordNumber) != 0) return@mapNotNull null

                val chapterUrl = PageMetadataExtractor.resolveHttpUrl(
                    "$seriesPath/$slug",
                    seriesUrl,
                ) ?: return@mapNotNull null
                if (DomainNormalizer.normalizedHostFromUrl(chapterUrl) != expectedHost) {
                    return@mapNotNull null
                }

                val publishedAt = match.groups["created"]?.value
                    ?.let(PageMetadataExtractor::parseEpochMillis)
                HydratedChapter(
                    slug = slug,
                    chapter = ParsedChapter(
                        sourceKey = chapterUrl,
                        label = "Chapter $identifier",
                        url = chapterUrl,
                        publishedAtEpochMillis = publishedAt,
                    ),
                )
            }.toList()
        }
        val declaredCount = scripts.asSequence()
            .mapNotNull { script ->
                TOTAL_CHAPTER_COUNT_PATTERN.find(script)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
            }
            .firstOrNull()

        return HydratedChapterList(
            declaredCount = declaredCount,
            recordCount = recordCount,
            chapters = chapters,
        )
    }

    private fun chapterIdentifier(url: String, seriesPath: String): String? {
        val path = pathFromUrl(url)?.trimEnd('/') ?: return null
        return CHAPTER_PATH_PATTERN.matchEntire(path.removePrefix("$seriesPath/"))
            ?.groupValues
            ?.getOrNull(1)
    }

    private fun isNewestFirst(chapters: List<ParsedChapter>, seriesPath: String): Boolean {
        val numbers = chapters.map { chapter ->
            chapterIdentifier(chapter.url, seriesPath)
                ?.lowercase()
                ?.removeSuffix("a")
                ?.replace(',', '.')
                ?.toBigDecimalOrNull()
                ?: return false
        }
        return numbers.zipWithNext().all { (newer, older) -> newer > older }
    }

    private fun seriesPath(rawUrl: String): String? {
        val path = pathFromUrl(rawUrl)?.trimEnd('/') ?: return null
        val segments = path.split('/').filter(String::isNotEmpty)
        return path.takeIf { segments.size == 2 && segments[0] == "series" }
    }

    private fun pathFromUrl(rawUrl: String): String? =
        try {
            URI(rawUrl).normalize().path
        } catch (_: URISyntaxException) {
            null
        }

    private fun String?.isHiveToonsHost(): Boolean =
        this == "hivetoons.org" || this?.endsWith(".hivetoons.org") == true

    private fun incompleteListReason(foundCount: Int, expectedCount: Int?): String =
        if (expectedCount != null && expectedCount > foundCount) {
            localizedMessage(
                "Only $foundCount of $expectedCount chapter entries were recognized; the recent window could not be verified and saved chapters were kept.",
                "Apenas $foundCount de $expectedCount capítulos foram reconhecidos; não foi possível validar a lista recente e os capítulos salvos foram mantidos.",
            )
        } else {
            localizedMessage(
                "The series page did not provide a verifiable recent chapter window; saved chapters were kept unchanged.",
                "A página da série não apresentou uma sequência recente de capítulos verificável; os capítulos salvos foram mantidos.",
            )
        }

    private companion object {
        val CHAPTER_COUNT_PATTERN = Regex("\\((\\d+)\\)")
        val TOTAL_CHAPTER_COUNT_PATTERN = Regex("totalChapterCount:(\\d+)")
        val SERIALIZED_CHAPTER_START_PATTERN = Regex(
            """\${'$'}R\[\d+]=\{id:\d+,slug:"chapter-[^"]+""",
        )
        val SERIALIZED_CHAPTER_PATTERN = Regex(
            """\${'$'}R\[\d+]=\{id:\d+,slug:"(?<slug>chapter-[^"]+)",number:(?<number>\d+(?:\.\d+)?),title:"(?:\\.|[^"\\])*",featuredImage:(?:"(?:\\.|[^"\\])*"|null),createdAt:"(?<created>[^"]+)""",
        )
        val CHAPTER_NUMBER_PATTERN = Regex("""\d+(?:[.,]\d+)?""")
        val CHAPTER_PATH_PATTERN = Regex(
            pattern = "chapter-([0-9]+(?:[.,][0-9]+)?[a-z]?)",
            option = RegexOption.IGNORE_CASE,
        )
    }

    private data class HydratedChapter(
        val slug: String,
        val chapter: ParsedChapter,
    )

    private data class HydratedChapterList(
        val declaredCount: Int?,
        val recordCount: Int,
        val chapters: List<HydratedChapter>,
    ) {
        companion object {
            val EMPTY = HydratedChapterList(null, 0, emptyList())
        }
    }
}
