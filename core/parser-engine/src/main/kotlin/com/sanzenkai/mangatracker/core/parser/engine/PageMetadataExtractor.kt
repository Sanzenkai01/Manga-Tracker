package com.sanzenkai.mangatracker.core.parser.engine

import com.sanzenkai.mangatracker.core.localization.localizedMessage
import com.sanzenkai.mangatracker.core.parser.api.ParsedChapter
import com.sanzenkai.mangatracker.core.parser.api.ParsedSitePage
import com.sanzenkai.mangatracker.core.parser.api.ParserDocument
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.math.BigDecimal
import java.net.URI
import java.net.URISyntaxException
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

object PageMetadataExtractor {
    private const val MAX_HTML_CHARACTERS = 2 * 1024 * 1024
    private const val MAX_JSON_LD_SCRIPT_CHARACTERS = 128 * 1024
    private const val MAX_JSON_LD_TOTAL_CHARACTERS = 256 * 1024
    private const val MAX_JSON_LD_STRUCTURES = 4_096
    private const val MAX_JSON_LD_DEPTH = 64
    private const val MAX_GENERIC_CHAPTER_LINKS = 800
    private const val MIN_GENERIC_CHAPTER_LINKS = 3

    fun extract(document: ParserDocument): ParsedSitePage {
        require(document.html.length <= MAX_HTML_CHARACTERS) {
            localizedMessage(
                "The HTML document exceeds the parsing size limit.",
                "O documento HTML excede o limite de tamanho para análise.",
            )
        }
        val parsed = Jsoup.parse(document.html, document.pageUrl)
        val metadata = extractHtmlMetadata(parsed)
        val structuredObjects = extractJsonLdObjects(parsed)
        val seriesObject = structuredObjects
            .firstNotNullOfOrNull { it.optJSONObject("isPartOf") }
            ?: structuredObjects.firstOrNull { it.hasType("CreativeWorkSeries") }
        val chapterPageUrl = sequenceOf(
            document.pageUrl,
            metadata["og:url"],
            metadata["link:canonical"],
        )
            .filterNotNull()
            .mapNotNull { resolveHttpUrl(it, document.pageUrl) }
            .firstOrNull { findChapterIdentity(it) != null && isSameHost(it, document.pageUrl) }
        val schemaSeriesUrl = seriesObject?.readString("url")
            ?.let { resolveHttpUrl(it, document.pageUrl) }
            ?.takeIf { isSameHost(it, document.pageUrl) }
        val metadataSeriesUrl = sequenceOf(metadata["og:url"], metadata["link:canonical"])
            .filterNotNull()
            .mapNotNull { resolveHttpUrl(it, document.pageUrl) }
            .firstOrNull {
                findChapterIdentity(it) == null && isSameHost(it, document.pageUrl)
            }
        val seriesUrl = schemaSeriesUrl
            ?: metadataSeriesUrl
            ?: chapterPageUrl?.let(::deriveSeriesUrl)
            ?: document.pageUrl.takeIf { chapterPageUrl == null }
        val schemaHeadline = structuredObjects.firstNotNullOfOrNull {
            it.readString("headline")
        }
        val schemaName = structuredObjects.firstNotNullOfOrNull {
            if (it.hasType("CreativeWorkSeries") ||
                it.hasType("ComicSeries") ||
                it.hasType("BookSeries") ||
                it.hasType("Manga")
            ) {
                it.readString("name")
            } else {
                null
            }
        }
        val title = seriesObject?.readString("name")
            ?: structuredObjects.firstNotNullOfOrNull { it.readString("seriesName") }
            ?: schemaHeadline?.stripChapterSuffix()
            ?: schemaName?.stripChapterSuffix()
            ?: metadata["og:title"]?.stripChapterSuffix()
            ?: metadata["name:title"]?.stripChapterSuffix()
            ?: parsed.selectFirst("h1")?.text()?.trim()?.stripChapterSuffix()
            ?: parsed.title().trim().takeIf(String::isNotEmpty)?.stripChapterSuffix()
        val originalCoverUrl = (metadata["og:image"]
            ?: metadata["twitter:image"]
            ?: metadata["itemprop:image"])
            ?.let { resolveHttpUrl(it, document.pageUrl) }
            ?: structuredObjects.firstNotNullOfOrNull {
                if (it.hasType("Article") || it.hasType("CreativeWork") || it.hasType("Manga")) {
                    it.imageUrl()
                } else {
                    null
                }
            }
                ?.let { resolveHttpUrl(it, document.pageUrl) }
        val publicationDate = structuredObjects.firstNotNullOfOrNull {
            if (it.hasType("Article") || it.hasType("CreativeWork") || it.hasType("Chapter")) {
                it.readString("datePublished")
            } else {
                null
            }
        } ?: metadata["article:published_time"]
            ?: metadata["name:datepublished"]
            ?: metadata["name:publish_date"]
        val chapterIdentity = chapterPageUrl?.let(::findChapterIdentity)
            ?: schemaHeadline?.let(::findChapterIdentity)
            ?: metadata["og:title"]?.let(::findChapterIdentity)
            ?: parsed.selectFirst("h1")?.text()?.let(::findChapterIdentity)
        val currentChapter = chapterIdentity?.let { (label, _) ->
            ParsedChapter(
                sourceKey = chapterPageUrl ?: document.pageUrl,
                label = label,
                url = chapterPageUrl ?: document.pageUrl,
                publishedAtEpochMillis = publicationDate?.let(::parseEpochMillis),
            )
        }
        val itemLists = structuredObjects.mapNotNull { it.readItemList(document.pageUrl) }
        val htmlChapterWindow = extractHtmlChapterWindow(parsed, document.pageUrl)
        val itemList = itemLists.firstOrNull(ChapterList::isComplete)
            ?: itemLists.firstOrNull(ChapterList::isLatestChapterKnown)
            ?: htmlChapterWindow
            ?: itemLists.firstOrNull()
        val hints = wordpressHints(parsed)
        val dynamicShell = isDynamicShell(parsed)
        val warnings = buildList {
            if (dynamicShell) {
                add(
                    localizedMessage(
                        "The page requires JavaScript and contains no content in the received HTML; the app does not execute JavaScript.",
                        "A página depende de JavaScript e não contém conteúdo no HTML recebido; o app não executa JavaScript.",
                    ),
                )
            }
            if (itemList == null || (!itemList.isComplete && !itemList.isLatestChapterKnown)) {
                add(
                    localizedMessage(
                        "A complete chapter list could not be confirmed in the received HTML.",
                        "Não foi possível confirmar uma lista completa de capítulos no HTML recebido.",
                    ),
                )
            }
        }

        return ParsedSitePage(
            title = title,
            originalCoverUrl = originalCoverUrl,
            seriesUrl = seriesUrl,
            currentChapter = itemList?.takeIf(ChapterList::isLatestChapterKnown)
                ?.chapters
                ?.firstOrNull()
                ?: currentChapter,
            chapters = itemList?.chapters.orEmpty(),
            isChapterListComplete = itemList?.isComplete == true,
            metadata = metadata,
            platformHints = hints,
            warnings = warnings,
            isLatestChapterKnown = itemList?.isLatestChapterKnown == true,
        )
    }

    fun isDynamicShell(document: ParserDocument): Boolean =
        isDynamicShell(Jsoup.parse(document.html, document.pageUrl))

    fun wordpressHints(document: ParserDocument): Set<String> =
        wordpressHints(Jsoup.parse(document.html, document.pageUrl))

    fun hasStructuredOrOpenGraphMetadata(document: ParserDocument): Boolean {
        val parsed = Jsoup.parse(document.html, document.pageUrl)
        return parsed.selectFirst("meta[property^=og:]") != null ||
            parsed.selectFirst("script[type=application/ld+json]") != null
    }

    fun hasHtmlMetadata(document: ParserDocument): Boolean {
        val parsed = Jsoup.parse(document.html, document.pageUrl)
        return parsed.title().isNotBlank() ||
            parsed.selectFirst("link[rel=canonical]") != null ||
            parsed.selectFirst("meta[name=description], h1") != null ||
            parsed.select("a[href]").take(MAX_GENERIC_CHAPTER_LINKS + 1).count { anchor ->
                findChapterIdentity(anchor.text()) != null ||
                    findChapterIdentity(anchor.attr("href")) != null
            } >= MIN_GENERIC_CHAPTER_LINKS
    }

    fun parseEpochMillis(rawValue: String): Long? {
        val value = rawValue.trim()
        if (value.isEmpty()) return null
        return try {
            Instant.parse(value).toEpochMilli()
        } catch (_: DateTimeParseException) {
            try {
                OffsetDateTime.parse(value).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                try {
                    LocalDate.parse(value).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
                } catch (_: DateTimeParseException) {
                    parseCommonDate(value)
                }
            }
        }
    }

    fun resolveHttpUrl(rawValue: String, baseUrl: String): String? {
        val value = rawValue.trim()
        if (value.isEmpty()) return null
        return try {
            val base = URI(baseUrl)
            val resolved = base.resolve(URI(value))
            val scheme = resolved.scheme?.lowercase(Locale.ROOT)
            if ((scheme == "http" || scheme == "https") &&
                resolved.host != null &&
                resolved.rawUserInfo == null
            ) {
                resolved.toASCIIString()
            } else {
                null
            }
        } catch (_: URISyntaxException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun extractHtmlMetadata(document: org.jsoup.nodes.Document): Map<String, String> {
        val metadata = linkedMapOf<String, String>()
        document.select("meta[property], meta[name], meta[itemprop]").forEach { element ->
            val property = element.attr("property").trim().lowercase(Locale.ROOT)
            val name = element.attr("name").trim().lowercase(Locale.ROOT)
            val itemprop = element.attr("itemprop").trim().lowercase(Locale.ROOT)
            val content = element.attr("content").trim()
            val key = when {
                property.startsWith("og:") || property.startsWith("article:") -> property
                name.startsWith("twitter:") -> name
                name.isNotEmpty() -> "name:$name"
                itemprop.isNotEmpty() -> "itemprop:$itemprop"
                else -> ""
            }
            if (key.isNotEmpty() && content.isNotEmpty()) {
                metadata.putIfAbsent(key, content)
            }
        }
        document.select("meta[property^=og:], meta[name^=twitter:]").forEach { element ->
            val key = element.attr("property").ifBlank { element.attr("name") }
                .trim()
                .lowercase(Locale.ROOT)
            val content = element.attr("content").trim()
            if (key.isNotEmpty() && content.isNotEmpty()) metadata.putIfAbsent(key, content)
        }
        document.selectFirst("link[rel=canonical]")?.attr("abs:href")
            ?.takeIf(String::isNotBlank)
            ?.let { metadata.putIfAbsent("link:canonical", it) }
        document.selectFirst("meta[itemprop=image]")?.attr("content")
            ?.takeIf(String::isNotBlank)
            ?.let { metadata.putIfAbsent("itemprop:image", it) }
        return metadata
    }

    private fun extractJsonLdObjects(document: org.jsoup.nodes.Document): List<JSONObject> {
        val objects = mutableListOf<JSONObject>()
        var parsedCharacters = 0
        var parsedStructures = 0
        document.select("script[type=application/ld+json]").forEach { script ->
            val json = script.data().ifBlank { script.html() }
            val structureCount = boundedJsonStructureCount(json) ?: return@forEach
            if (parsedCharacters + json.length > MAX_JSON_LD_TOTAL_CHARACTERS ||
                parsedStructures + structureCount > MAX_JSON_LD_STRUCTURES
            ) {
                return@forEach
            }
            parsedCharacters += json.length
            parsedStructures += structureCount
            try {
                collectJsonObjects(JSONTokener(json).nextValue(), objects)
            } catch (_: JSONException) {
                Unit
            }
        }
        return objects
    }

    private fun boundedJsonStructureCount(json: String): Int? {
        if (json.length > MAX_JSON_LD_SCRIPT_CHARACTERS) return null

        var depth = 0
        var structures = 0
        var inString = false
        var isEscaped = false
        for (character in json) {
            if (inString) {
                when {
                    isEscaped -> isEscaped = false
                    character == '\\' -> isEscaped = true
                    character == '"' -> inString = false
                }
                continue
            }

            when (character) {
                '"' -> inString = true
                '{', '[' -> {
                    depth += 1
                    structures += 1
                    if (depth > MAX_JSON_LD_DEPTH || structures > MAX_JSON_LD_STRUCTURES) {
                        return null
                    }
                }
                '}', ']' -> {
                    depth -= 1
                    if (depth < 0) return null
                }
            }
        }
        return structures.takeIf { depth == 0 && !inString }
    }

    private fun collectJsonObjects(value: Any?, destination: MutableList<JSONObject>) {
        when (value) {
            is JSONObject -> {
                destination += value
                val keys = value.keys()
                while (keys.hasNext()) collectJsonObjects(value.opt(keys.next()), destination)
            }
            is JSONArray -> {
                for (index in 0 until value.length()) collectJsonObjects(value.opt(index), destination)
            }
        }
    }

    private fun wordpressHints(document: org.jsoup.nodes.Document): Set<String> {
        val hints = linkedSetOf<String>()
        val generator = document.select("meta[name=generator]").joinToString(" ") {
            it.attr("content")
        }
        val hasWordPressAsset = document.select("[src], [href]").any { element ->
            element.attr("src").contains("wp-content", ignoreCase = true) ||
                element.attr("href").contains("wp-content", ignoreCase = true) ||
                element.attr("src").contains("wp-includes", ignoreCase = true) ||
                element.attr("href").contains("wp-includes", ignoreCase = true)
        }
        if (generator.contains("wordpress", ignoreCase = true) ||
            hasWordPressAsset ||
            document.getElementById("wpadminbar") != null
        ) {
            hints += "WordPress"
        }
        return hints
    }

    private fun isDynamicShell(document: org.jsoup.nodes.Document): Boolean {
        val hasClientRoot = document.selectFirst("#root, #app, #__next") != null
        val hasModuleScript = document.selectFirst("script[type=module], script[async][src]") != null
        return hasClientRoot && hasModuleScript && document.body().text().isBlank()
    }

    internal fun findChapterIdentity(value: String): Pair<String, String>? {
        val searchableValue = try {
            URI(value).path?.takeIf(String::isNotEmpty) ?: value
        } catch (_: URISyntaxException) {
            value
        }
        val match = CHAPTER_IDENTITY_PATTERN.find(searchableValue) ?: return null
        val type = match.groupValues[1].trimEnd('.', ':', '#')
        val rawIdentifier = match.groupValues[2].replace(',', '.')
        val identifier = HYPHEN_DECIMAL_PATTERN.replace(rawIdentifier, "$1.$2$3")
        val label = "$type $identifier"
        return label to identifier
    }

    private fun extractHtmlChapterWindow(
        document: org.jsoup.nodes.Document,
        pageUrl: String,
    ): ChapterList? {
        val anchors = document.select("a[href]")
        if (anchors.size > MAX_GENERIC_CHAPTER_LINKS) return null

        val candidates = anchors.mapNotNull { anchor ->
            if (anchor.isChapterNavigation()) return@mapNotNull null
            val url = resolveHttpUrl(anchor.attr("href"), pageUrl) ?: return@mapNotNull null
            if (!isSameHost(url, pageUrl) || isFragmentOnlyChapterLink(url, pageUrl)) {
                return@mapNotNull null
            }
            val label = sequenceOf(
                anchor.text().trim(),
                anchor.attr("title").trim(),
                anchor.selectFirst("img[alt]")?.attr("alt")?.trim().orEmpty(),
            ).firstOrNull(String::isNotEmpty).orEmpty()
            val identity = findChapterIdentity(label)
                ?: findChapterIdentity(url)
                ?: return@mapNotNull null
            val seriesKey = chapterSeriesKey(url) ?: return@mapNotNull null
            val dateElement = anchor.selectFirst("time[datetime], time[content]")
                ?: anchor.parent()
                    ?.takeIf { it.select("a[href]").size <= 2 }
                    ?.selectFirst("time[datetime], time[content]")
            val dateValue = dateElement?.let {
                it.attr("datetime").ifBlank { it.attr("content") }
            }
            HtmlChapterCandidate(
                seriesKey = seriesKey,
                chapter = ParsedChapter(
                    sourceKey = url,
                    label = identity.first,
                    url = url,
                    publishedAtEpochMillis = dateValue?.let(::parseEpochMillis),
                ),
            )
        }.distinctBy { it.chapter.sourceKey }

        return candidates
            .groupBy(HtmlChapterCandidate::seriesKey)
            .values
            .asSequence()
            .filter { it.size >= MIN_GENERIC_CHAPTER_LINKS }
            .mapNotNull { group ->
                val orderedChapters = orderNewestFirst(group.map(HtmlChapterCandidate::chapter))
                    ?: return@mapNotNull null
                ChapterList(
                    chapters = orderedChapters,
                    isComplete = false,
                    isLatestChapterKnown = true,
                )
            }
            .maxByOrNull { it.chapters.size }
    }

    private fun orderNewestFirst(chapters: List<ParsedChapter>): List<ParsedChapter>? {
        if (chapters.size < MIN_GENERIC_CHAPTER_LINKS) return null
        val identities = chapters.map { chapter ->
            findChapterIdentity(chapter.label)
                ?: findChapterIdentity(chapter.url)
                ?: return null
        }
        if (identities.map { it.second.lowercase(Locale.ROOT) }.distinct().size != chapters.size) {
            return null
        }

        val numbers = identities.map { (_, identifier) ->
            identifier.toBigDecimalOrNull()
        }
        if (numbers.all { it != null }) {
            val values = numbers.filterNotNull()
            if (values.map(BigDecimal::stripTrailingZeros).distinct().size == values.size &&
                isStrictlyOrderedNumbers(values)
            ) {
                return chapters.zip(values)
                    .sortedByDescending { it.second }
                    .map { it.first }
            }
        }

        val dates = chapters.map { it.publishedAtEpochMillis ?: return null }
        if (dates.distinct().size == dates.size && isStrictlyOrderedDates(dates)) {
            return chapters.zip(dates)
                .sortedByDescending { it.second }
                .map { it.first }
        }
        return null
    }

    private fun isStrictlyOrderedNumbers(values: List<BigDecimal>): Boolean =
        values.zipWithNext().all { (first, second) -> first > second } ||
            values.zipWithNext().all { (first, second) -> first < second }

    private fun isStrictlyOrderedDates(values: List<Long>): Boolean =
        values.zipWithNext().all { (first, second) -> first > second } ||
            values.zipWithNext().all { (first, second) -> first < second }

    private fun isSameHost(firstUrl: String, secondUrl: String): Boolean {
        val firstHost = DomainNormalizer.normalizedHostFromUrl(firstUrl)
        return firstHost != null && firstHost == DomainNormalizer.normalizedHostFromUrl(secondUrl)
    }

    private fun isFragmentOnlyChapterLink(url: String, pageUrl: String): Boolean =
        try {
            val chapterUri = URI(url)
            val pageUri = URI(pageUrl)
            chapterUri.path == pageUri.path &&
                chapterUri.rawQuery == pageUri.rawQuery &&
                chapterUri.rawFragment != null
        } catch (_: URISyntaxException) {
            true
        }

    internal fun chapterSeriesKey(rawUrl: String): String? {
        val uri = try {
            URI(rawUrl).normalize()
        } catch (_: URISyntaxException) {
            return null
        }
        val pathSegments = uri.path.orEmpty().trimEnd('/')
            .split('/')
            .filter(String::isNotEmpty)
        val lastSegment = pathSegments.lastOrNull().orEmpty()
        val previousSegment = pathSegments.dropLast(1).lastOrNull().orEmpty()
        val chapterPostSlugBase = chapterPostSlugBase(lastSegment)
        val segmentsToRemove = when {
            chapterPostSlugBase != null -> 0
            CHAPTER_PATH_SEGMENT_PATTERN.matches(lastSegment) -> 1
            lastSegment.matches(NUMERIC_CHAPTER_SLUG_PATTERN) &&
                previousSegment.matches(CHAPTER_ROUTE_SEGMENT_PATTERN) -> 2
            lastSegment.matches(NUMERIC_CHAPTER_SEGMENT_PATTERN) -> 1
            else -> 0
        }
        val path = when {
            chapterPostSlugBase != null ->
                "/" + (pathSegments.dropLast(1) + chapterPostSlugBase).joinToString("/")
            segmentsToRemove == 0 ->
                uri.path.orEmpty().trimEnd('/').ifEmpty { "/" }
            else -> "/" + pathSegments.dropLast(segmentsToRemove).joinToString("/")
        }
        val queryContext = uri.rawQuery.orEmpty()
            .split('&')
            .filter(String::isNotBlank)
            .filterNot { parameter ->
                parameter.substringBefore('=').lowercase(Locale.ROOT) in CHAPTER_QUERY_KEYS
            }
            .sorted()
            .joinToString("&")
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        return buildString {
            append(host)
            if (uri.port >= 0) append(':').append(uri.port)
            append(path)
            if (queryContext.isNotEmpty()) append('?').append(queryContext)
        }
    }

    private fun deriveSeriesUrl(rawUrl: String): String? {
        val uri = try {
            URI(rawUrl)
        } catch (_: URISyntaxException) {
            return null
        }
        val pathSegments = uri.path.orEmpty().trimEnd('/')
            .split('/')
            .filter(String::isNotEmpty)
        val lastSegment = pathSegments.lastOrNull().orEmpty()
        val previousSegment = pathSegments.dropLast(1).lastOrNull().orEmpty()
        val chapterPostSlugBase = chapterPostSlugBase(lastSegment)
        val segmentsToRemove = when {
            chapterPostSlugBase != null -> 0
            CHAPTER_PATH_SEGMENT_PATTERN.matches(lastSegment) -> 1
            lastSegment.matches(NUMERIC_CHAPTER_SLUG_PATTERN) &&
                previousSegment.matches(CHAPTER_ROUTE_SEGMENT_PATTERN) -> 2
            else -> 0
        }
        val queryContext = uri.rawQuery.orEmpty()
            .split('&')
            .filter(String::isNotBlank)
            .filterNot { parameter ->
                parameter.substringBefore('=').lowercase(Locale.ROOT) in CHAPTER_QUERY_KEYS
            }
            .joinToString("&")
        if (chapterPostSlugBase == null &&
            segmentsToRemove == 0 &&
            queryContext == uri.rawQuery.orEmpty()
        ) {
            return null
        }

        val path = when {
            chapterPostSlugBase != null ->
                "/" + (pathSegments.dropLast(1) + chapterPostSlugBase).joinToString("/")
            segmentsToRemove == 0 ->
                uri.path.orEmpty().trimEnd('/').ifEmpty { "/" }
            else -> "/" + pathSegments.dropLast(segmentsToRemove).joinToString("/")
        }
        return try {
            URI(uri.scheme, null, uri.host, uri.port, path, queryContext.ifEmpty { null }, null)
                .toASCIIString()
        } catch (_: URISyntaxException) {
            null
        }
    }

    private fun Element.isChapterNavigation(): Boolean {
        val linkLabel = "${text()} ${attr("title")}".lowercase(Locale.ROOT)
        if (NAVIGATION_DIRECTION_PATTERN.containsMatchIn(linkLabel) ||
            attr("rel").lowercase(Locale.ROOT) in setOf("prev", "next")
        ) {
            return true
        }
        var current: Element? = this
        repeat(8) {
            val element = current ?: return false
            val elementClass = "${element.id()} ${element.className()} ${element.attr("role")}"
            if (element.normalName() in NAVIGATION_TAGS ||
                NAVIGATION_CLASS_PATTERN.containsMatchIn(elementClass)
            ) {
                return true
            }
            current = element.parent()
        }
        return false
    }

    private fun String.stripChapterSuffix(): String? {
        val match = CHAPTER_SUFFIX_PATTERN.find(this) ?: return trim().takeIf(String::isNotEmpty)
        return substring(0, match.range.first).trim().takeIf(String::isNotEmpty)
    }

    private fun chapterPostSlugBase(segment: String): String? {
        val match = CHAPTER_POST_SLUG_SUFFIX_PATTERN.find(segment) ?: return null
        if (match.range.last != segment.lastIndex) return null
        return segment.substring(0, match.range.first).trim('-', '_')
            .takeIf(String::isNotEmpty)
    }

    private fun parseCommonDate(value: String): Long? {
        try {
            return DateTimeFormatter.RFC_1123_DATE_TIME.parse(value, Instant::from).toEpochMilli()
        } catch (_: DateTimeParseException) {
            Unit
        }
        val formatters = listOf(
            DateTimeFormatter.RFC_1123_DATE_TIME,
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT),
            DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.ROOT),
        )
        return formatters.firstNotNullOfOrNull { formatter ->
            try {
                LocalDate.parse(value, formatter)
                    .atStartOfDay(java.time.ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli()
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }

    private fun JSONObject.hasType(expected: String): Boolean {
        val type = opt("@type")
        return when (type) {
            is JSONArray -> (0 until type.length()).any {
                type.optString(it).equals(expected, ignoreCase = true)
            }
            is String -> type.equals(expected, ignoreCase = true)
            else -> false
        }
    }

    private fun JSONObject.readString(key: String): String? {
        val value = opt(key)
        return when (value) {
            is String -> value.trim().takeIf(String::isNotEmpty)
            is JSONObject -> value.readString("name") ?: value.readString("url")
            else -> null
        }
    }

    private fun JSONObject.imageUrl(): String? {
        val image = opt("image")
        return when (image) {
            is String -> image
            is JSONObject -> image.readString("url") ?: image.readString("@id")
            is JSONArray -> (0 until image.length()).firstNotNullOfOrNull { index ->
                when (val item = image.opt(index)) {
                    is String -> item
                    is JSONObject -> item.readString("url") ?: item.readString("@id")
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun JSONObject.readItemList(baseUrl: String): ChapterList? {
        if (!hasType("ItemList")) return null
        val items = optJSONArray("itemListElement") ?: return null
        val declaredItemCount = (opt("numberOfItems") as? Number)
            ?.toDouble()
            ?.takeIf { it >= 0 && it <= Int.MAX_VALUE && it % 1.0 == 0.0 }
            ?.toInt()
        val chapters = mutableListOf<ParsedChapter>()
        var allItemsValid = items.length() > 0
        for (index in 0 until items.length()) {
            val listEntry = items.optJSONObject(index)
            val item = listEntry?.optJSONObject("item") ?: listEntry
            val rawUrl = item?.readString("url") ?: item?.readString("@id")
            val url = rawUrl?.let { resolveHttpUrl(it, baseUrl) }
            val label = item?.readString("name") ?: item?.readString("headline")
            val chapterIdentity = label?.let(::findChapterIdentity)
                ?: rawUrl?.let(::findChapterIdentity)
            val finalLabel = if (chapterIdentity == null) {
                null
            } else {
                when {
                    label == null -> chapterIdentity.first
                    label.contains(chapterIdentity.first, ignoreCase = true) -> label
                    else -> "${chapterIdentity.first} — $label"
                }
            }
            if (url == null || finalLabel == null) {
                allItemsValid = false
                continue
            }
            chapters += ParsedChapter(
                sourceKey = url,
                label = finalLabel,
                url = url,
                publishedAtEpochMillis = item.readString("datePublished")?.let(::parseEpochMillis),
            )
        }
        val uniqueChapters = chapters.distinctBy(ParsedChapter::sourceKey)
        val isComplete = allItemsValid &&
            uniqueChapters.isNotEmpty() &&
            uniqueChapters.size == items.length() &&
            declaredItemCount == items.length()
        val orderedChapters = orderNewestFirst(uniqueChapters)
        return ChapterList(
            chapters = orderedChapters ?: uniqueChapters,
            isComplete = isComplete,
            isLatestChapterKnown = orderedChapters != null,
        )
    }

    private data class ChapterList(
        val chapters: List<ParsedChapter>,
        val isComplete: Boolean,
        val isLatestChapterKnown: Boolean = false,
    )

    private data class HtmlChapterCandidate(
        val seriesKey: String,
        val chapter: ParsedChapter,
    )

    private val CHAPTER_IDENTITY_PATTERN = Regex(
        pattern = """(?i)(?:^|[^a-z])((?:chapter|chap\.?|ch\.?|cap(?:i|\x{ed})tulo|cap\.?|episode|epis(?:o|\x{f3})dio|ep\.?))[-_/\s.:#=]+((?:[0-9]+(?:[.,-][0-9]+)?(?:[a-z])?)|(?:special|extra|bonus|side[-_ ]?story)(?:[-_/\s.:#=]*[0-9]+)?)""",
    )
    private val CHAPTER_SUFFIX_PATTERN = Regex(
        pattern = """(?i)\s+(?:chapter|chap\.?|ch\.?|cap(?:i|\x{ed})tulo|cap\.?|episode|epis(?:o|\x{f3})dio|ep\.?)[-_/\s.:#=]+(?:(?:[0-9]+(?:[.,-][0-9]+)?(?:[a-z])?)|(?:special|extra|bonus|side[-_ ]?story)(?:[-_/\s.:#=]*[0-9]+)?).*$""",
    )
    private val CHAPTER_PATH_SEGMENT_PATTERN = Regex(
        pattern = """(?i)(?:chapter|chap|ch|cap(?:i|\x{ed})tulo|cap|episode|ep)[-_.]?(?:[0-9]+(?:[.,-][0-9]+)?[a-z]?|special|extra|bonus|side[-_]?story)(?:[-_][a-z0-9]+)*(?:\.html?)?""",
    )
    private val NUMERIC_CHAPTER_SEGMENT_PATTERN = Regex(
        pattern = """[0-9]+(?:[.,-][0-9]+)?[a-z]?""",
        option = RegexOption.IGNORE_CASE,
    )
    private val NUMERIC_CHAPTER_SLUG_PATTERN = Regex(
        pattern = """[0-9]+(?:[.,-][0-9]+)?[a-z]?(?:[-_][a-z0-9]+)*""",
        option = RegexOption.IGNORE_CASE,
    )
    private val CHAPTER_POST_SLUG_SUFFIX_PATTERN = Regex(
        pattern = """(?i)(?:[-_](?:chapter|chap|ch|cap(?:i|\x{ed})tulo|cap|episode|ep)[-_]?(?:[0-9]+(?:[.,-][0-9]+)?[a-z]?|special|extra|bonus|side[-_]?story)(?:[-_][a-z0-9]+)*)$""",
    )
    private val HYPHEN_DECIMAL_PATTERN = Regex(
        pattern = """^([0-9]+)-([0-9]+)([a-z]?)$""",
        option = RegexOption.IGNORE_CASE,
    )
    private val CHAPTER_ROUTE_SEGMENT_PATTERN = Regex(
        pattern = """(?:chapter|chap|ch|cap(?:i|\x{ed})tulo|cap|episode|ep)""",
        option = RegexOption.IGNORE_CASE,
    )
    private val CHAPTER_QUERY_KEYS = setOf(
        "chapter",
        "chapter_id",
        "chapter_no",
        "chap",
        "chap_id",
        "ch",
        "capitulo",
        "cap\u00edtulo",
        "episode",
        "ep",
    )
    private val NAVIGATION_DIRECTION_PATTERN = Regex(
        pattern = """\b(?:next|previous|prev|first|last)\b""",
        option = RegexOption.IGNORE_CASE,
    )
    private val NAVIGATION_CLASS_PATTERN = Regex(
        pattern = """(?:^|[\s_-])(?:navigation|nav|breadcrumb|pagination|related|recommend(?:ed|ations)?|similar|menu)(?:$|[\s_-])""",
        option = RegexOption.IGNORE_CASE,
    )
    private val NAVIGATION_TAGS = setOf("nav", "header", "footer", "aside")
}
