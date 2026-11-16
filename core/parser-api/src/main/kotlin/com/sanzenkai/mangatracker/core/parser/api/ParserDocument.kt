package com.sanzenkai.mangatracker.core.parser.api

data class ParserDocument(
    val pageUrl: String,
    val contentType: String?,
    val html: String,
    val requestedUrl: String = pageUrl,
    val supplementalData: Map<String, String> = emptyMap(),
)

data class ParserMatch(
    val confidence: Int,
    val evidence: String,
)

data class ParsedChapter(
    val sourceKey: String,
    val label: String,
    val url: String,
    val publishedAtEpochMillis: Long?,
)

data class ParsedSitePage(
    val title: String?,
    val originalCoverUrl: String?,
    val seriesUrl: String?,
    val currentChapter: ParsedChapter?,
    val chapters: List<ParsedChapter>,
    val isChapterListComplete: Boolean,
    val metadata: Map<String, String> = emptyMap(),
    val platformHints: Set<String> = emptySet(),
    val warnings: List<String> = emptyList(),
    val isLatestChapterKnown: Boolean = false,
)

enum class SiteParseIssue {
    INVALID_HTML,
    INVALID_CONFIGURATION,
    UNSUPPORTED_DYNAMIC_CONTENT,
    INCOMPLETE_CHAPTER_LIST,
    NO_MATCH,
}

sealed interface SiteParseResult {
    data class Success(
        val page: ParsedSitePage,
    ) : SiteParseResult

    data class RequiresConfiguration(
        val reason: String,
        val preview: ParsedSitePage? = null,
        val issue: SiteParseIssue = SiteParseIssue.INCOMPLETE_CHAPTER_LIST,
    ) : SiteParseResult

    data class Failure(
        val reason: String,
        val issue: SiteParseIssue = SiteParseIssue.INVALID_HTML,
    ) : SiteParseResult
}
