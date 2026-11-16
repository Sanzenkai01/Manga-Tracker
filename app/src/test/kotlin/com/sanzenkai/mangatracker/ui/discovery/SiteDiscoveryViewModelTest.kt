package com.sanzenkai.mangatracker.ui.discovery

import androidx.lifecycle.ViewModelStore
import com.sanzenkai.mangatracker.core.data.network.HtmlDocumentFetcher
import com.sanzenkai.mangatracker.core.data.network.HtmlFetchResult
import com.sanzenkai.mangatracker.core.domain.model.CheckAttempt
import com.sanzenkai.mangatracker.core.domain.model.ChapterSyncResult
import com.sanzenkai.mangatracker.core.domain.model.DiscoveredChapter
import com.sanzenkai.mangatracker.core.domain.model.Manga
import com.sanzenkai.mangatracker.core.domain.model.MangaMetadataUpdate
import com.sanzenkai.mangatracker.core.domain.model.MangaBackupSnapshot
import com.sanzenkai.mangatracker.core.domain.model.MangaRegistrationResult
import com.sanzenkai.mangatracker.core.domain.model.MangaSummary
import com.sanzenkai.mangatracker.core.domain.model.MonitoringSettings
import com.sanzenkai.mangatracker.core.domain.model.ReadingAction
import com.sanzenkai.mangatracker.core.domain.model.ReadingActionOrigin
import com.sanzenkai.mangatracker.core.domain.model.ReadingHistoryEntry
import com.sanzenkai.mangatracker.core.domain.model.SiteConfiguration
import com.sanzenkai.mangatracker.core.domain.model.TrackedChapter
import com.sanzenkai.mangatracker.core.domain.model.ValidatedChapterSnapshot
import com.sanzenkai.mangatracker.core.domain.repository.MangaRepository
import com.sanzenkai.mangatracker.core.parser.api.ParserDocument
import com.sanzenkai.mangatracker.core.parser.api.ParsedChapter
import com.sanzenkai.mangatracker.core.parser.api.ParsedSitePage
import com.sanzenkai.mangatracker.core.parser.api.ParserMatch
import com.sanzenkai.mangatracker.core.parser.api.SiteParser
import com.sanzenkai.mangatracker.core.parser.api.SiteParseIssue
import com.sanzenkai.mangatracker.core.parser.api.SiteParseResult
import com.sanzenkai.mangatracker.core.parser.engine.ExtractionRule
import com.sanzenkai.mangatracker.core.parser.engine.MANUAL_PARSER_ID
import com.sanzenkai.mangatracker.core.parser.engine.ManualSiteConfiguration
import com.sanzenkai.mangatracker.core.parser.engine.HtmlMetadataParser
import com.sanzenkai.mangatracker.core.parser.engine.ParserRegistry
import com.sanzenkai.mangatracker.core.parser.engine.SiteDiscoveryEngine
import com.sanzenkai.mangatracker.monitoring.MonitoringScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class SiteDiscoveryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun changingUrlWhileFetchIsPendingDiscardsThePreviousResult() = runTest {
        val repository = RecordingMangaRepository()
        val pendingFetch = CompletableDeferred<HtmlFetchResult>()
        val oldDocument = ParserDocument(
            pageUrl = "https://example.test/manga/old/chapter-1",
            contentType = "text/html",
            html = "<html><title>Old Manga</title></html>",
        )
        val viewModel = SiteDiscoveryViewModel(
            mangaRepository = repository,
            htmlFetcher = HtmlDocumentFetcher { pendingFetch.await() },
            discoveryEngine = SiteDiscoveryEngine(ParserRegistry(setOf(HtmlMetadataParser()))),
            parserDispatcher = StandardTestDispatcher(testScheduler),
        )
        val store = ViewModelStore().apply { put("discovery", viewModel) }
        try {
            viewModel.onUrlChanged(oldDocument.pageUrl)
            viewModel.analyzePage()
            runCurrent()
            assertTrue(viewModel.uiState.value.isLoading)

            viewModel.onUrlChanged("https://example.test/manga/new/chapter-2")
            pendingFetch.complete(HtmlFetchResult.Success(oldDocument))
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isLoading)
            assertNull(viewModel.uiState.value.result)
            viewModel.registerManga()
            advanceUntilIdle()
            assertNull(repository.registeredManga)
        } finally {
            store.clear()
        }
    }

    @Test
    fun manualConfigurationMustPassAgainstFetchedHtmlBeforeSavingByHost() = runTest {
        val repository = RecordingMangaRepository()
        val document = ParserDocument(
            pageUrl = "https://example.test/manga/sample",
            contentType = "text/html",
            html = """
                <html><head><title>Sample Manga Chapter 12</title></head>
                <body>
                  <h1 class="title">Sample Manga</h1>
                  <span class="chapter-number">12</span>
                  <a class="chapter-link" href="/manga/sample/chapter-12">Chapter</a>
                </body></html>
            """.trimIndent(),
        )
        val viewModel = SiteDiscoveryViewModel(
            mangaRepository = repository,
            htmlFetcher = HtmlDocumentFetcher { HtmlFetchResult.Success(document) },
            discoveryEngine = SiteDiscoveryEngine(ParserRegistry(setOf(HtmlMetadataParser()))),
            clock = { 1234L },
            parserDispatcher = StandardTestDispatcher(testScheduler),
        )
        val store = ViewModelStore().apply { put("discovery", viewModel) }
        try {
            viewModel.onUrlChanged(document.pageUrl)
            viewModel.analyzePage()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.result is SiteParseResult.RequiresConfiguration)
            assertNull(repository.savedConfiguration)
            viewModel.registerManga()
            advanceUntilIdle()
            assertNull(repository.registeredManga)

            viewModel.saveManualConfiguration()
            assertFalse(viewModel.uiState.value.manualTestPassed)
            assertNull(repository.savedConfiguration)

            viewModel.onManualConfigurationChanged(
                ManualSiteConfiguration.empty("example.test").copy(
                    titleRule = ExtractionRule(".title"),
                    chapterNumberRule = ExtractionRule(".chapter-number"),
                    chapterLinkRule = ExtractionRule(".chapter-link", "href"),
                ),
            )
            viewModel.testManualConfiguration()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.result is SiteParseResult.Success)
            assertTrue(viewModel.uiState.value.manualTestPassed)
            assertFalse((viewModel.uiState.value.result as SiteParseResult.Success).page.isChapterListComplete)

            viewModel.saveManualConfiguration()
            advanceUntilIdle()

            assertEquals(MANUAL_PARSER_ID, repository.savedConfiguration?.parserId)
            assertEquals("example.test", repository.savedConfiguration?.normalizedHost)
            assertEquals(1234L, repository.savedConfiguration?.validatedAtEpochMillis)
            assertEquals(0, repository.chapterSnapshotsSaved)
        } finally {
            store.clear()
        }
    }

    @Test
    fun completeSuccessfulParseRegistersAllKnownChapters() = runTest {
        val repository = RecordingMangaRepository()
        val document = ParserDocument(
            pageUrl = "https://example.test/manga/sample/chapter-12",
            contentType = "text/html",
            html = "<html><body>sample</body></html>",
        )
        val currentChapter = ParsedChapter(
            sourceKey = "chapter-12",
            label = "Chapter 12",
            url = document.pageUrl,
            publishedAtEpochMillis = 100L,
        )
        val parser = object : SiteParser {
            override val id = "test.complete"

            override fun match(document: ParserDocument) = ParserMatch(100, "controlled test parser")

            override fun parse(document: ParserDocument) = SiteParseResult.Success(
                ParsedSitePage(
                    title = "Sample Manga",
                    originalCoverUrl = "https://example.test/cover.jpg",
                    seriesUrl = "https://example.test/manga/sample",
                    currentChapter = currentChapter,
                    chapters = listOf(currentChapter),
                    isChapterListComplete = true,
                ),
            )
        }
        val viewModel = SiteDiscoveryViewModel(
            mangaRepository = repository,
            htmlFetcher = HtmlDocumentFetcher { HtmlFetchResult.Success(document) },
            discoveryEngine = SiteDiscoveryEngine(ParserRegistry(setOf(parser))),
            clock = { 1234L },
            parserDispatcher = StandardTestDispatcher(testScheduler),
            idGenerator = { "new-manga-id" },
        )
        val store = ViewModelStore().apply { put("discovery", viewModel) }
        try {
            viewModel.onUrlChanged(document.pageUrl)
            viewModel.analyzePage()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.result is SiteParseResult.Success)
            viewModel.registerManga()
            advanceUntilIdle()

            assertEquals("new-manga-id", repository.registeredManga?.id)
            assertEquals("Sample Manga", repository.registeredManga?.title)
            assertEquals("chapter-12", repository.registeredChapters?.single()?.sourceChapterKey)
            assertEquals("12", repository.registeredChapters?.single()?.normalizedNumber)
            assertEquals("new-manga-id", viewModel.uiState.value.registeredMangaId)
        } finally {
            store.clear()
        }
    }

    @Test
    fun verifiedLatestChapterRegistersOnlyItsCurrentChapter() = runTest {
        val repository = RecordingMangaRepository()
        val document = ParserDocument(
            pageUrl = "https://hivetoons.org/series/eleceed",
            contentType = "text/html",
            html = "<html><body>sample</body></html>",
        )
        val chapters = (424..426).map { number ->
            ParsedChapter(
                sourceKey = "chapter-$number",
                label = "Chapter $number",
                url = "https://hivetoons.org/series/eleceed/chapter-$number",
                publishedAtEpochMillis = null,
            )
        }.reversed()
        val parser = object : SiteParser {
            override val id = "test.latest-window"

            override fun match(document: ParserDocument) = ParserMatch(100, "verified latest chapter")

            override fun parse(document: ParserDocument) = SiteParseResult.Success(
                ParsedSitePage(
                    title = "Eleceed",
                    originalCoverUrl = null,
                    seriesUrl = "https://hivetoons.org/series/eleceed",
                    currentChapter = chapters.first(),
                    chapters = chapters,
                    isChapterListComplete = false,
                    isLatestChapterKnown = true,
                ),
            )
        }
        val viewModel = SiteDiscoveryViewModel(
            mangaRepository = repository,
            htmlFetcher = HtmlDocumentFetcher { HtmlFetchResult.Success(document) },
            discoveryEngine = SiteDiscoveryEngine(ParserRegistry(setOf(parser))),
            parserDispatcher = StandardTestDispatcher(testScheduler),
            idGenerator = { "new-manga-id" },
        )
        val store = ViewModelStore().apply { put("discovery", viewModel) }
        try {
            viewModel.onUrlChanged(document.pageUrl)
            viewModel.analyzePage()
            advanceUntilIdle()

            viewModel.registerManga()
            advanceUntilIdle()

            assertEquals(listOf("chapter-426"), repository.registeredChapters?.map { it.sourceChapterKey })
        } finally {
            store.clear()
        }
    }

    @Test
    fun incompleteChapterListCanRegisterTheMangaUsingOnlyTheEnteredChapter() = runTest {
        val repository = RecordingMangaRepository()
        val document = ParserDocument(
            pageUrl = "https://example.test/manga/sample/chapter-12",
            contentType = "text/html",
            html = "<html><body>sample</body></html>",
        )
        val currentChapter = ParsedChapter(
            sourceKey = "chapter-12",
            label = "Chapter 12",
            url = document.pageUrl,
            publishedAtEpochMillis = 100L,
        )
        val parser = object : SiteParser {
            override val id = "test.incomplete"

            override fun match(document: ParserDocument) = ParserMatch(100, "controlled test parser")

            override fun parse(document: ParserDocument) =
                SiteParseResult.RequiresConfiguration(
                    reason = "The chapter list is incomplete.",
                    preview = ParsedSitePage(
                        title = "Sample Manga",
                        originalCoverUrl = null,
                        seriesUrl = "https://example.test/manga/sample",
                        currentChapter = currentChapter,
                        chapters = listOf(
                            ParsedChapter(
                                sourceKey = "chapter-11",
                                label = "Chapter 11",
                                url = "https://example.test/manga/sample/chapter-11",
                                publishedAtEpochMillis = null,
                            ),
                        ),
                        isChapterListComplete = false,
                    ),
                    issue = SiteParseIssue.INCOMPLETE_CHAPTER_LIST,
                )
        }
        val viewModel = SiteDiscoveryViewModel(
            mangaRepository = repository,
            htmlFetcher = HtmlDocumentFetcher { HtmlFetchResult.Success(document) },
            discoveryEngine = SiteDiscoveryEngine(ParserRegistry(setOf(parser))),
            clock = { 1234L },
            parserDispatcher = StandardTestDispatcher(testScheduler),
            idGenerator = { "new-manga-id" },
        )
        val store = ViewModelStore().apply { put("discovery", viewModel) }
        try {
            viewModel.onUrlChanged(document.pageUrl)
            viewModel.analyzePage()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.result is SiteParseResult.RequiresConfiguration)
            assertEquals("Sample Manga", viewModel.uiState.value.registrationCandidate?.title)
            assertFalse(viewModel.uiState.value.isManualConfigurationVisible)

            viewModel.registerManga()
            advanceUntilIdle()

            assertEquals("new-manga-id", repository.registeredManga?.id)
            assertEquals(document.pageUrl, repository.registeredManga?.entryChapterUrl)
            assertEquals(listOf("chapter-12"), repository.registeredChapters?.map { it.sourceChapterKey })
        } finally {
            store.clear()
        }
    }

    @Test
    fun dynamicChapterPageCanBeSavedWithoutSchedulingUnsupportedAutomaticChecks() = runTest {
        val repository = RecordingMangaRepository()
        val document = ParserDocument(
            pageUrl = "https://imperiodabritannia.net/manga/sample/capitulo/185",
            contentType = "text/html",
            html = "<html><title>Sample Manga</title></html>",
        )
        val chapter = ParsedChapter(
            sourceKey = document.pageUrl,
            label = "Capítulo 185",
            url = document.pageUrl,
            publishedAtEpochMillis = null,
        )
        val parser = object : SiteParser {
            override val id = "test.dynamic"

            override fun match(document: ParserDocument) = ParserMatch(100, "dynamic page fixture")

            override fun parse(document: ParserDocument) = SiteParseResult.RequiresConfiguration(
                reason = "The chapter list is rendered in the browser.",
                preview = ParsedSitePage(
                    title = "Sample Manga",
                    originalCoverUrl = null,
                    seriesUrl = "https://imperiodabritannia.net/manga/sample/",
                    currentChapter = chapter,
                    chapters = emptyList(),
                    isChapterListComplete = false,
                ),
                issue = SiteParseIssue.UNSUPPORTED_DYNAMIC_CONTENT,
            )
        }
        val scheduler = RecordingMonitoringScheduler()
        val viewModel = SiteDiscoveryViewModel(
            mangaRepository = repository,
            htmlFetcher = HtmlDocumentFetcher { HtmlFetchResult.Success(document) },
            discoveryEngine = SiteDiscoveryEngine(ParserRegistry(setOf(parser))),
            parserDispatcher = StandardTestDispatcher(testScheduler),
            monitoringScheduler = scheduler,
        )
        val store = ViewModelStore().apply { put("discovery", viewModel) }
        try {
            viewModel.onUrlChanged(document.pageUrl)
            viewModel.analyzePage()
            advanceUntilIdle()

            assertEquals("Sample Manga", viewModel.uiState.value.registrationCandidate?.title)
            viewModel.registerManga()
            advanceUntilIdle()

            assertEquals(false, repository.registeredMonitoringEnabled)
            assertEquals(emptyList<String>(), scheduler.enqueuedMangaIds)
            assertEquals(listOf(document.pageUrl), repository.registeredChapters?.map { it.url })
        } finally {
            store.clear()
        }
    }

    private class RecordingMangaRepository : MangaRepository {
        var savedConfiguration: SiteConfiguration? = null
        var chapterSnapshotsSaved: Int = 0
        var registeredManga: Manga? = null
        var registeredChapters: List<DiscoveredChapter>? = null
        var registeredMonitoringEnabled: Boolean? = null

        override suspend fun createBackupSnapshot(exportedAtEpochMillis: Long) =
            error("Not used in this test")

        override suspend fun restoreBackup(snapshot: MangaBackupSnapshot) =
            error("Not used in this test")

        override fun observeLibrary(): Flow<List<MangaSummary>> = emptyFlow()

        override fun observeManga(mangaId: String): Flow<Manga?> = emptyFlow()

        override suspend fun saveManga(manga: Manga) = error("Not used in this test")

        override suspend fun fillMissingMangaMetadata(
            mangaId: String,
            metadata: MangaMetadataUpdate,
            updatedAtEpochMillis: Long,
        ): Boolean = error("Not used in this test")

        override suspend fun registerManga(
            manga: Manga,
            initialChapters: List<DiscoveredChapter>,
            detectedAtEpochMillis: Long,
            monitoringEnabled: Boolean,
        ): MangaRegistrationResult {
            registeredManga = manga
            registeredChapters = initialChapters
            registeredMonitoringEnabled = monitoringEnabled
            return MangaRegistrationResult.Added(manga.id, initialChapters.size)
        }

        override fun observeChapters(mangaId: String): Flow<List<TrackedChapter>> = emptyFlow()

        override fun observeUnreadCount(mangaId: String): Flow<Int> = emptyFlow()

        override suspend fun saveFoundChapters(
            mangaId: String,
            snapshot: ValidatedChapterSnapshot,
            detectedAtEpochMillis: Long,
        ): ChapterSyncResult {
            chapterSnapshotsSaved++
            return ChapterSyncResult(emptyList())
        }

        override suspend fun recordSuccessfulCheck(
            mangaId: String,
            snapshot: ValidatedChapterSnapshot,
            attempt: CheckAttempt,
        ): ChapterSyncResult = error("Not used in this test")

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
        ): Boolean = error("Not used in this test")

        override suspend fun getPendingChapterNotifications(mangaId: String): List<TrackedChapter> =
            error("Not used in this test")

        override fun observeReadingHistory(chapterId: String): Flow<List<ReadingHistoryEntry>> =
            emptyFlow()

        override suspend fun saveSiteConfiguration(configuration: SiteConfiguration) {
            savedConfiguration = configuration
        }

        override suspend fun getSiteConfiguration(normalizedHost: String): SiteConfiguration? =
            savedConfiguration?.takeIf { it.normalizedHost == normalizedHost }

        override fun observeMonitoringSettings(mangaId: String): Flow<MonitoringSettings?> =
            emptyFlow()

        override suspend fun saveMonitoringSettings(settings: MonitoringSettings) =
            error("Not used in this test")

        override suspend fun setMonitoringEnabled(mangaId: String, isEnabled: Boolean) =
            error("Not used in this test")

        override fun observeCheckAttempts(mangaId: String): Flow<List<CheckAttempt>> = emptyFlow()

        override suspend fun recordCheckAttempt(attempt: CheckAttempt) =
            error("Not used in this test")
    }

    private class RecordingMonitoringScheduler : MonitoringScheduler {
        val enqueuedMangaIds = mutableListOf<String>()

        override fun ensurePeriodicMonitoring() = Unit

        override fun enqueueAutomaticMangaCheck(mangaId: String) {
            enqueuedMangaIds += mangaId
        }

        override fun cancelAutomaticMangaCheck(mangaId: String) = Unit
    }

    class MainDispatcherRule : TestWatcher() {
        private val dispatcher = StandardTestDispatcher()

        override fun starting(description: Description) {
            Dispatchers.setMain(dispatcher)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }
}
