package com.sanzenkai.mangatracker.core.parser.engine

import com.sanzenkai.mangatracker.core.parser.api.ParserDocument
import com.sanzenkai.mangatracker.core.parser.api.ParserMatch
import com.sanzenkai.mangatracker.core.parser.api.SiteParser

data class ParserCandidate(
    val parser: SiteParser,
    val match: ParserMatch,
)

class ParserRegistry(
    private val parsers: Set<SiteParser>,
) {
    fun candidates(document: ParserDocument): List<ParserCandidate> =
        parsers.mapNotNull { parser ->
            parser.match(document)?.let { match -> ParserCandidate(parser, match) }
        }.sortedByDescending { it.match.confidence }
}
