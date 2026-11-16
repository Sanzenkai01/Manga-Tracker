package com.sanzenkai.mangatracker.core.parser.engine

import com.sanzenkai.mangatracker.core.localization.localizedMessage
import com.sanzenkai.mangatracker.core.parser.api.ParserDocument
import com.sanzenkai.mangatracker.core.parser.api.SiteParseIssue
import com.sanzenkai.mangatracker.core.parser.api.SiteParseResult

class SiteDiscoveryEngine(
    private val registry: ParserRegistry,
) {
    fun discover(
        document: ParserDocument,
        manualConfiguration: ManualSiteConfiguration? = null,
    ): SiteParseResult {
        if (manualConfiguration != null) {
            val manualParser = ManualCssRegexParser(manualConfiguration)
            if (manualParser.match(document) != null) return manualParser.parse(document)
        }

        var bestConfigurationRequest: SiteParseResult.RequiresConfiguration? = null
        var firstFailure: SiteParseResult.Failure? = null
        for (candidate in registry.candidates(document)) {
            when (val result = candidate.parser.parse(document)) {
                is SiteParseResult.Success -> return result
                is SiteParseResult.RequiresConfiguration -> {
                    if (bestConfigurationRequest == null) bestConfigurationRequest = result
                }
                is SiteParseResult.Failure -> {
                    if (firstFailure == null) firstFailure = result
                }
            }
        }
        return bestConfigurationRequest
            ?: firstFailure
            ?: SiteParseResult.RequiresConfiguration(
                reason = localizedMessage(
                    "No parser recognized this page. Configure selectors for this domain.",
                    "Nenhum parser reconheceu esta página. Configure seletores para este domínio.",
                ),
                issue = SiteParseIssue.NO_MATCH,
            )
    }
}
