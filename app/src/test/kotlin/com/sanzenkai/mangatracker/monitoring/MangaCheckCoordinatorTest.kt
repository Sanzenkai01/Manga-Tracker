package com.sanzenkai.mangatracker.monitoring

import com.sanzenkai.mangatracker.core.data.network.HtmlDocumentFetcher
import com.sanzenkai.mangatracker.core.data.network.HtmlFetchFailureCode
import com.sanzenkai.mangatracker.core.data.network.HtmlFetchResult
import com.sanzenkai.mangatracker.core.domain.model.CheckAttempt
import com.sanzenkai.mangatracker.core.domain.model.ChapterSyncResult
import com.sanzenkai.mangatracker.core.domain.model.DiscoveredChapter
import com.sanzenkai.mangatracker.core.domain.model.Manga
import com.sanzenkai.mangatracker.core.domain.model.MangaMetadataUpdate
import com.sanzenkai.mangatracker.core.domain.model.MangaBackupSnapshot
import com.sanzenkai.mangatracker.core.domain.model.MangaRegistrationResult
import com.sanzenkai.mangatracker.core.domain.model.MangaSummary
import com.sanzenkai.mangatracker.core.domain.model.MonitoringProblemSignals
import com.sanzenkai.mangatracker.core.domain.model.MonitoringSettings
import com.sanzenkai.mangatracker.core.domain.model.ReadingAction
import com.sanzenkai.mangatracker.core.domain.model.ReadingActionOrigin
import com.sanzenkai.mangatracker.core.domain.model.ReadingHistoryEntry
import com.sanzenkai.mangatracker.core.domain.model.SiteConfiguration
import com.sanzenkai.mangatracker.core.domain.model.TrackedChapter
import com.sanzenkai.mangatracker.core.domain.model.ValidatedChapterSnapshot
import com.sanzenkai.mangatracker.core.domain.repository.MangaRepository
import com.sanzenkai.mangatracker.core.domain.repository.SiteProblemNotificationRepository
import com.sanzenkai.mangatracker.core.parser.api.ParsedChapter
import com.sanzenkai.mangatracker.core.parser.api.ParsedSitePage
import com.sanzenkai.mangatracker.core.parser.api.ParserDocument
import com.sanzenkai.mangatracker.core.parser.api.ParserMatch
import com.sanzenkai.mangatracker.core.parser.api.SiteParser
import com.sanzenkai.mangatracker.core.parser.api.SiteParseResult
import com.sanzenkai.mangatracker.core.parser.engine.ParserRegistry
import com.sanzenkai.mangatracker.core.parser.engine.SiteDiscoveryEngine
import com.sanzenkai.mangatracker.notifications.ChapterNotificationPublisher
import com.sanzenkai.mangatracker.notifications.NoOpChapterNotificationPublisher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MangaCheckCoordinatorTest {
    @Test
    fun incompleteParseRecordsItsOwnOutcomeAndNeverPersistsAChapterSnapshot() = runTest {
        val manga = testManga()
        val repository = RecordingMangaRepository(manga)
        val document = testDocument(manga)
        val coordinator = coordinator(
            repository = repository,
            document = document,
            page = ParsedSitePage(
                title = manga.title,
                originalCoverUrl = null,
                seriesUrl = manga.seriesUrl,
                currentChapter = parsedChapter("chapter-1", 1, document.pageUrl),
                chapters = emptyList(),
                isChapterListComplete = false,
            ),
            parserDispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = coordinator.check(
            mangaId = manga.id,
            trigger = "MANUAL",
            requireMonitoringEnabled = false,
        )

        assertTrue(result is MangaCheckResult.Failure)
        assertEquals(
            MANGA_CHECK_ERROR_INCOMPLETE_CHAPTER_LIST,
            (result as MangaCheckResult.Failure).errorCode,
        )
        assertFalse((result as MangaCheckResult.Failure).retryable)
        assertTrue(repository.savedSnapshots.isEmpty())
        assertEquals(MANGA_CHECK_OUTCOME_INCOMPLETE_LIST, repository.attempts.single().outcome)
    }

    @Test
    fun successfulParseFillsMissingMetadataEvenWhenChapterListIsIncomplete() = runTest {
        val manga = testManga().copy(originalCoverUrl = null)
        val repository = RecordingMangaRepository(manga)
        val document = testDocument(manga)
        val coverUrl = "https://cdn.example.test/covers/sample.jpg"
        val coordinator = coordinator(
            repository = repository,
            document = document,
            page = ParsedSitePage(
                title = "A replacement title",
                originalCoverUrl = coverUrl,
                seriesUrl = manga.seriesUrl,
                currentChapter = parsedChapter("chapter-1", 1, document.pageUrl),
                chapters = emptyList(),
                isChapterListComplete = false,
            ),
            parserDispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = coordinator.check(
            mangaId = manga.id,
            trigger = "AUTOMATIC",
            requireMonitoringEnabled = true,
        )

        assertTrue(result is MangaCheckResult.Failure)
        assertEquals(MANGA_CHECK_ERROR_INCOMPLETE_CHAPTER_LIST, (result as MangaCheckResult.Failure).errorCode)
        assertEquals("Sample Manga", repository.manga.title)
        assertEquals(coverUrl, repository.manga.originalCoverUrl)
        assertEquals(100L, repository.manga.updatedAtEpochMillis)
        assertTrue(repository.savedSnapshots.isEmpty())
    }

    @Test
    fun completeChecksCountOnlyChapterKeysNotPreviouslyPersisted() = runTest {
        val manga = testManga()
        val repository = RecordingMangaRepository(manga)
        val document = testDocument(manga)
        val requestedUrls = mutableListOf<String>()
        val chapters = listOf(
            parsedChapter("chapter-1", 1, "https://example.test/manga/sample/chapter-1"),
            parsedChapter("chapter-2", 2, "https://example.test/manga/sample/chapter-2"),
        )
        val coordinator = coordinator(
            repository = repository,
            document = document,
            page = ParsedSitePage(
                title = manga.title,
                originalCoverUrl = null,
                seriesUrl = manga.seriesUrl,
                currentChapter = chapters.last(),
                chapters = chapters,
                isChapterListComplete = true,
            ),
            parserDispatcher = StandardTestDispatcher(testScheduler),
            onFetch = { requestedUrls += it },
        )

        val firstCheck = coordinator.check(manga.id, "AUTOMATIC", requireMonitoringEnabled = false)
        val secondCheck = coordinator.check(manga.id, "AUTOMATIC", requireMonitoringEnabled = false)

        assertEquals(2, (firstCheck as MangaCheckResult.Success).newChapterCount)
        assertEquals(0, (secondCheck as MangaCheckResult.Success).newChapterCount)
        assertEquals(2, repository.savedSnapshots.size)
        assertEquals(listOf("SUCCESS", "SUCCESS"), repository.attempts.map { it.outcome })
        assertEquals(listOf(manga.seriesUrl, manga.seriesUrl), requestedUrls)
    }

    @Test
    fun latestChapterWindowAddsOnlyChaptersAfterTheSavedBaseline() = runTest {
        val manga = testManga()
        val repository = RecordingMangaRepository(manga).apply {
            seedExistingChapter(parsedChapter("chapter-426", 426, "https://example.test/manga/sample/chapter-426"))
        }
        val document = testDocument(manga)
        val latestWindow = listOf(
            parsedChapter("chapter-428", 428, "https://example.test/manga/sample/chapter-428"),
            parsedChapter("chapter-427", 427, "https://example.test/manga/sample/chapter-427"),
            parsedChapter("chapter-426", 426, "https://example.test/manga/sample/chapter-426"),
            parsedChapter("chapter-425", 425, "https://example.test/manga/sample/chapter-425"),
        )
        val coordinator = coordinator(
            repository = repository,
            document = document,
            page = ParsedSitePage(
                title = manga.title,
                originalCoverUrl = null,
                seriesUrl = manga.seriesUrl,
                currentChapter = latestWindow.first(),
                chapters = latestWindow,
                isChapterListComplete = false,
                isLatestChapterKnown = true,
            ),
            parserDispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = coordinator.check(manga.id, "MANUAL", requireMonitoringEnabled = false)

        assertTrue(result is MangaCheckResult.Success)
        assertEquals(2, (result as MangaCheckResult.Success).newChapterCount)
        assertEquals(
            listOf("chapter-428", "chapter-427"),
            repository.savedSnapshots.single().chapters.map { it.sourceChapterKey },
        )
        assertEquals(
            listOf("chapter-426", "chapter-428", "chapter-427"),
            repository.chapters.map { it.sourceChapterKey },
        )
    }

    @Test
    fun latestChapterWindowUsesNumbersAfterSiteChangesKeysAndIgnoresExtras() = runTest {
        val manga = testManga()
        val repository = RecordingMangaRepository(manga).apply {
            seedExistingChapter(
                ParsedChapter(
                    sourceKey = "old-site-chapter",
                    label = "Chapter 3.5",
                    url = "https://example.test/manga/sample/chapter-3-5",
                    publishedAtEpochMillis = null,
                ),
            )
        }
        val document = testDocument(manga)
        val latestWindow = listOf(
            ParsedChapter(
                sourceKey = "site-chapter-4",
                label = "Solo Leveling 2 - Chapter 4",
                url = "https://example.test/manga/sample/chapter-4",
                publishedAtEpochMillis = null,
            ),
            ParsedChapter(
                sourceKey = "site-extra",
                label = "Extra 3",
                url = "https://example.test/manga/sample/extra-3",
                publishedAtEpochMillis = null,
            ),
            ParsedChapter(
                sourceKey = "site-chapter-3-5",
                label = "Chapter 3,5",
                url = "https://example.test/manga/sample/chapter-3-5",
                publishedAtEpochMillis = null,
            ),
        )
        val coordinator = coordinator(
            repository = repository,
            document = document,
            page = ParsedSitePage(
                title = manga.title,
                originalCoverUrl = null,
                seriesUrl = manga.seriesUrl,
                currentChapter = latestWindow.first(),
                chapters = latestWindow,
                isChapterListComplete = false,
                isLatestChapterKnown = true,
            ),
            parserDispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = coordinator.check(manga.id, "MANUAL", requireMonitoringEnabled = false)

        assertTrue(result is MangaCheckResult.Success)
        assertEquals(1, (result as MangaCheckResult.Success).newChapterCount)
        assertEquals(
            listOf("site-chapter-4"),
            repository.savedSnapshots.single().chapters.map { it.sourceChapterKey },
        )
    }

    @Test
    fun usesEntryChapterUrlWhenSeriesUrlIsNotOnTheTrackedHost() = runTest {
        val manga = testManga().copy(seriesUrl = "https://other.test/manga/sample")
        val repository = RecordingMangaRepository(manga)
        val document = testDocument(manga)
        var requestedUrl: String? = null
        val coordinator = coordinator(
            repository = repository,
            document = document,
            page = ParsedSitePage(
                title = manga.title,
                originalCoverUrl = null,
                seriesUrl = manga.seriesUrl,
                currentChapter = parsedChapter("chapter-1", 1, document.pageUrl),
                chapters = listOf(parsedChapter("chapter-1", 1, document.pageUrl)),
                isChapterListComplete = true,
            ),
            parserDispatcher = StandardTestDispatcher(testScheduler),
            onFetch = { requestedUrl = it },
        )

        coordinator.check(manga.id, "MANUAL", requireMonitoringEnabled = false)

        assertEquals(manga.entryChapterUrl, requestedUrl)
    }

    @Test
    fun pausingDuringFetchPreventsAutomaticSnapshotPersistence() = runTest {
        val manga = testManga()
        val repository = RecordingMangaRepository(manga)
        val document = testDocument(manga)
        val coordinator = coordinator(
            repository = repository,
            document = document,
            page = ParsedSitePage(
                title = manga.title,
                originalCoverUrl = null,
                seriesUrl = manga.seriesUrl,
                currentChapter = parsedChapter("chapter-1", 1, document.pageUrl),
                chapters = listOf(parsedChapter("chapter-1", 1, document.pageUrl)),
                isChapterListComplete = true,
            ),
            parserDispatcher = StandardTestDispatcher(testScheduler),
            onFetch = { repository.monitoringEnabled = false },
        )

        val result = coordinator.check(manga.id, "AUTOMATIC", requireMonitoringEnabled = true)

        assertTrue(result is MangaCheckResult.Skipped)
        assertTrue(repository.savedSnapshots.isEmpty())
    }

    @Test
    fun transientNetworkFailureIsRetryableAndDoesNotPersistAChapterSnapshot() = runTest {
        val manga = testManga()
        val repository = RecordingMangaRepository(manga)
        val document = testDocument(manga)
        val coordinator = coordinator(
            repository = repository,
            document = document,
            page = ParsedSitePage(
                title = manga.title,
                originalCoverUrl = null,
                seriesUrl = manga.seriesUrl,
                currentChapter = null,
                chapters = emptyList(),
                isChapterListComplete = false,
            ),
            parserDispatcher = StandardTestDispatcher(testScheduler),
            fetchResult = HtmlFetchResult.Failure(
                reason = "The network is temporarily unavailable.",
                code = HtmlFetchFailureCode.NETWORK_ERROR,
            ),
        )

        val result = coordinator.check(manga.id, "AUTOMATIC", requireMonitoringEnabled = false)

        assertTrue(result is MangaCheckResult.Failure)
        val failure = result as MangaCheckResult.Failure
        assertTrue(failure.retryable)
        assertEquals(HtmlFetchFailureCode.NETWORK_ERROR.name, failure.errorCode)
        assertTrue(repository.savedSnapshots.isEmpty())
        assertEquals("FAILURE", repository.attempts.single().outcome)
    }

    @Test
    fun siteProblemNotificationIsSentOnceAfterFiveEligibleFailures() = runTest {
        val manga = testManga()
        val repository = RecordingMangaRepository(manga)
        repeat(4) { index ->
            repository.attempts += CheckAttempt(
                id = "site-failure-$index",
                mangaId = manga.id,
                trigger = "AUTOMATIC",
                startedAtEpochMillis = index * 10L,
                finishedAtEpochMillis = index * 10L + 1,
                outcome = "FAILURE",
                errorCode = HtmlFetchFailureCode.PAGE_UNAVAILABLE.name,
            )
        }
        val publisher = RecordingChapterNotificationPublisher()
        val notificationRepository = RecordingSiteProblemNotificationRepository()
        val document = testDocument(manga)
        val coordinator = coordinator(
            repository = repository,
            document = document,
            page = ParsedSitePage(
                title = manga.title,
                originalCoverUrl = null,
                seriesUrl = manga.seriesUrl,
                currentChapter = null,
                chapters = emptyList(),
                isChapterListComplete = false,
            ),
            parserDispatcher = StandardTestDispatcher(testScheduler),
            fetchResult = HtmlFetchResult.Failure(
                reason = "The page is unavailable.",
                code = HtmlFetchFailureCode.PAGE_UNAVAILABLE,
            ),
            notificationPublisher = publisher,
            siteProblemNotificationRepository = notificationRepository,
        )

        coordinator.check(manga.id, "AUTOMATIC", requireMonitoringEnabled = true)
        coordinator.check(manga.id, "AUTOMATIC", requireMonitoringEnabled = true)

        assertEquals(listOf(manga.id to "site-failure-0"), publisher.siteProblemNotifications)
        assertEquals("site-failure-0", notificationRepository.claimedEpisodes[manga.id])
    }

    @Test
    fun networkFailuresAloneDoNotCreateASiteProblemNotification() = runTest {
        val manga = testManga()
        val repository = RecordingMangaRepository(manga)
        val publisher = RecordingChapterNotificationPublisher()
        val document = testDocument(manga)
        val coordinator = coordinator(
            repository = repository,
            document = document,
            page = ParsedSitePage(
                title = manga.title,
                originalCoverUrl = null,
                seriesUrl = manga.seriesUrl,
                currentChapter = null,
                chapters = emptyList(),
                isChapterListComplete = false,
            ),
            parserDispatcher = StandardTestDispatcher(testScheduler),
            fetchResult = HtmlFetchResult.Failure(
                reason = "The network is temporarily unavailable.",
                code = HtmlFetchFailureCode.NETWORK_ERROR,
            ),
            notificationPublisher = publisher,
            siteProblemNotificationRepository = RecordingSiteProblemNotificationRepository(),
        )

        coordinator.check(manga.id, "AUTOMATIC", requireMonitoringEnabled = true)

        assertTrue(publisher.siteProblemNotifications.isEmpty())
    }

    @Test
    fun successfulCheckEndsAnEpisodeAndLaterFailureEpisodeCanNotify() = runTest {
        val manga = testManga()
        val repository = RecordingMangaRepository(manga)
        repeat(4) { index ->
            repository.attempts += CheckAttempt(
                id = "first-site-failure-$index",
                mangaId = manga.id,
                trigger = "AUTOMATIC",
                startedAtEpochMillis = index * 10L,
                finishedAtEpochMillis = index * 10L + 1,
                outcome = "FAILURE",
                errorCode = HtmlFetchFailureCode.PAGE_UNAVAILABLE.name,
            )
        }
        val publisher = RecordingChapterNotificationPublisher()
        val notificationRepository = RecordingSiteProblemNotificationRepository()
        val document = testDocument(manga)
        val failurePage = ParsedSitePage(
            title = manga.title,
            originalCoverUrl = null,
            seriesUrl = manga.seriesUrl,
            currentChapter = null,
            chapters = emptyList(),
            isChapterListComplete = false,
        )

        coordinator(
            repository = repository,
            document = document,
            page = failurePage,
            parserDispatcher = StandardTestDispatcher(testScheduler),
            fetchResult = HtmlFetchResult.Failure("Unavailable.", HtmlFetchFailureCode.PAGE_UNAVAILABLE),
            notificationPublisher = publisher,
            siteProblemNotificationRepository = notificationRepository,
        ).check(manga.id, "AUTOMATIC", requireMonitoringEnabled = true)

        coordinator(
            repository = repository,
            document = document,
            page = ParsedSitePage(
                title = manga.title,
                originalCoverUrl = null,
                seriesUrl = manga.seriesUrl,
                currentChapter = parsedChapter("chapter-1", 1, document.pageUrl),
                chapters = listOf(parsedChapter("chapter-1", 1, document.pageUrl)),
                isChapterListComplete = true,
            ),
            parserDispatcher = StandardTestDispatcher(testScheduler),
            notificationPublisher = publisher,
            siteProblemNotificationRepository = notificationRepository,
        ).check(manga.id, "AUTOMATIC", requireMonitoringEnabled = true)

        repeat(4) { index ->
            repository.attempts += CheckAttempt(
                id = "second-site-failure-$index",
                mangaId = manga.id,
                trigger = "AUTOMATIC",
                startedAtEpochMillis = 110L + index * 10,
                finishedAtEpochMillis = 111L + index * 10,
                outcome = "FAILURE",
                errorCode = HtmlFetchFailureCode.PAGE_UNAVAILABLE.name,
            )
        }
        val nextEpisodeSignals = repository.observeMonitoringProblem(manga.id).first()
        assertEquals(4, nextEpisodeSignals.siteFailureCount)
        assertEquals("second-site-failure-0", nextEpisodeSignals.episodeId)
        val nextEpisodeResult = coordinator(
            repository = repository,
            document = document,
            page = failurePage,
            parserDispatcher = StandardTestDispatcher(testScheduler),
            fetchResult = HtmlFetchResult.Failure("Unavailable.", HtmlFetchFailureCode.PAGE_UNAVAILABLE),
            notificationPublisher = publisher,
            siteProblemNotificationRepository = notificationRepository,
            clock = { 200L },
        ).check(manga.id, "AUTOMATIC", requireMonitoringEnabled = true)

        assertTrue(nextEpisodeResult is MangaCheckResult.Failure)
        assertEquals(
            listOf(
                manga.id to "first-site-failure-0",
                manga.id to "second-site-failure-0",
            ),
            publisher.siteProblemNotifications,
        )
        assertEquals(listOf(manga.id), publisher.clearedProblemMangaIds)
    }

    @Test
    fun newlyDiscoveredChaptersAreNotifiedAndDeliveryTimestampIsPersisted() = runTest {
        val manga = testManga()
        val repository = RecordingMangaRepository(manga)
        val publisher = RecordingChapterNotificationPublisher()
        val document = testDocument(manga)
        val coordinator = coordinator(
            repository = repository,
            document = document,
            page = ParsedSitePage(
                title = manga.title,
                originalCoverUrl = null,
                seriesUrl = manga.seriesUrl,
                currentChapter = parsedChapter("chapter-1", 1, document.pageUrl),
                chapters = listOf(parsedChapter("chapter-1", 1, document.pageUrl)),
                isChapterListComplete = true,
            ),
            parserDispatcher = StandardTestDispatcher(testScheduler),
            notificationPublisher = publisher,
        )

        coordinator.check(manga.id, "MANUAL", requireMonitoringEnabled = false)

        assertEquals(listOf("Chapter 1"), publisher.publishedChapters.map { it.label })
        assertEquals(listOf("id-chapter-1"), repository.notificationPostedIds)
        assertEquals(100L, repository.chapters.single().notificationPostedAtEpochMillis)
        assertFalse(repository.chapters.single().isRead)
    }

    @Test
    fun notificationDeniedDuringOneCheckIsRetriedAfterALaterSuccessfulCheck() = runTest {
        val manga = testManga()
        val repository = RecordingMangaRepository(manga)
        val publisher = RecordingChapterNotificationPublisher().apply {
            deliveryEnabled = false
        }
        val document = testDocument(manga)
        val coordinator = coordinator(
            repository = repository,
            document = document,
            page = ParsedSitePage(
                title = manga.title,
                originalCoverUrl = null,
                seriesUrl = manga.seriesUrl,
                currentChapter = parsedChapter("chapter-1", 1, document.pageUrl),
                chapters = listOf(parsedChapter("chapter-1", 1, document.pageUrl)),
                isChapterListComplete = true,
            ),
            parserDispatcher = StandardTestDispatcher(testScheduler),
            notificationPublisher = publisher,
        )

        val firstCheck = coordinator.check(manga.id, "AUTOMATIC", requireMonitoringEnabled = false)
        publisher.deliveryEnabled = true
        val secondCheck = coordinator.check(manga.id, "AUTOMATIC", requireMonitoringEnabled = false)

        assertEquals(1, (firstCheck as MangaCheckResult.Success).newChapterCount)
        assertEquals(0, (secondCheck as MangaCheckResult.Success).newChapterCount)
        assertEquals(
            listOf(
                listOf("id-chapter-1"),
                listOf("id-chapter-1"),
            ),
            publisher.publicationAttempts.map { chapters -> chapters.map(TrackedChapter::id) },
        )
        assertEquals(listOf("id-chapter-1"), repository.notificationPostedIds)
        assertTrue(repository.pendingNotificationIds.isEmpty())
    }

    private fun coordinator(
        repository: RecordingMangaRepository,
        document: ParserDocument,
        page: ParsedSitePage,
        parserDispatcher: kotlinx.coroutines.CoroutineDispatcher,
        onFetch: (String) -> Unit = {},
        fetchResult: HtmlFetchResult = HtmlFetchResult.Success(document),
        notificationPublisher: ChapterNotificationPublisher = NoOpChapterNotificationPublisher,
        siteProblemNotificationRepository: SiteProblemNotificationRepository =
            com.sanzenkai.mangatracker.core.domain.repository.NoOpSiteProblemNotificationRepository,
        clock: () -> Long = { 100L },
    ) = MangaCheckCoordinator(
        mangaRepository = repository,
        htmlFetcher = HtmlDocumentFetcher { requestedUrl ->
            onFetch(requestedUrl)
            fetchResult
        },
        discoveryEngine = SiteDiscoveryEngine(
            ParserRegistry(
                setOf(
                    object : SiteParser {
                        override val id = "test.monitoring"

                        override fun match(document: ParserDocument) =
                            ParserMatch(100, "controlled fixture parser")

                        override fun parse(document: ParserDocument) = SiteParseResult.Success(page)
                    },
                ),
            ),
        ),
        clock = clock,
        idGenerator = { "check-${repository.attempts.size}" },
        parserDispatcher = parserDispatcher,
        minimumSameHostRequestIntervalMillis = 0,
        notificationPublisher = notificationPublisher,
        siteProblemNotificationRepository = siteProblemNotificationRepository,
    )

    private fun testManga() = Manga(
        id = "manga-1",
        title = "Sample Manga",
        originalCoverUrl = null,
        seriesUrl = "https://example.test/manga/sample",
        entryChapterUrl = "https://example.test/manga/sample/chapter-1",
        normalizedDomain = "example.test",
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
    )

    private fun testDocument(manga: Manga) = ParserDocument(
        pageUrl = manga.seriesUrl ?: manga.entryChapterUrl,
        contentType = "text/html",
        html = "<html><body>sample</body></html>",
    )

    private fun parsedChapter(key: String, number: Int, url: String) = ParsedChapter(
        sourceKey = key,
        label = "Chapter $number",
        url = url,
        publishedAtEpochMillis = null,
    )

    private class RecordingMangaRepository(
        var manga: Manga,
    ) : MangaRepository {
        val savedSnapshots = mutableListOf<ValidatedChapterSnapshot>()
        val attempts = mutableListOf<CheckAttempt>()
        val chapters = mutableListOf<TrackedChapter>()
        val notificationPostedIds = mutableListOf<String>()
        val pendingNotificationIds = mutableSetOf<String>()
        private val knownChapterKeys = mutableSetOf<String>()
        var monitoringEnabled = true

        override suspend fun createBackupSnapshot(exportedAtEpochMillis: Long) =
            error("Not used in this test")

        override suspend fun restoreBackup(snapshot: MangaBackupSnapshot) =
            error("Not used in this test")

        fun seedExistingChapter(chapter: ParsedChapter) {
            val number = chapter.label.substringAfterLast(' ').toBigDecimal().toPlainString()
            knownChapterKeys += chapter.sourceKey
            chapters += TrackedChapter(
                id = "existing-${chapter.sourceKey}",
                mangaId = manga.id,
                sourceChapterKey = chapter.sourceKey,
                label = chapter.label,
                chapterKind = "NUMBERED",
                normalizedNumber = number,
                url = chapter.url,
                publishedAtEpochMillis = chapter.publishedAtEpochMillis,
                firstDetectedAtEpochMillis = 1L,
                notificationPostedAtEpochMillis = null,
                isRead = false,
            )
        }

        override fun observeLibrary(): Flow<List<MangaSummary>> = emptyFlow()

        override fun observeManga(mangaId: String): Flow<Manga?> = flowOf(manga.takeIf { it.id == mangaId })

        override suspend fun saveManga(manga: Manga) = error("Not used in this test")

        override suspend fun fillMissingMangaMetadata(
            mangaId: String,
            metadata: MangaMetadataUpdate,
            updatedAtEpochMillis: Long,
        ): Boolean {
            if (manga.id != mangaId) return false
            val title = manga.title.takeIf(String::isNotBlank)
                ?: metadata.title?.trim()?.takeIf(String::isNotEmpty)
                ?: manga.title
            val coverUrl = manga.originalCoverUrl?.takeIf(String::isNotBlank)
                ?: metadata.originalCoverUrl?.trim()?.takeIf(String::isNotEmpty)
            val seriesUrl = manga.seriesUrl?.takeIf(String::isNotBlank)
                ?: metadata.seriesUrl?.trim()?.takeIf(String::isNotEmpty)
            if (title == manga.title &&
                coverUrl == manga.originalCoverUrl &&
                seriesUrl == manga.seriesUrl
            ) {
                return false
            }
            manga = manga.copy(
                title = title,
                originalCoverUrl = coverUrl,
                seriesUrl = seriesUrl,
                updatedAtEpochMillis = updatedAtEpochMillis,
            )
            return true
        }

        override suspend fun registerManga(
            manga: Manga,
            initialChapters: List<DiscoveredChapter>,
            detectedAtEpochMillis: Long,
            monitoringEnabled: Boolean,
        ): MangaRegistrationResult = error("Not used in this test")

        override fun observeChapters(mangaId: String): Flow<List<TrackedChapter>> = flowOf(chapters)

        override fun observeUnreadCount(mangaId: String): Flow<Int> = emptyFlow()

        override suspend fun saveFoundChapters(
            mangaId: String,
            snapshot: ValidatedChapterSnapshot,
            detectedAtEpochMillis: Long,
        ): ChapterSyncResult = error("Not used in this test")

        override suspend fun recordSuccessfulCheck(
            mangaId: String,
            snapshot: ValidatedChapterSnapshot,
            attempt: CheckAttempt,
        ): ChapterSyncResult {
            savedSnapshots += snapshot
            attempts += attempt
            val newChapterIds = snapshot.chapters
                .filter { knownChapterKeys.add(it.sourceChapterKey) }
                .map { discovered ->
                    val id = "id-${discovered.sourceChapterKey}"
                    chapters += TrackedChapter(
                        id = id,
                        mangaId = mangaId,
                        sourceChapterKey = discovered.sourceChapterKey,
                        label = discovered.label,
                        chapterKind = discovered.chapterKind,
                        normalizedNumber = discovered.normalizedNumber,
                        url = discovered.url,
                        publishedAtEpochMillis = discovered.publishedAtEpochMillis,
                        firstDetectedAtEpochMillis = attempt.finishedAtEpochMillis ?: attempt.startedAtEpochMillis,
                        notificationPostedAtEpochMillis = null,
                        isRead = false,
                    )
                    id
                }
            pendingNotificationIds += newChapterIds
            return ChapterSyncResult(newChapterIds)
        }

        override suspend fun setChapterReadState(
            chapterId: String,
            action: ReadingAction,
            origin: ReadingActionOrigin,
            occurredAtEpochMillis: Long,
        ): Boolean = error("Not used in this test")

        override suspend fun setMangaReadState(
            mangaIds: Set<String>,
            action: ReadingAction,
            origin: ReadingActionOrigin,
            occurredAtEpochMillis: Long,
        ): Int = error("Not used in this test")

        override suspend fun removeMangas(mangaIds: Set<String>): Int =
            error("Not used in this test")

        override suspend fun markChapterNotificationPosted(
            chapterId: String,
            postedAtEpochMillis: Long,
        ): Boolean {
            val index = chapters.indexOfFirst { it.id == chapterId }
            if (index < 0 || chapters[index].notificationPostedAtEpochMillis != null) return false
            chapters[index] = chapters[index].copy(notificationPostedAtEpochMillis = postedAtEpochMillis)
            notificationPostedIds += chapterId
            pendingNotificationIds -= chapterId
            return true
        }

        override suspend fun getPendingChapterNotifications(mangaId: String): List<TrackedChapter> =
            chapters.filter { it.mangaId == mangaId && it.id in pendingNotificationIds && !it.isRead }

        override fun observeReadingHistory(chapterId: String): Flow<List<ReadingHistoryEntry>> =
            emptyFlow()

        override suspend fun saveSiteConfiguration(configuration: SiteConfiguration) =
            error("Not used in this test")

        override suspend fun getSiteConfiguration(normalizedHost: String): SiteConfiguration? = null

        override fun observeMonitoringSettings(mangaId: String): Flow<MonitoringSettings?> =
            flowOf(MonitoringSettings(manga.id, monitoringEnabled, null, null, null))

        override suspend fun saveMonitoringSettings(settings: MonitoringSettings) =
            error("Not used in this test")

        override suspend fun setMonitoringEnabled(mangaId: String, isEnabled: Boolean) =
            error("Not used in this test")

        override fun observeCheckAttempts(mangaId: String): Flow<List<CheckAttempt>> = emptyFlow()

        override fun observeMonitoringProblem(mangaId: String): Flow<MonitoringProblemSignals> {
            val latestSuccessAt = attempts
                .filter { it.outcome == "SUCCESS" }
                .maxByOrNull { it.finishedAtEpochMillis ?: it.startedAtEpochMillis }
                ?.let { it.finishedAtEpochMillis ?: it.startedAtEpochMillis }
            val siteFailures = attempts
                .filter { attempt ->
                    attempt.errorCode in SITE_FAILURE_CODES &&
                        (latestSuccessAt == null ||
                            (attempt.finishedAtEpochMillis ?: attempt.startedAtEpochMillis) > latestSuccessAt)
                }
                .sortedBy { it.finishedAtEpochMillis ?: it.startedAtEpochMillis }
            val firstFailure = siteFailures.firstOrNull()
            return flowOf(
                MonitoringProblemSignals(
                    mangaId = mangaId,
                    siteFailureCount = siteFailures.size,
                    lastSuccessAtEpochMillis = latestSuccessAt,
                    episodeId = firstFailure?.id,
                    firstSiteFailureAtEpochMillis =
                        firstFailure?.finishedAtEpochMillis ?: firstFailure?.startedAtEpochMillis,
                ),
            )
        }

        override suspend fun recordCheckAttempt(attempt: CheckAttempt) {
            attempts += attempt
        }

        private companion object {
            val SITE_FAILURE_CODES = setOf(
                HtmlFetchFailureCode.PAGE_UNAVAILABLE.name,
                "INVALID_HTML",
                "INCOMPLETE_CHAPTER_LIST",
            )
        }
    }

    private class RecordingChapterNotificationPublisher : ChapterNotificationPublisher {
        val publishedChapters = mutableListOf<TrackedChapter>()
        val publicationAttempts = mutableListOf<List<TrackedChapter>>()
        val siteProblemNotifications = mutableListOf<Pair<String, String>>()
        val clearedProblemMangaIds = mutableListOf<String>()
        var deliveryEnabled = true

        override suspend fun publishNewChapters(
            manga: Manga,
            chapters: List<TrackedChapter>,
        ): Set<String> {
            publicationAttempts += chapters
            publishedChapters += chapters
            return if (deliveryEnabled) chapters.map { it.id }.toSet() else emptySet()
        }

        override fun ensureChannel() = Unit

        override fun cancelChapter(chapterId: String) = Unit

        override fun cancelMangaNotifications(mangaIds: Set<String>) = Unit

        override fun canPostSiteProblemNotifications() = true

        override suspend fun publishSiteMonitoringProblem(manga: Manga, episodeId: String): Boolean {
            siteProblemNotifications += manga.id to episodeId
            return true
        }

        override fun clearSiteMonitoringProblem(mangaId: String) {
            clearedProblemMangaIds += mangaId
        }
    }

    private class RecordingSiteProblemNotificationRepository : SiteProblemNotificationRepository {
        val claimedEpisodes = mutableMapOf<String, String>()

        override suspend fun claimEpisodeNotification(mangaId: String, episodeId: String): Boolean {
            if (claimedEpisodes[mangaId] == episodeId) return false
            claimedEpisodes[mangaId] = episodeId
            return true
        }
    }
}
