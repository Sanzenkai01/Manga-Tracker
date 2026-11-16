package com.sanzenkai.mangatracker.core.parser.api

interface SiteParser {
    val id: String

    fun match(document: ParserDocument): ParserMatch?

    fun parse(document: ParserDocument): SiteParseResult
}
