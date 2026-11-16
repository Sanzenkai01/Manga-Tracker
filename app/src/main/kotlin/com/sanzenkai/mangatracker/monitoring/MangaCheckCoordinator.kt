package com.sanzenkai.mangatracker.monitoring

import android.util.Log
import android.database.sqlite.SQLiteException
import com.sanzenkai.mangatracker.core.data.network.HtmlDocumentFetcher
import com.sanzenkai.mangatracker.core.data.network.HtmlFetchFailureCode
import com.sanzenkai.mangatracker.core.data.network.HtmlFetchResult
import com.sanzenkai.mangatracker.core.domain.model.CheckAttempt
import com.sanzenkai.mangatracker.core.domain.model.Manga
import com.sanzenkai.mangatracker.core.domain.model.MangaMetadataUpdate
import com.sanzenkai.mangatracker.core.domain.model.ValidatedChapterSnapshot
import com.sanzenkai.mangatracker.core.domain.repository.MangaRepository
import com.sanzenkai.mangatracker.core.localization.localizedMessage
import com.sanzenkai.mangatracker.core.parser.api.ParsedChapter
import com.sanzenkai.mangatracker.core.parser.api.ParsedSitePage
import com.sanzenkai.mangatracker.core.parser.api.SiteParseResult
import com.sanzenkai.mangatracker.core.parser.engine.DomainNormalizer
import com.sanzenkai.mangatracker.core.parser.engine.MANUAL_PARSER_ID
import com.sanzenkai.mangatracker.core.parser.engine.ManualConfigurationDecodeResult
import com.sanzenkai.mangatracker.core.parser.engine.ManualSiteConfigurationCodec
import com.sanzenkai.mangatracker.core.parser.engine.SiteDiscoveryEngine
import com.sanzenkai.mangatracker.notifications.ChapterNotificationPublisher
import com.sanzenkai.mangatracker.notifications.NoOpChapterNotificationPublisher
import com.sanzenkai.mangatracker.ui.discovery.toDiscoveredChapter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MangaCheckCoordinator(
    private val mangaRepository: MangaRepository,
    private val htmlFetcher: HtmlDocumentFetcher,
    private val discoveryEngine: SiteDiscoveryEngine,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val parserDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val minimumSameHostRequestIntervalMillis: Long = DEFAULT_HOST_REQUEST_INTERVAL_MILLIS,
    private val notificationPublisher: ChapterNotificationPublisher = NoOpChapterNotificationPublisher,
) {
    private val mangaLocks = ConcurrentHashMap<String, Mutex>()
    private val hostLocks = ConcurrentHashMap<String, Mutex>()
    private val lastHostRequestAt = ConcurrentHashMap<String, Long>()

    init {
        require(minimumSameHostRequestIntervalMillis >= 0)
    }

    suspend fun check(
        mangaId: String,
        trigger: String,
        requireMonitoringEnabled: Boolean,
    ): MangaCheckResult = mangaLocks.computeIfAbsent(mangaId) { Mutex() }.withLock {
        val manga = try {
            mangaRepository.observeManga(mangaId).first()
        } catch (error: SQLiteException) {
            Log.e(TAG, "Could not load manga from local storage.", error)
            return@withLock MangaCheckResult.Failure(
                mangaId = mangaId,
                normalizedHost = null,
                errorCode = ERROR_LOCAL_STORAGE,
                message = localizedMessage(
                    english = "The manga could not be loaded from local storage.",
                    portugueseBrazilian = "Não foi possível carregar o mangá do armazenamento local.",
                ),
                retryable = true,
            )
        } ?: return@withLock MangaCheckResult.Skipped(
            mangaId,
            localizedMessage("The manga is no longer in the library.", "O mangá não está mais cadastrado."),
        )

        if (requireMonitoringEnabled && !isMonitoringEnabled(mangaId)) {
            return@withLock MangaCheckResult.Skipped(
                mangaId,
                localizedMessage(
                    "Automatic chapter checks are paused.",
                    "A busca automática de capítulos está pausada.",
                ),
            )
        }

        val startedAt = clock()
        try {
            waitForHostSlot(manga.normalizedDomain)
            if (requireMonitoringEnabled && !isMonitoringEnabled(mangaId)) {
                return@withLock MangaCheckResult.Skipped(
                    mangaId,
                    localizedMessage(
                        "Automatic chapter checks were paused before the request.",
                        "A busca automática de capítulos foi pausada antes da solicitação.",
                    ),
                )
            }

            val manualConfiguration = loadManualConfiguration(manga.normalizedDomain)
            val checkUrl = if (manualConfiguration != null) {
                manga.entryChapterUrl
            } else {
                manga.seriesUrl
                    ?.takeIf { DomainNormalizer.normalizedHostFromUrl(it) == manga.normalizedDomain }
                    ?: manga.entryChapterUrl
            }
            when (val fetched = htmlFetcher.fetch(checkUrl)) {
                is HtmlFetchResult.Failure -> recordFailure(
                    mangaId = mangaId,
                    normalizedHost = manga.normalizedDomain,
                    trigger = trigger,
                    startedAt = startedAt,
                    errorCode = fetched.code.name,
                    message = fetched.reason,
                    retryable = fetched.code.isRetryable(),
                )
                is HtmlFetchResult.Success -> {
                    val fetchedHost = DomainNormalizer.normalizedHostFromUrl(fetched.document.pageUrl)
                    if (fetchedHost != manga.normalizedDomain) {
                        return@withLock recordFailure(
                            mangaId = mangaId,
                            normalizedHost = manga.normalizedDomain,
                            trigger = trigger,
                            startedAt = startedAt,
                            errorCode = ERROR_HOST_MISMATCH,
                            message = localizedMessage(
                                "The fetched page's domain no longer matches the tracked manga.",
                                "O domínio da página obtida não corresponde mais ao mangá acompanhado.",
                            ),
                            retryable = false,
                        )
                    }

                    when (
                        val parsed = withContext(parserDispatcher) {
                            discoveryEngine.discover(
                                document = fetched.document,
                                manualConfiguration = manualConfiguration,
                            )
                        }
                    ) {
                        is SiteParseResult.Success -> {
                            val page = parsed.page
                            if (requireMonitoringEnabled && !isMonitoringEnabled(mangaId)) {
                                return@withLock MangaCheckResult.Skipped(
                                    mangaId,
                                    localizedMessage(
                                        "Automatic chapter checks were paused before saving metadata.",
                                        "A busca automática de capítulos foi pausada antes de salvar os metadados.",
                                    ),
                                )
                            }
                            val metadataUpdated = mangaRepository.fillMissingMangaMetadata(
                                mangaId = mangaId,
                                metadata = MangaMetadataUpdate(
                                    title = page.title?.trim()?.takeIf(String::isNotEmpty),
                                    originalCoverUrl = page.originalCoverUrl
                                        ?.trim()
                                        ?.takeIf { it.isNotEmpty() &&
                                            DomainNormalizer.normalizedHostFromUrl(it) != null
                                        },
                                    seriesUrl = page.seriesUrl
                                        ?.trim()
                                        ?.takeIf {
                                            DomainNormalizer.normalizedHostFromUrl(it) ==
                                                manga.normalizedDomain
                                        },
                                ),
                                updatedAtEpochMillis = clock(),
                            )
                            val mangaForNotifications = if (metadataUpdated) {
                                mangaRepository.observeManga(mangaId).first() ?: manga
                            } else {
                                manga
                            }
                            val chapterDelta = when {
                                page.isChapterListComplete && !page.isLatestChapterKnown ->
                                    (page.chapters + listOfNotNull(page.currentChapter))
                                        .distinctBy { it.sourceKey }
                                page.isLatestChapterKnown ->
                                    findLatestChapterDelta(mangaId, page)
                                else -> null
                            }
                            if (chapterDelta == null) {
                                recordFailure(
                                    mangaId = mangaId,
                                    normalizedHost = manga.normalizedDomain,
                                    trigger = trigger,
                                    startedAt = startedAt,
                                    errorCode = MANGA_CHECK_ERROR_INCOMPLETE_CHAPTER_LIST,
                                    message = localizedMessage(
                                        "The chapter list is incomplete; saved chapters were not changed.",
                                        "A lista de capítulos está incompleta; os capítulos salvos não foram alterados.",
                                    ),
                                    retryable = false,
                                )
                            } else {
                                if (requireMonitoringEnabled && !isMonitoringEnabled(mangaId)) {
                                    return@withLock MangaCheckResult.Skipped(
                                        mangaId,
                                        localizedMessage(
                                            "Automatic chapter checks were paused before saving the result.",
                                            "A busca automática de capítulos foi pausada antes de salvar o resultado.",
                                        ),
                                    )
                                }
                                val chapters = chapterDelta.map { it.toDiscoveredChapter() }
                                val finishedAt = clock()
                                val attempt = CheckAttempt(
                                    id = idGenerator(),
                                    mangaId = mangaId,
                                    trigger = trigger,
                                    startedAtEpochMillis = startedAt,
                                    finishedAtEpochMillis = finishedAt,
                                    outcome = OUTCOME_SUCCESS,
                                    errorCode = null,
                                )
                                val syncResult = mangaRepository.recordSuccessfulCheck(
                                    mangaId = mangaId,
                                    snapshot = if (page.isLatestChapterKnown) {
                                        ValidatedChapterSnapshot.fromVerifiedLatestChapterWindow(
                                            chapters = chapters,
                                            isLatestChapterKnown = page.isLatestChapterKnown,
                                        )
                                    } else {
                                        ValidatedChapterSnapshot.fromParserSuccess(
                                            chapters = chapters,
                                            isComplete = true,
                                        )
                                    },
                                    attempt = attempt,
                                )
                                publishPendingChapterNotifications(
                                    mangaId = mangaId,
                                    manga = mangaForNotifications,
                                    postedAtEpochMillis = finishedAt,
                                )
                                MangaCheckResult.Success(
                                    mangaId = mangaId,
                                    normalizedHost = manga.normalizedDomain,
                                    newChapterCount = syncResult.newChapterIds.size,
                                    checkedAtEpochMillis = finishedAt,
                                )
                            }
                        }
                        is SiteParseResult.RequiresConfiguration -> recordFailure(
                            mangaId = mangaId,
                            normalizedHost = manga.normalizedDomain,
                            trigger = trigger,
                            startedAt = startedAt,
                            errorCode = parsed.issue.name,
                            message = parsed.reason,
                            retryable = false,
                        )
                        is SiteParseResult.Failure -> recordFailure(
                            mangaId = mangaId,
                            normalizedHost = manga.normalizedDomain,
                            trigger = trigger,
                            startedAt = startedAt,
                            errorCode = parsed.issue.name,
                            message = parsed.reason,
                            retryable = false,
                        )
                    }
                }
            }
        } catch (error: SQLiteException) {
            Log.e(TAG, "Could not save the local manga check.", error)
            MangaCheckResult.Failure(
                mangaId = mangaId,
                normalizedHost = manga.normalizedDomain,
                errorCode = ERROR_LOCAL_STORAGE,
                message = localizedMessage(
                    english = "The local check could not be saved.",
                    portugueseBrazilian = "Não foi possível salvar a verificação local.",
                ),
                retryable = true,
            )
        } catch (error: IllegalStateException) {
            recordFailure(
                mangaId = mangaId,
                normalizedHost = manga.normalizedDomain,
                trigger = trigger,
                startedAt = startedAt,
                errorCode = ERROR_CONFIGURATION,
                message = error.message ?: localizedMessage(
                    "The saved site configuration is invalid.",
                    "A configuração salva do site é inválida.",
                ),
                retryable = false,
            )
        }
    }

    private suspend fun findLatestChapterDelta(
        mangaId: String,
        page: ParsedSitePage,
    ): List<ParsedChapter>? {
        val observedChapters = (page.chapters + listOfNotNull(page.currentChapter))
            .distinctBy { it.sourceKey }
        if (observedChapters.isEmpty()) return null

        val savedChapters = mangaRepository.observeChapters(mangaId).first()
        val savedKeys = savedChapters.mapTo(mutableSetOf()) { it.sourceChapterKey }
        val newestSavedIndex = observedChapters.indexOfFirst { it.sourceKey in savedKeys }
        if (newestSavedIndex >= 0) return observedChapters.take(newestSavedIndex)

        val latestSavedNumber = savedChapters.mapNotNull { chapter ->
            chapter.normalizedNumber?.toBigDecimalOrNull()
        }.maxOrNull() ?: return null
        val numberedChapters = observedChapters.map { chapter ->
            val number = chapter.toDiscoveredChapter().normalizedNumber?.toBigDecimalOrNull()
                ?: return null
            chapter to number
        }
        return numberedChapters
            .filter { (_, number) -> number > latestSavedNumber }
            .map { (chapter, _) -> chapter }
    }

    private suspend fun isMonitoringEnabled(mangaId: String): Boolean =
        mangaRepository.observeMonitoringSettings(mangaId).first()?.isEnabled == true

    private suspend fun publishPendingChapterNotifications(
        mangaId: String,
        manga: Manga,
        postedAtEpochMillis: Long,
    ) {
        val pendingChapters = mangaRepository.getPendingChapterNotifications(mangaId)
        val postedIds = notificationPublisher.publishNewChapters(manga, pendingChapters)
        postedIds.forEach { chapterId ->
            try {
                if (!mangaRepository.markChapterNotificationPosted(chapterId, postedAtEpochMillis)) {
                    Log.w(TAG, "Notification was published but its state was already recorded.")
                }
            } catch (error: SQLiteException) {
                Log.e(TAG, "Notification was published but its state could not be saved.", error)
            }
        }
    }

    private suspend fun waitForHostSlot(host: String) {
        if (minimumSameHostRequestIntervalMillis == 0L) return
        hostLocks.computeIfAbsent(host) { Mutex() }.withLock {
            val now = clock()
            val previousRequestAt = lastHostRequestAt[host]
            val waitMillis = previousRequestAt
                ?.let { minimumSameHostRequestIntervalMillis - (now - it) }
                ?.coerceAtLeast(0)
                ?: 0
            if (waitMillis > 0) delay(waitMillis)
            lastHostRequestAt[host] = clock()
        }
    }

    private suspend fun loadManualConfiguration(normalizedHost: String) =
        mangaRepository.getSiteConfiguration(normalizedHost)?.let { saved ->
            if (saved.parserId != MANUAL_PARSER_ID) return@let null
            if (saved.formatVersion != MANUAL_CONFIGURATION_FORMAT_VERSION) {
                throw IllegalStateException(
                    localizedMessage(
                        "The saved site configuration uses an unsupported format.",
                        "A configuração salva do site usa um formato incompatível.",
                    ),
                )
            }
            when (val decoded = ManualSiteConfigurationCodec.decode(saved.configJson)) {
                is ManualConfigurationDecodeResult.Failure ->
                    throw IllegalStateException(decoded.reason)
                is ManualConfigurationDecodeResult.Success -> {
                    if (decoded.configuration.normalizedHost != normalizedHost) {
                        throw IllegalStateException(
                            localizedMessage(
                                "The saved configuration's domain does not match the site.",
                                "O domínio da configuração salva não corresponde ao site.",
                            ),
                        )
                    }
                    decoded.configuration
                }
            }
        }

    private suspend fun recordFailure(
        mangaId: String,
        normalizedHost: String,
        trigger: String,
        startedAt: Long,
        errorCode: String,
        message: String,
        retryable: Boolean,
    ): MangaCheckResult.Failure {
        val finishedAt = clock()
        val outcome = if (errorCode == MANGA_CHECK_ERROR_INCOMPLETE_CHAPTER_LIST) {
            MANGA_CHECK_OUTCOME_INCOMPLETE_LIST
        } else {
            OUTCOME_FAILURE
        }
        return try {
            mangaRepository.recordCheckAttempt(
                CheckAttempt(
                    id = idGenerator(),
                    mangaId = mangaId,
                    trigger = trigger,
                    startedAtEpochMillis = startedAt,
                    finishedAtEpochMillis = finishedAt,
                    outcome = outcome,
                    errorCode = errorCode,
                ),
            )
            MangaCheckResult.Failure(
                mangaId = mangaId,
                normalizedHost = normalizedHost,
                errorCode = errorCode,
                message = message,
                retryable = retryable,
            )
        } catch (error: SQLiteException) {
            Log.e(TAG, "Could not record the manga check failure.", error)
            MangaCheckResult.Failure(
                mangaId = mangaId,
                normalizedHost = normalizedHost,
                errorCode = ERROR_LOCAL_STORAGE,
                message = localizedMessage(
                    english = "The check failed and its outcome could not be saved locally.",
                    portugueseBrazilian = "A verificação falhou e não foi possível salvar o resultado localmente.",
                ),
                retryable = true,
            )
        }
    }

    private fun HtmlFetchFailureCode.isRetryable(): Boolean =
        this == HtmlFetchFailureCode.NETWORK_ERROR ||
            this == HtmlFetchFailureCode.RATE_LIMITED ||
            this == HtmlFetchFailureCode.SERVER_ERROR

    private companion object {
        const val TAG = "MangaCheckCoordinator"
        const val OUTCOME_SUCCESS = "SUCCESS"
        const val OUTCOME_FAILURE = "FAILURE"
        const val ERROR_LOCAL_STORAGE = "LOCAL_STORAGE"
        const val ERROR_HOST_MISMATCH = "HOST_MISMATCH"
        const val ERROR_CONFIGURATION = "INVALID_SAVED_CONFIGURATION"
        const val MANUAL_CONFIGURATION_FORMAT_VERSION = 1
        const val DEFAULT_HOST_REQUEST_INTERVAL_MILLIS = 2_000L
    }
}
