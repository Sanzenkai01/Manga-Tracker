package com.sanzenkai.mangatracker.data.backup

import com.sanzenkai.mangatracker.core.domain.model.CheckAttempt
import com.sanzenkai.mangatracker.core.domain.model.Manga
import com.sanzenkai.mangatracker.core.domain.model.MangaBackupEntry
import com.sanzenkai.mangatracker.core.domain.model.MangaBackupSnapshot
import com.sanzenkai.mangatracker.core.domain.model.MonitoringSettings
import com.sanzenkai.mangatracker.core.domain.model.ReadingAction
import com.sanzenkai.mangatracker.core.domain.model.ReadingHistoryEntry
import com.sanzenkai.mangatracker.core.domain.model.SiteConfiguration
import com.sanzenkai.mangatracker.core.domain.model.TrackedChapter
import java.net.URI
import java.net.URISyntaxException
import org.json.JSONArray
import org.json.JSONObject

object MangaBackupJsonCodec {
    private const val FORMAT_NAME = "com.sanzenkai.mangatracker.backup"

    fun encode(snapshot: MangaBackupSnapshot): String =
        JSONObject()
            .put("format", FORMAT_NAME)
            .put("formatVersion", snapshot.formatVersion)
            .put("exportedAtEpochMillis", snapshot.exportedAtEpochMillis)
            .put(
                "mangas",
                JSONArray().apply {
                    snapshot.mangas.forEach { put(it.toJson()) }
                },
            )
            .put(
                "siteConfigurations",
                JSONArray().apply {
                    snapshot.siteConfigurations.forEach { configuration ->
                        put(
                            JSONObject()
                                .put("normalizedHost", configuration.normalizedHost)
                                .put("parserId", configuration.parserId)
                                .put("formatVersion", configuration.formatVersion)
                                .put("configJson", configuration.configJson)
                                .put("updatedAtEpochMillis", configuration.updatedAtEpochMillis)
                                .putNullable(
                                    "validatedAtEpochMillis",
                                    configuration.validatedAtEpochMillis,
                                ),
                        )
                    }
                },
            )
            .toString(2)

    fun decode(json: String): MangaBackupSnapshot {
        val root = JSONObject(json)
        require(root.requiredString("format") == FORMAT_NAME) {
            "The selected file is not a Manga Tracker backup."
        }
        val formatVersion = root.getInt("formatVersion")
        require(formatVersion == MangaBackupSnapshot.CURRENT_FORMAT_VERSION) {
            "The backup format version is not supported."
        }
        val snapshot = MangaBackupSnapshot(
            exportedAtEpochMillis = root.requiredLong("exportedAtEpochMillis"),
            mangas = root.requiredArray("mangas").mapObjects { mangaEntry ->
                val manga = mangaEntry.getJSONObject("manga").toManga()
                val chapters = mangaEntry.requiredArray("chapters").mapObjects { it.toChapter() }
                val history = mangaEntry.requiredArray("readingHistory").mapObjects {
                    it.toReadingHistoryEntry()
                }
                require(mangaEntry.has("monitoringSettings")) {
                    "The backup is missing 'monitoringSettings'."
                }
                val monitoring = if (mangaEntry.isNull("monitoringSettings")) {
                    null
                } else {
                    mangaEntry.getJSONObject("monitoringSettings").toMonitoringSettings()
                }
                val attempts = mangaEntry.requiredArray("checkAttempts").mapObjects {
                    it.toCheckAttempt()
                }
                MangaBackupEntry(manga, chapters, history, monitoring, attempts)
            },
            siteConfigurations = root.requiredArray("siteConfigurations").mapObjects {
                it.toSiteConfiguration()
            },
            formatVersion = formatVersion,
        )
        validate(snapshot)
        return snapshot
    }

    private fun JSONObject.toManga() =
        Manga(
            id = requiredString("id"),
            title = requiredString("title"),
            originalCoverUrl = nullableString("originalCoverUrl").also { it?.requireWebUrl() },
            seriesUrl = nullableString("seriesUrl").also { it?.requireWebUrl() },
            entryChapterUrl = requiredString("entryChapterUrl").also { it.requireWebUrl() },
            normalizedDomain = requiredString("normalizedDomain").also {
                require(it.none(Char::isWhitespace) && '/' !in it) {
                    "The backup contains an invalid manga domain."
                }
            },
            createdAtEpochMillis = requiredLong("createdAtEpochMillis"),
            updatedAtEpochMillis = requiredLong("updatedAtEpochMillis"),
        )

    private fun JSONObject.toChapter() =
        TrackedChapter(
            id = requiredString("id"),
            mangaId = requiredString("mangaId"),
            sourceChapterKey = requiredString("sourceChapterKey"),
            label = requiredString("label"),
            chapterKind = requiredString("chapterKind"),
            normalizedNumber = nullableString("normalizedNumber"),
            url = requiredString("url").also { it.requireWebUrl() },
            publishedAtEpochMillis = nullableLong("publishedAtEpochMillis"),
            firstDetectedAtEpochMillis = requiredLong("firstDetectedAtEpochMillis"),
            notificationPostedAtEpochMillis = nullableLong("notificationPostedAtEpochMillis"),
            isRead = requiredBoolean("isRead"),
        )

    private fun JSONObject.toReadingHistoryEntry() =
        ReadingHistoryEntry(
            id = requiredLong("id"),
            chapterId = requiredString("chapterId"),
            action = ReadingAction.valueOf(requiredString("action")),
            origin = requiredString("origin"),
            occurredAtEpochMillis = requiredLong("occurredAtEpochMillis"),
        )

    private fun JSONObject.toMonitoringSettings() =
        MonitoringSettings(
            mangaId = requiredString("mangaId"),
            isEnabled = requiredBoolean("isEnabled"),
            lastAttemptAtEpochMillis = nullableLong("lastAttemptAtEpochMillis"),
            lastSuccessAtEpochMillis = nullableLong("lastSuccessAtEpochMillis"),
            lastOutcome = nullableString("lastOutcome"),
        )

    private fun JSONObject.toCheckAttempt() =
        CheckAttempt(
            id = requiredString("id"),
            mangaId = requiredString("mangaId"),
            trigger = requiredString("trigger"),
            startedAtEpochMillis = requiredLong("startedAtEpochMillis"),
            finishedAtEpochMillis = nullableLong("finishedAtEpochMillis"),
            outcome = requiredString("outcome"),
            errorCode = nullableString("errorCode"),
        )

    private fun JSONObject.toSiteConfiguration() =
        SiteConfiguration(
            normalizedHost = requiredString("normalizedHost"),
            parserId = requiredString("parserId"),
            formatVersion = getInt("formatVersion").also {
                require(it > 0) { "The backup contains an invalid site configuration." }
            },
            configJson = requiredString("configJson"),
            updatedAtEpochMillis = requiredLong("updatedAtEpochMillis"),
            validatedAtEpochMillis = nullableLong("validatedAtEpochMillis"),
        )

    private fun validate(snapshot: MangaBackupSnapshot) {
        val mangaIds = snapshot.mangas.map { it.manga.id }
        require(mangaIds.distinct().size == mangaIds.size) {
            "The backup contains duplicate manga IDs."
        }
        val chapterIds = snapshot.mangas.flatMap { entry -> entry.chapters.map { it.id } }
        require(chapterIds.distinct().size == chapterIds.size) {
            "The backup contains duplicate chapter IDs."
        }
        snapshot.mangas.forEach { entry ->
            require(entry.chapters.all { it.mangaId == entry.manga.id }) {
                "The backup contains a chapter assigned to the wrong manga."
            }
            require(entry.chapters.map { it.sourceChapterKey }.distinct().size == entry.chapters.size) {
                "The backup contains duplicate chapter keys."
            }
            val chapterIdsForManga = entry.chapters.mapTo(mutableSetOf()) { it.id }
            require(entry.readingHistory.all { it.chapterId in chapterIdsForManga }) {
                "The backup contains reading history for an unknown chapter."
            }
            val monitoringSettings = entry.monitoringSettings
            require(monitoringSettings == null || monitoringSettings.mangaId == entry.manga.id) {
                "The backup contains monitoring settings for the wrong manga."
            }
            require(entry.checkAttempts.all { it.mangaId == entry.manga.id }) {
                "The backup contains a check attempt for the wrong manga."
            }
        }
        val siteHosts = snapshot.siteConfigurations.map { it.normalizedHost }
        require(siteHosts.distinct().size == siteHosts.size) {
            "The backup contains duplicate site configurations."
        }
    }

    private fun JSONObject.requiredString(name: String): String {
        require(has(name) && !isNull(name)) { "The backup is missing '$name'." }
        return getString(name).also { value ->
            require(value.isNotBlank()) { "The backup contains an empty '$name' value." }
        }
    }

    private fun JSONObject.nullableString(name: String): String? {
        require(has(name)) { "The backup is missing '$name'." }
        return if (isNull(name)) null else getString(name)
    }

    private fun JSONObject.requiredLong(name: String): Long {
        require(has(name) && !isNull(name)) { "The backup is missing '$name'." }
        return getLong(name).also { value ->
            require(value >= 0L) { "The backup contains an invalid '$name' value." }
        }
    }

    private fun JSONObject.nullableLong(name: String): Long? {
        require(has(name)) { "The backup is missing '$name'." }
        return if (isNull(name)) null else getLong(name).also { value ->
            require(value >= 0L) { "The backup contains an invalid '$name' value." }
        }
    }

    private fun JSONObject.requiredBoolean(name: String): Boolean {
        require(has(name) && !isNull(name)) { "The backup is missing '$name'." }
        return getBoolean(name)
    }

    private fun JSONObject.requiredArray(name: String): JSONArray {
        require(has(name) && !isNull(name)) { "The backup is missing '$name'." }
        return getJSONArray(name)
    }

    private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
        (0 until length()).map { index -> transform(getJSONObject(index)) }

    private fun String.requireWebUrl() {
        val uri = try {
            URI(this)
        } catch (error: URISyntaxException) {
            throw IllegalArgumentException("The backup contains an invalid web URL.", error)
        }
        require(
            (uri.scheme.equals("http", ignoreCase = true) ||
                uri.scheme.equals("https", ignoreCase = true)) &&
                !uri.host.isNullOrBlank() &&
                uri.userInfo == null,
        ) {
            "The backup contains an invalid web URL."
        }
    }

    private fun MangaBackupEntry.toJson(): JSONObject =
        JSONObject()
            .put(
                "manga",
                JSONObject()
                    .put("id", manga.id)
                    .put("title", manga.title)
                    .putNullable("originalCoverUrl", manga.originalCoverUrl)
                    .putNullable("seriesUrl", manga.seriesUrl)
                    .put("entryChapterUrl", manga.entryChapterUrl)
                    .put("normalizedDomain", manga.normalizedDomain)
                    .put("createdAtEpochMillis", manga.createdAtEpochMillis)
                    .put("updatedAtEpochMillis", manga.updatedAtEpochMillis),
            )
            .put(
                "chapters",
                JSONArray().apply {
                    chapters.forEach { chapter ->
                        put(
                            JSONObject()
                                .put("id", chapter.id)
                                .put("mangaId", chapter.mangaId)
                                .put("sourceChapterKey", chapter.sourceChapterKey)
                                .put("label", chapter.label)
                                .put("chapterKind", chapter.chapterKind)
                                .putNullable("normalizedNumber", chapter.normalizedNumber)
                                .put("url", chapter.url)
                                .putNullable("publishedAtEpochMillis", chapter.publishedAtEpochMillis)
                                .put("firstDetectedAtEpochMillis", chapter.firstDetectedAtEpochMillis)
                                .putNullable(
                                    "notificationPostedAtEpochMillis",
                                    chapter.notificationPostedAtEpochMillis,
                                )
                                .put("isRead", chapter.isRead),
                        )
                    }
                },
            )
            .put(
                "readingHistory",
                JSONArray().apply {
                    readingHistory.forEach { entry ->
                        put(
                            JSONObject()
                                .put("id", entry.id)
                                .put("chapterId", entry.chapterId)
                                .put("action", entry.action.name)
                                .put("origin", entry.origin)
                                .put("occurredAtEpochMillis", entry.occurredAtEpochMillis),
                        )
                    }
                },
            )
            .put(
                "monitoringSettings",
                monitoringSettings?.let { settings ->
                    JSONObject()
                        .put("mangaId", settings.mangaId)
                        .put("isEnabled", settings.isEnabled)
                        .putNullable("lastAttemptAtEpochMillis", settings.lastAttemptAtEpochMillis)
                        .putNullable("lastSuccessAtEpochMillis", settings.lastSuccessAtEpochMillis)
                        .putNullable("lastOutcome", settings.lastOutcome)
                } ?: JSONObject.NULL,
            )
            .put(
                "checkAttempts",
                JSONArray().apply {
                    checkAttempts.forEach { attempt ->
                        put(
                            JSONObject()
                                .put("id", attempt.id)
                                .put("mangaId", attempt.mangaId)
                                .put("trigger", attempt.trigger)
                                .put("startedAtEpochMillis", attempt.startedAtEpochMillis)
                                .putNullable("finishedAtEpochMillis", attempt.finishedAtEpochMillis)
                                .put("outcome", attempt.outcome)
                                .putNullable("errorCode", attempt.errorCode),
                        )
                    }
                },
            )

    private fun JSONObject.putNullable(name: String, value: Any?): JSONObject =
        put(name, value ?: JSONObject.NULL)
}
