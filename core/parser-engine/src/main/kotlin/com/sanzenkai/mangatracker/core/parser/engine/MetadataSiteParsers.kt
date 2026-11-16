package com.sanzenkai.mangatracker.core.parser.engine

import com.sanzenkai.mangatracker.core.localization.localizedMessage
import com.sanzenkai.mangatracker.core.parser.api.ParserDocument
import com.sanzenkai.mangatracker.core.parser.api.ParserMatch
import com.sanzenkai.mangatracker.core.parser.api.SiteParseIssue
import com.sanzenkai.mangatracker.core.parser.api.SiteParseResult
import com.sanzenkai.mangatracker.core.parser.api.SiteParser

class StructuredMetadataParser : SiteParser {
    override val id: String = "structured-metadata"

    override fun match(document: ParserDocument): ParserMatch? =
        if (PageMetadataExtractor.hasStructuredOrOpenGraphMetadata(document)) {
            ParserMatch(
                confidence = 70,
                evidence = localizedMessage(
                    "Open Graph or JSON-LD metadata is present.",
                    "Há metadados Open Graph ou JSON-LD.",
                ),
            )
        } else {
            null
        }

    override fun parse(document: ParserDocument): SiteParseResult {
        val page = extractPage(document) ?: return invalidHtml()
        return page.toParseResult(document)
    }
}

class WordPressStructureParser : SiteParser {
    override val id: String = "wordpress-structure"

    override fun match(document: ParserDocument): ParserMatch? {
        val hints = PageMetadataExtractor.wordpressHints(document)
        return if (hints.isNotEmpty()) {
            ParserMatch(
                confidence = 85,
                evidence = localizedMessage(
                    "WordPress markers are present; theme-specific selectors are not assumed.",
                    "Foram encontrados indícios de WordPress; seletores específicos de tema não são presumidos.",
                ),
            )
        } else {
            null
        }
    }

    override fun parse(document: ParserDocument): SiteParseResult {
        val page = extractPage(document)?.copy(
            platformHints = PageMetadataExtractor.wordpressHints(document),
        ) ?: return invalidHtml()
        return page.toParseResult(document)
    }
}

class HtmlMetadataParser : SiteParser {
    override val id: String = "html-metadata"

    override fun match(document: ParserDocument): ParserMatch? =
        if (PageMetadataExtractor.hasHtmlMetadata(document)) {
            ParserMatch(
                confidence = 20,
                evidence = localizedMessage(
                    "An HTML title, description, or canonical link is present.",
                    "Há título, descrição ou link canônico no HTML.",
                ),
            )
        } else {
            null
        }

    override fun parse(document: ParserDocument): SiteParseResult {
        val page = extractPage(document) ?: return invalidHtml()
        return page.toParseResult(document)
    }
}

private fun extractPage(document: ParserDocument) =
    try {
        PageMetadataExtractor.extract(document)
    } catch (_: IllegalArgumentException) {
        null
    }

private fun com.sanzenkai.mangatracker.core.parser.api.ParsedSitePage.toParseResult(
    document: ParserDocument,
): SiteParseResult {
    if (title == null && currentChapter == null && originalCoverUrl == null) {
        val dynamicPage = PageMetadataExtractor.isDynamicShell(document)
        return SiteParseResult.RequiresConfiguration(
            reason = if (dynamicPage) {
                localizedMessage(
                    "The page renders its content with JavaScript; static HTML parsing cannot access it.",
                    "A página carrega o conteúdo com JavaScript; a análise de HTML estático não consegue acessá-lo.",
                )
            } else {
                localizedMessage(
                    "The page did not expose enough metadata to identify a manga or chapter.",
                    "A página não forneceu metadados suficientes para identificar o mangá ou capítulo.",
                )
            },
            preview = this,
            issue = if (dynamicPage) {
                SiteParseIssue.UNSUPPORTED_DYNAMIC_CONTENT
            } else {
                SiteParseIssue.NO_MATCH
            },
        )
    }

    return if (isChapterListComplete || isLatestChapterKnown) {
        SiteParseResult.Success(this)
    } else {
        SiteParseResult.RequiresConfiguration(
            reason = if (PageMetadataExtractor.isDynamicShell(document)) {
                localizedMessage(
                    "Chapter-list content is client-rendered and is not available in the received HTML.",
                    "A lista de capítulos é carregada no navegador e não está disponível no HTML recebido.",
                )
            } else {
                localizedMessage(
                    "Some metadata was found, but a complete chapter list could not be confirmed.",
                    "Alguns metadados foram encontrados, mas não foi possível confirmar uma lista completa de capítulos.",
                )
            },
            preview = this,
            issue = if (PageMetadataExtractor.isDynamicShell(document)) {
                SiteParseIssue.UNSUPPORTED_DYNAMIC_CONTENT
            } else {
                SiteParseIssue.INCOMPLETE_CHAPTER_LIST
            },
        )
    }
}

private fun invalidHtml() =
    SiteParseResult.Failure(
        reason = localizedMessage(
            "The HTML document is invalid or exceeds the parsing size limit.",
            "O documento HTML é inválido ou excede o limite de tamanho para análise.",
        ),
        issue = SiteParseIssue.INVALID_HTML,
    )
