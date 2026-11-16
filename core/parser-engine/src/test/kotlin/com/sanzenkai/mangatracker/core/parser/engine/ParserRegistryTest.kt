package com.sanzenkai.mangatracker.core.parser.engine

import com.sanzenkai.mangatracker.core.parser.api.ParserDocument
import com.sanzenkai.mangatracker.core.parser.api.ParserMatch
import com.sanzenkai.mangatracker.core.parser.api.SiteParseResult
import com.sanzenkai.mangatracker.core.parser.api.SiteParser
import org.junit.Assert.assertEquals
import org.junit.Test

class ParserRegistryTest {
    @Test
    fun candidatesAreOrderedByConfidenceWithoutDomainDispatch() {
        val registry = ParserRegistry(
            setOf(
                StubParser("generic", 40),
                StubParser("site-layout", 90),
            ),
        )

        val candidates = registry.candidates(
            ParserDocument(
                pageUrl = "https://example.org/chapter/1",
                contentType = "text/html",
                html = "<html></html>",
            ),
        )

        assertEquals(listOf("site-layout", "generic"), candidates.map { it.parser.id })
    }

    @Test
    fun returnsNoCandidatesWhenNoParserMatches() {
        val registry = ParserRegistry(setOf(StubParser("unsupported", null)))

        val candidates = registry.candidates(
            ParserDocument(
                pageUrl = "https://example.org/chapter/1",
                contentType = "text/html",
                html = "<html></html>",
            ),
        )

        assertEquals(emptyList<ParserCandidate>(), candidates)
    }

    private class StubParser(
        override val id: String,
        private val confidence: Int?,
    ) : SiteParser {
        override fun match(document: ParserDocument): ParserMatch? =
            confidence?.let { ParserMatch(it, "test fixture") }

        override fun parse(document: ParserDocument): SiteParseResult =
            SiteParseResult.RequiresConfiguration("Parser is only a test stub.")
    }
}
