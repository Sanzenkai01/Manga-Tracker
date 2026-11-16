package com.sanzenkai.mangatracker.data.backup

import android.app.Application
import com.sanzenkai.mangatracker.core.domain.model.CheckAttempt
import com.sanzenkai.mangatracker.core.domain.model.Manga
import com.sanzenkai.mangatracker.core.domain.model.MangaBackupEntry
import com.sanzenkai.mangatracker.core.domain.model.MangaBackupSnapshot
import com.sanzenkai.mangatracker.core.domain.model.MonitoringSettings
import com.sanzenkai.mangatracker.core.domain.model.ReadingAction
import com.sanzenkai.mangatracker.core.domain.model.ReadingHistoryEntry
import com.sanzenkai.mangatracker.core.domain.model.SiteConfiguration
import com.sanzenkai.mangatracker.core.domain.model.TrackedChapter
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class MangaBackupJsonCodecTest {
    @Test
    fun encodedBackupIncludesLibraryProgressAndSavedConfiguration() {
        val backup = MangaBackupSnapshot(
            exportedAtEpochMillis = 100L,
            mangas = listOf(
                MangaBackupEntry(
                    manga = Manga(
                        id = "manga-1",
                        title = "A manga",
                        originalCoverUrl = null,
                        seriesUrl = "https://example.test/series",
                        entryChapterUrl = "https://example.test/chapter/1",
                        normalizedDomain = "example.test",
                        createdAtEpochMillis = 1L,
                        updatedAtEpochMillis = 2L,
                    ),
                    chapters = listOf(
                        TrackedChapter(
                            id = "chapter-1",
                            mangaId = "manga-1",
                            sourceChapterKey = "chapter-1",
                            label = "Chapter 1",
                            chapterKind = "NUMBERED",
                            normalizedNumber = "1",
                            url = "https://example.test/chapter/1",
                            publishedAtEpochMillis = null,
                            firstDetectedAtEpochMillis = 10L,
                            notificationPostedAtEpochMillis = null,
                            isRead = true,
                        ),
                    ),
                    readingHistory = listOf(
                        ReadingHistoryEntry(
                            id = 5L,
                            chapterId = "chapter-1",
                            action = ReadingAction.READ,
                            origin = "DETAIL_SCREEN",
                            occurredAtEpochMillis = 20L,
                        ),
                    ),
                    monitoringSettings = MonitoringSettings(
                        mangaId = "manga-1",
                        isEnabled = true,
                        lastAttemptAtEpochMillis = 30L,
                        lastSuccessAtEpochMillis = 30L,
                        lastOutcome = "SUCCESS",
                    ),
                    checkAttempts = listOf(
                        CheckAttempt(
                            id = "attempt-1",
                            mangaId = "manga-1",
                            trigger = "MANUAL",
                            startedAtEpochMillis = 30L,
                            finishedAtEpochMillis = 30L,
                            outcome = "SUCCESS",
                            errorCode = null,
                        ),
                    ),
                ),
            ),
            siteConfigurations = listOf(
                SiteConfiguration(
                    normalizedHost = "example.test",
                    parserId = "manual-css",
                    formatVersion = 2,
                    configJson = """{"chapter":"a.chapter"}""",
                    updatedAtEpochMillis = 40L,
                    validatedAtEpochMillis = null,
                ),
            ),
        )

        val encoded = MangaBackupJsonCodec.encode(backup)
        assertEquals(backup, MangaBackupJsonCodec.decode(encoded))

        val json = JSONObject(encoded)
        val mangaEntry = json.getJSONArray("mangas").getJSONObject(0)
        val chapter = mangaEntry.getJSONArray("chapters").getJSONObject(0)
        val configuration = json.getJSONArray("siteConfigurations").getJSONObject(0)

        assertEquals(MangaBackupSnapshot.CURRENT_FORMAT_VERSION, json.getInt("formatVersion"))
        assertEquals(100L, json.getLong("exportedAtEpochMillis"))
        assertTrue(mangaEntry.getJSONObject("manga").isNull("originalCoverUrl"))
        assertTrue(chapter.getBoolean("isRead"))
        assertEquals("READ", mangaEntry.getJSONArray("readingHistory").getJSONObject(0).getString("action"))
        assertTrue(mangaEntry.getJSONObject("monitoringSettings").getBoolean("isEnabled"))
        assertEquals("manual-css", configuration.getString("parserId"))
        assertTrue(configuration.isNull("validatedAtEpochMillis"))

        val unsafeUrlBackup = backup.copy(
            mangas = backup.mangas.map { entry ->
                entry.copy(
                    manga = entry.manga.copy(entryChapterUrl = "file:///private/backup.json"),
                )
            },
        )
        assertThrows(IllegalArgumentException::class.java) {
            MangaBackupJsonCodec.decode(MangaBackupJsonCodec.encode(unsafeUrlBackup))
        }
    }

    @Test
    fun decodeRejectsChapterReferencesThatDoNotBelongToTheirManga() {
        val backup = MangaBackupSnapshot(
            exportedAtEpochMillis = 100L,
            mangas = listOf(
                MangaBackupEntry(
                    manga = Manga(
                        id = "manga-1",
                        title = "A manga",
                        originalCoverUrl = null,
                        seriesUrl = "https://example.test/series",
                        entryChapterUrl = "https://example.test/chapter/1",
                        normalizedDomain = "example.test",
                        createdAtEpochMillis = 1L,
                        updatedAtEpochMillis = 2L,
                    ),
                    chapters = listOf(
                        TrackedChapter(
                            id = "chapter-1",
                            mangaId = "other-manga",
                            sourceChapterKey = "chapter-1",
                            label = "Chapter 1",
                            chapterKind = "NUMBERED",
                            normalizedNumber = "1",
                            url = "https://example.test/chapter/1",
                            publishedAtEpochMillis = null,
                            firstDetectedAtEpochMillis = 10L,
                            notificationPostedAtEpochMillis = null,
                            isRead = false,
                        ),
                    ),
                    readingHistory = emptyList(),
                    monitoringSettings = null,
                    checkAttempts = emptyList(),
                ),
            ),
            siteConfigurations = emptyList(),
        )

        assertThrows(IllegalArgumentException::class.java) {
            MangaBackupJsonCodec.decode(MangaBackupJsonCodec.encode(backup))
        }
    }

    @Test
    fun decodeRejectsUnsupportedBackupVersions() {
        val json = JSONObject()
            .put("format", "com.sanzenkai.mangatracker.backup")
            .put("formatVersion", MangaBackupSnapshot.CURRENT_FORMAT_VERSION + 1)
            .put("exportedAtEpochMillis", 100L)
            .put("mangas", JSONArray())
            .put("siteConfigurations", JSONArray())

        assertThrows(IllegalArgumentException::class.java) {
            MangaBackupJsonCodec.decode(json.toString())
        }
    }
}
