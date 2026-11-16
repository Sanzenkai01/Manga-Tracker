package com.sanzenkai.mangatracker.ui.discovery

import com.sanzenkai.mangatracker.core.parser.api.ParsedSitePage
import com.sanzenkai.mangatracker.core.parser.api.SiteParseIssue
import com.sanzenkai.mangatracker.core.parser.api.SiteParseResult
import com.sanzenkai.mangatracker.core.parser.engine.ManualSiteConfiguration

data class SiteDiscoveryUiState(
    val url: String = "",
    val isLoading: Boolean = false,
    val isTestingConfiguration: Boolean = false,
    val isSavingConfiguration: Boolean = false,
    val isRegisteringManga: Boolean = false,
    val result: SiteParseResult? = null,
    val manualConfiguration: ManualSiteConfiguration? = null,
    val isManualConfigurationVisible: Boolean = false,
    val manualTestPassed: Boolean = false,
    val registeredMangaId: String? = null,
    val message: String? = null,
    val error: String? = null,
) {
    val previewPage: ParsedSitePage?
        get() = when (val parseResult = result) {
            is SiteParseResult.Success -> parseResult.page
            is SiteParseResult.RequiresConfiguration -> parseResult.preview
            is SiteParseResult.Failure, null -> null
        }

    val registrationCandidate: ParsedSitePage?
        get() {
            val page = previewPage ?: return null
            val canRegister = when (val parseResult = result) {
                is SiteParseResult.Success -> true
                is SiteParseResult.RequiresConfiguration ->
                    parseResult.issue == SiteParseIssue.INCOMPLETE_CHAPTER_LIST ||
                        (parseResult.issue == SiteParseIssue.UNSUPPORTED_DYNAMIC_CONTENT &&
                            page.currentChapter != null)
                is SiteParseResult.Failure, null -> false
            }
            if (!canRegister) return null

            val currentChapter = page.currentChapter
            return page.takeIf {
                !it.title.isNullOrBlank() &&
                    !it.seriesUrl.isNullOrBlank() &&
                    currentChapter != null &&
                    currentChapter.sourceKey.isNotBlank() &&
                    currentChapter.label.isNotBlank() &&
                    currentChapter.url.isNotBlank()
            }
        }
}
