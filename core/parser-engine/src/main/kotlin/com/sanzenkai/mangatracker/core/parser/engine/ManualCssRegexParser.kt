package com.sanzenkai.mangatracker.core.parser.engine

import com.google.re2j.Pattern
import com.google.re2j.PatternSyntaxException
import com.sanzenkai.mangatracker.core.localization.localizedMessage
import com.sanzenkai.mangatracker.core.parser.api.ParsedChapter
import com.sanzenkai.mangatracker.core.parser.api.ParsedSitePage
import com.sanzenkai.mangatracker.core.parser.api.ParserDocument
import com.sanzenkai.mangatracker.core.parser.api.ParserMatch
import com.sanzenkai.mangatracker.core.parser.api.SiteParseIssue
import com.sanzenkai.mangatracker.core.parser.api.SiteParseResult
import com.sanzenkai.mangatracker.core.parser.api.SiteParser
import org.jsoup.nodes.Element
import org.jsoup.select.Selector

class ManualCssRegexParser(
    private val configuration: ManualSiteConfiguration,
) : SiteParser {
    override val id: String = MANUAL_PARSER_ID

    override fun match(document: ParserDocument): ParserMatch? =
        DomainNormalizer.normalizedHostFromUrl(document.pageUrl)
            ?.takeIf { it == configuration.normalizedHost }
            ?.let {
                ParserMatch(
                    confidence = 100,
                    evidence = localizedMessage(
                        "User-tested CSS/regex configuration for $it.",
                        "Configuração CSS/regex testada pelo usuário para $it.",
                    ),
                )
            }

    override fun parse(document: ParserDocument): SiteParseResult {
        val validationErrors = ManualConfigurationValidator.validate(configuration)
        if (validationErrors.isNotEmpty()) {
            return invalidConfiguration(validationErrors.joinToString(" "))
        }

        val root = try {
            org.jsoup.Jsoup.parse(document.html, document.pageUrl)
        } catch (_: IllegalArgumentException) {
            return SiteParseResult.Failure(
                reason = localizedMessage(
                    "The HTML document could not be parsed.",
                    "Não foi possível analisar o documento HTML.",
                ),
                issue = SiteParseIssue.INVALID_HTML,
            )
        }
        val title = extract(root, configuration.titleRule)
            ?: return invalidConfiguration(
                localizedMessage(
                    "The title selector returned no value.",
                    "O seletor do título não retornou nenhum valor.",
                ),
            )
        val chapterLabel = extract(root, configuration.chapterNumberRule)
            ?: return invalidConfiguration(
                localizedMessage(
                    "The chapter number selector returned no value.",
                    "O seletor do número do capítulo não retornou nenhum valor.",
                ),
            )
        val chapterUrl = extract(root, configuration.chapterLinkRule)
            ?.let { PageMetadataExtractor.resolveHttpUrl(it, document.pageUrl) }
            ?: return invalidConfiguration(
                localizedMessage(
                    "The chapter link selector must return an HTTP or HTTPS URL.",
                    "O seletor do link do capítulo deve retornar uma URL HTTP ou HTTPS.",
                ),
            )
        val coverUrl = extract(root, configuration.coverRule)
            ?.let { PageMetadataExtractor.resolveHttpUrl(it, document.pageUrl) }
        val seriesUrl = extract(root, configuration.seriesUrlRule)
            ?.let { PageMetadataExtractor.resolveHttpUrl(it, document.pageUrl) }
        val publishedAt = extract(root, configuration.publishedAtRule)
            ?.let(PageMetadataExtractor::parseEpochMillis)
        val currentChapter = ParsedChapter(
            sourceKey = chapterUrl,
            label = chapterLabel,
            url = chapterUrl,
            publishedAtEpochMillis = publishedAt,
        )

        val warnings = mutableListOf<String>()
        if (configuration.coverRule.selector.isNotBlank() && coverUrl == null) {
            warnings += localizedMessage(
                "The cover selector did not return a valid HTTP or HTTPS URL.",
                "O seletor da capa não retornou uma URL HTTP ou HTTPS válida.",
            )
        }
        if (configuration.publishedAtRule.selector.isNotBlank() && publishedAt == null) {
            warnings += localizedMessage(
                "The date selector did not return a recognized date.",
                "O seletor da data não retornou uma data reconhecida.",
            )
        }

        val chapterList = parseChapterList(root, document, warnings)
        val page = ParsedSitePage(
            title = title,
            originalCoverUrl = coverUrl,
            seriesUrl = seriesUrl,
            currentChapter = currentChapter,
            chapters = chapterList.chapters,
            isChapterListComplete = chapterList.isComplete,
            metadata = emptyMap(),
            platformHints = PageMetadataExtractor.wordpressHints(document),
            warnings = warnings,
        )
        return SiteParseResult.Success(page)
    }

    private fun parseChapterList(
        root: Element,
        document: ParserDocument,
        warnings: MutableList<String>,
    ): ChapterList {
        val selector = configuration.chapterListItemSelector
        if (selector.isBlank()) return ChapterList(emptyList(), isComplete = false)

        val items = try {
            root.select(selector)
        } catch (_: Selector.SelectorParseException) {
            warnings += localizedMessage(
                "The chapter-list selector could not be evaluated.",
                "Não foi possível avaliar o seletor da lista de capítulos.",
            )
            return ChapterList(emptyList(), isComplete = false)
        }
        if (items.isEmpty()) {
            warnings += localizedMessage(
                "The chapter-list selector matched no entries.",
                "O seletor da lista de capítulos não encontrou itens.",
            )
            return ChapterList(emptyList(), isComplete = false)
        }
        if (items.size > MAX_MANUAL_CHAPTERS) {
            warnings += localizedMessage(
                "The chapter-list selector matched more entries than the safety limit allows.",
                "O seletor da lista de capítulos encontrou mais itens do que o limite de segurança permite.",
            )
            return ChapterList(emptyList(), isComplete = false)
        }

        var allEntriesValid = true
        val chapters = items.mapNotNull { item ->
            val label = extract(item, configuration.chapterNumberRule)
                ?: item.text().trim().takeIf(String::isNotEmpty)
            val rawUrl = extract(item, configuration.chapterLinkRule)
                ?: if (item.normalName() == "a") item.attr("href") else null
            val url = rawUrl?.let { PageMetadataExtractor.resolveHttpUrl(it, document.pageUrl) }
            if (label == null || url == null) {
                allEntriesValid = false
                return@mapNotNull null
            }
            ParsedChapter(
                sourceKey = url,
                label = label,
                url = url,
                publishedAtEpochMillis = extract(item, configuration.publishedAtRule)
                    ?.let(PageMetadataExtractor::parseEpochMillis),
            )
        }.distinctBy(ParsedChapter::sourceKey)
        val complete = allEntriesValid && chapters.isNotEmpty()
        if (!complete) {
            warnings += localizedMessage(
                "Some chapter-list entries could not be validated.",
                "Não foi possível validar alguns itens da lista de capítulos.",
            )
        }
        return ChapterList(chapters, isComplete = complete)
    }

    private fun extract(root: Element, rule: ExtractionRule): String? {
        if (rule.selector.isBlank()) return null
        val element = try {
            root.selectFirst(rule.selector)
        } catch (_: Selector.SelectorParseException) {
            return null
        } ?: return null
        val rawValue = if (rule.attribute.equals("text", ignoreCase = true)) {
            element.text()
        } else {
            element.attr(rule.attribute)
        }.trim().takeIf(String::isNotEmpty) ?: return null
        if (rule.regex.isBlank()) return rawValue

        return try {
            val matcher = Pattern.compile(rule.regex).matcher(rawValue)
            if (!matcher.find() || rule.captureGroup > matcher.groupCount()) {
                null
            } else {
                matcher.group(rule.captureGroup)?.trim()?.takeIf(String::isNotEmpty)
            }
        } catch (_: PatternSyntaxException) {
            null
        }
    }

    private fun invalidConfiguration(reason: String) =
        SiteParseResult.Failure(
            reason = reason,
            issue = SiteParseIssue.INVALID_CONFIGURATION,
        )

    private data class ChapterList(
        val chapters: List<ParsedChapter>,
        val isComplete: Boolean,
    )

    private companion object {
        const val MAX_MANUAL_CHAPTERS = 500
    }
}

const val MANUAL_PARSER_ID = "manual-css-regex"
