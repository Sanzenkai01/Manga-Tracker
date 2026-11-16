package com.sanzenkai.mangatracker.parsers.site

import com.sanzenkai.mangatracker.core.localization.localizedMessage
import com.sanzenkai.mangatracker.core.parser.api.ParserDocument
import com.sanzenkai.mangatracker.core.parser.api.ParserMatch
import com.sanzenkai.mangatracker.core.parser.api.SiteParseIssue
import com.sanzenkai.mangatracker.core.parser.api.SiteParseResult
import com.sanzenkai.mangatracker.core.parser.api.SiteParser
import com.sanzenkai.mangatracker.core.parser.engine.DomainNormalizer
import com.sanzenkai.mangatracker.core.parser.engine.PageMetadataExtractor

class HiveToonsArticleParser : SiteParser {
    override val id: String = "hivetoons-article-jsonld"

    override fun match(document: ParserDocument): ParserMatch? {
        val host = DomainNormalizer.normalizedHostFromUrl(document.pageUrl)
        return if (host == "hivetoons.org" || host?.endsWith(".hivetoons.org") == true) {
            ParserMatch(
                confidence = 95,
                evidence = localizedMessage(
                    "HiveToons article and series metadata are read from JSON-LD/Open Graph.",
                    "Metadados do artigo e informações da série do HiveToons são obtidos de JSON-LD/Open Graph.",
                ),
            )
        } else {
            null
        }
    }

    override fun parse(document: ParserDocument): SiteParseResult {
        val page = try {
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

        if (page.title == null && page.currentChapter == null) {
            return SiteParseResult.RequiresConfiguration(
                reason = localizedMessage(
                    "HiveToons article metadata is missing from the received HTML.",
                    "Os metadados do artigo do HiveToons não estão presentes no HTML recebido.",
                ),
                preview = page,
                issue = SiteParseIssue.NO_MATCH,
            )
        }
        return if (page.isChapterListComplete) {
            SiteParseResult.Success(page)
        } else {
            SiteParseResult.RequiresConfiguration(
                reason = localizedMessage(
                    "HiveToons provided article metadata, but this page did not expose a complete chapter list.",
                    "O HiveToons forneceu metadados do artigo, mas esta página não apresentou uma lista completa de capítulos.",
                ),
                preview = page,
                issue = SiteParseIssue.INCOMPLETE_CHAPTER_LIST,
            )
        }
    }
}

object MangaSiteParsers {
    fun default(): Set<SiteParser> = setOf(
        HiveToonsSeriesParser(),
        HiveToonsArticleParser(),
        ImperioDaBritanniaParser(),
        MairimashitaIrumaKunParser(),
        RoliaScanParser(),
        com.sanzenkai.mangatracker.core.parser.engine.SyndicationFeedParser(),
        com.sanzenkai.mangatracker.core.parser.engine.WordPressStructureParser(),
        com.sanzenkai.mangatracker.core.parser.engine.StructuredMetadataParser(),
        com.sanzenkai.mangatracker.core.parser.engine.HtmlMetadataParser(),
    )
}
