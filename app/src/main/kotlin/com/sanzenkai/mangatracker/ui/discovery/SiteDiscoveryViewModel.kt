package com.sanzenkai.mangatracker.ui.discovery

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanzenkai.mangatracker.core.data.network.HtmlFetchResult
import com.sanzenkai.mangatracker.core.data.network.HtmlDocumentFetcher
import com.sanzenkai.mangatracker.core.domain.model.SiteConfiguration
import com.sanzenkai.mangatracker.core.domain.model.Manga
import com.sanzenkai.mangatracker.core.domain.model.MangaRegistrationResult
import com.sanzenkai.mangatracker.core.domain.repository.MangaRepository
import com.sanzenkai.mangatracker.core.localization.localizedMessage
import com.sanzenkai.mangatracker.core.parser.api.ParserDocument
import com.sanzenkai.mangatracker.core.parser.api.ParsedChapter
import com.sanzenkai.mangatracker.core.parser.api.ParsedSitePage
import com.sanzenkai.mangatracker.core.parser.api.SiteParseIssue
import com.sanzenkai.mangatracker.core.parser.api.SiteParseResult
import com.sanzenkai.mangatracker.core.parser.engine.DomainNormalizer
import com.sanzenkai.mangatracker.core.parser.engine.MANUAL_PARSER_ID
import com.sanzenkai.mangatracker.core.parser.engine.ManualConfigurationDecodeResult
import com.sanzenkai.mangatracker.core.parser.engine.ManualConfigurationValidator
import com.sanzenkai.mangatracker.core.parser.engine.ManualSiteConfiguration
import com.sanzenkai.mangatracker.core.parser.engine.ManualSiteConfigurationCodec
import com.sanzenkai.mangatracker.core.parser.engine.SiteDiscoveryEngine
import com.sanzenkai.mangatracker.monitoring.MonitoringScheduler
import com.sanzenkai.mangatracker.monitoring.NoOpMonitoringScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.URISyntaxException
import java.util.UUID

class SiteDiscoveryViewModel(
    private val mangaRepository: MangaRepository,
    private val htmlFetcher: HtmlDocumentFetcher,
    private val discoveryEngine: SiteDiscoveryEngine,
    private val clock: () -> Long = System::currentTimeMillis,
    private val parserDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val monitoringScheduler: MonitoringScheduler = NoOpMonitoringScheduler,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SiteDiscoveryUiState())
    val uiState: StateFlow<SiteDiscoveryUiState> = _uiState.asStateFlow()
    private var currentDocument: ParserDocument? = null
    private var analysisJob: Job? = null
    private var manualTestJob: Job? = null
    private var analysisRequestId = 0L
    private var manualTestRequestId = 0L

    fun onUrlChanged(url: String) {
        analysisRequestId += 1
        manualTestRequestId += 1
        analysisJob?.cancel()
        manualTestJob?.cancel()
        analysisJob = null
        manualTestJob = null
        val host = DomainNormalizer.normalizedHostFromUrl(url)
        val existingConfiguration = _uiState.value.manualConfiguration
            ?.takeIf { it.normalizedHost == host }
        _uiState.value = _uiState.value.copy(
            url = url,
            result = null,
            manualConfiguration = existingConfiguration,
            isManualConfigurationVisible = false,
            manualTestPassed = false,
            registeredMangaId = null,
            shouldPromptForNotificationPermission = false,
            message = null,
            error = null,
            isLoading = false,
            isTestingConfiguration = false,
        )
        currentDocument = null
    }

    fun onManualConfigurationChanged(configuration: ManualSiteConfiguration) {
        manualTestRequestId += 1
        manualTestJob?.cancel()
        manualTestJob = null
        _uiState.value = _uiState.value.copy(
            manualConfiguration = configuration,
            manualTestPassed = false,
            registeredMangaId = null,
            shouldPromptForNotificationPermission = false,
            message = null,
            error = null,
            isTestingConfiguration = false,
        )
    }

    fun onManualConfigurationVisibilityChanged(visible: Boolean) {
        _uiState.value = _uiState.value.copy(isManualConfigurationVisible = visible)
    }

    fun registerManga() {
        val state = _uiState.value
        val document = currentDocument
        val page = state.registrationCandidate
        if (document == null || page == null) {
            _uiState.value = state.copy(
                error = localizedMessage(
                    english = "The page must provide the manga title, series page, and current chapter before it can be added.",
                    portugueseBrazilian = "A página precisa informar o título do mangá, a página da série e o capítulo atual para poder ser adicionada.",
                ),
            )
            return
        }
        val title = page.title?.trim()?.takeIf(String::isNotEmpty)
        val seriesUrl = page.seriesUrl?.trim()?.takeIf(String::isNotEmpty)
        val currentChapter = page.currentChapter
        if (title == null || seriesUrl == null || currentChapter == null) {
            _uiState.value = state.copy(
                error = localizedMessage(
                    english = "Identify the title, series page, and current chapter before adding this manga.",
                    portugueseBrazilian = "Identifique o título, a página da série e o capítulo atual antes de adicionar este mangá.",
                ),
            )
            return
        }

        val normalizedHost = DomainNormalizer.normalizedHostFromUrl(document.pageUrl)
        if (normalizedHost == null) {
            _uiState.value = state.copy(
                error = localizedMessage(
                    english = "The resolved page does not have a valid public domain.",
                    portugueseBrazilian = "A página resolvida não possui um domínio público válido.",
                ),
            )
            return
        }
        val chapterSelection = selectRegistrationChapters(state.url, page)
        val chapters = chapterSelection.chapters.map { it.toDiscoveredChapter() }
        if (chapters.isEmpty()) {
            _uiState.value = state.copy(
                error = localizedMessage(
                    english = "The current chapter could not be identified from this link.",
                    portugueseBrazilian = "Não foi possível identificar o capítulo atual neste link.",
                ),
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRegisteringManga = true,
                registeredMangaId = null,
                shouldPromptForNotificationPermission = false,
                error = null,
                message = null,
            )
            val now = clock()
            val configuration = state.manualConfiguration
                ?.takeIf { state.manualTestPassed && it.normalizedHost == normalizedHost }
            val monitoringEnabled =
                (state.result as? SiteParseResult.RequiresConfiguration)?.issue !=
                    SiteParseIssue.UNSUPPORTED_DYNAMIC_CONTENT
            try {
                if (configuration != null) {
                    mangaRepository.saveSiteConfiguration(
                        SiteConfiguration(
                            normalizedHost = configuration.normalizedHost,
                            parserId = MANUAL_PARSER_ID,
                            formatVersion = CONFIGURATION_FORMAT_VERSION,
                            configJson = ManualSiteConfigurationCodec.encode(configuration),
                            updatedAtEpochMillis = now,
                            validatedAtEpochMillis = now,
                        ),
                    )
                }
                when (
                    val registration = mangaRepository.registerManga(
                        manga = Manga(
                            id = idGenerator(),
                            title = title,
                            originalCoverUrl = page.originalCoverUrl,
                            seriesUrl = seriesUrl,
                            entryChapterUrl = chapterSelection.entryChapter.url,
                            normalizedDomain = normalizedHost,
                            createdAtEpochMillis = now,
                            updatedAtEpochMillis = now,
                        ),
                        initialChapters = chapters,
                        detectedAtEpochMillis = now,
                        monitoringEnabled = monitoringEnabled,
                    )
                ) {
                    is MangaRegistrationResult.Added -> {
                        if (monitoringEnabled) {
                            monitoringScheduler.enqueueAutomaticMangaCheck(registration.mangaId)
                        }
                        _uiState.value = _uiState.value.copy(
                            isRegisteringManga = false,
                            registeredMangaId = registration.mangaId,
                            shouldPromptForNotificationPermission = monitoringEnabled,
                            message = withRegistrationFallbackNotice(
                                successMessage = if (registration.chapterCount == 1) {
                                    localizedMessage(
                                        english = "Manga added with 1 chapter.",
                                        portugueseBrazilian = "Mangá adicionado com 1 capítulo.",
                                    )
                                } else {
                                    localizedMessage(
                                        english = "Manga added with ${registration.chapterCount} chapters.",
                                        portugueseBrazilian = "Mangá adicionado com ${registration.chapterCount} capítulos.",
                                    )
                                },
                                chapterSelection = chapterSelection,
                            ),
                        )
                    }
                    is MangaRegistrationResult.AlreadyTracked -> {
                        _uiState.value = _uiState.value.copy(
                            isRegisteringManga = false,
                            registeredMangaId = registration.mangaId,
                            shouldPromptForNotificationPermission = false,
                            message = withRegistrationFallbackNotice(
                                successMessage = localizedMessage(
                                    english = "This manga is already in your library.",
                                    portugueseBrazilian = "Este mangá já está na sua biblioteca.",
                                ),
                                chapterSelection = chapterSelection,
                            ),
                        )
                    }
                }
            } catch (error: SQLiteException) {
                _uiState.value = _uiState.value.copy(
                    isRegisteringManga = false,
                    error = localizedMessage(
                        english = "The manga could not be saved to the local library.",
                        portugueseBrazilian = "Não foi possível salvar o mangá na biblioteca local.",
                    ),
                )
            }
        }
    }

    fun analyzePage() {
        analysisJob?.cancel()
        manualTestJob?.cancel()
        manualTestRequestId += 1
        val requestId = ++analysisRequestId
        val inputUrl = _uiState.value.url.trim()
        val normalizedHost = DomainNormalizer.normalizedHostFromUrl(inputUrl)
        if (normalizedHost == null) {
            _uiState.value = _uiState.value.copy(
                error = localizedMessage(
                    english = "Enter a valid public HTTP or HTTPS URL without embedded credentials.",
                    portugueseBrazilian = "Informe uma URL pública válida, HTTP ou HTTPS, sem credenciais incorporadas.",
                ),
                result = null,
                isLoading = false,
                isTestingConfiguration = false,
            )
            currentDocument = null
            return
        }

        analysisJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                result = null,
                manualTestPassed = false,
                isTestingConfiguration = false,
                message = null,
                error = null,
            )
            when (val fetched = htmlFetcher.fetch(inputUrl)) {
                is HtmlFetchResult.Failure -> {
                    if (requestId != analysisRequestId) return@launch
                    currentDocument = null
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        manualConfiguration = _uiState.value.manualConfiguration
                            ?.takeIf { it.normalizedHost == normalizedHost }
                            ?: ManualSiteConfiguration.empty(normalizedHost),
                        isManualConfigurationVisible = false,
                        error = fetched.reason,
                    )
                }
                is HtmlFetchResult.Success -> {
                    if (requestId != analysisRequestId) return@launch
                    currentDocument = fetched.document
                    val savedConfiguration = try {
                        mangaRepository.getSiteConfiguration(normalizedHost)
                    } catch (error: SQLiteException) {
                        if (requestId != analysisRequestId) return@launch
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = localizedMessage(
                                english = "The site's local configuration could not be loaded.",
                                portugueseBrazilian = "Não foi possível carregar a configuração local do site.",
                            ),
                        )
                        return@launch
                    }
                    val manualConfiguration = decodeSavedConfiguration(savedConfiguration, normalizedHost)
                        ?: ManualSiteConfiguration.empty(normalizedHost)
                    val savedConfigurationWasManual =
                        savedConfiguration?.parserId == MANUAL_PARSER_ID
                    val result = withContext(parserDispatcher) {
                        discoveryEngine.discover(
                            document = fetched.document,
                            manualConfiguration = manualConfiguration.takeIf { savedConfigurationWasManual },
                        )
                    }
                    if (requestId != analysisRequestId) return@launch
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        result = result,
                        manualConfiguration = manualConfiguration,
                        isManualConfigurationVisible = false,
                        message = registrationFallbackMessage(inputUrl, result),
                    )
                }
            }
        }
    }

    fun testManualConfiguration() {
        val configuration = _uiState.value.manualConfiguration
        val document = currentDocument
        if (configuration == null || document == null) {
            _uiState.value = _uiState.value.copy(
                error = localizedMessage(
                    english = "Analyze a page before testing a manual configuration.",
                    portugueseBrazilian = "Analise uma página antes de testar uma configuração manual.",
                ),
                manualTestPassed = false,
            )
            return
        }
        val validationErrors = ManualConfigurationValidator.validate(configuration)
        if (validationErrors.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                error = validationErrors.joinToString(" "),
                manualTestPassed = false,
            )
            return
        }

        manualTestJob?.cancel()
        val requestId = ++manualTestRequestId
        manualTestJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isTestingConfiguration = true,
                manualTestPassed = false,
                error = null,
                message = null,
            )
            val result = withContext(parserDispatcher) {
                discoveryEngine.discover(document, manualConfiguration = configuration)
            }
            if (requestId != manualTestRequestId ||
                document !== currentDocument ||
                configuration != _uiState.value.manualConfiguration
            ) {
                return@launch
            }
            val successfulPage = (result as? SiteParseResult.Success)?.page
            val configurationMessage = when {
                successfulPage == null -> null
                !successfulPage.isChapterListComplete ->
                    localizedMessage(
                        english = "Configured fields were found. Add and test a chapter-list selector before treating the result as complete.",
                        portugueseBrazilian = "Os campos configurados foram encontrados. Adicione e teste um seletor da lista de capítulos antes de considerar o resultado completo.",
                    )
                else -> localizedMessage(
                    english = "Configuration passed with a complete chapter list.",
                    portugueseBrazilian = "Configuração aprovada com uma lista completa de capítulos.",
                )
            }
            val registrationWarning = registrationFallbackMessage(_uiState.value.url, result)
            _uiState.value = _uiState.value.copy(
                isTestingConfiguration = false,
                result = result,
                manualTestPassed = successfulPage != null,
                message = listOfNotNull(configurationMessage, registrationWarning)
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(separator = "\n"),
                error = if (result is SiteParseResult.Failure) result.reason else null,
            )
        }
    }

    fun saveManualConfiguration() {
        val configuration = _uiState.value.manualConfiguration
        if (configuration == null || !_uiState.value.manualTestPassed) {
            _uiState.value = _uiState.value.copy(
                error = localizedMessage(
                    english = "Test the current configuration successfully before saving it.",
                    portugueseBrazilian = "Teste a configuração atual com sucesso antes de salvá-la.",
                ),
            )
            return
        }
        val errors = ManualConfigurationValidator.validate(configuration)
        if (errors.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(error = errors.joinToString(" "))
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingConfiguration = true, error = null)
            val now = clock()
            try {
                mangaRepository.saveSiteConfiguration(
                    SiteConfiguration(
                        normalizedHost = configuration.normalizedHost,
                        parserId = MANUAL_PARSER_ID,
                        formatVersion = CONFIGURATION_FORMAT_VERSION,
                        configJson = ManualSiteConfigurationCodec.encode(configuration),
                        updatedAtEpochMillis = now,
                        validatedAtEpochMillis = now,
                    ),
                )
            } catch (error: SQLiteException) {
                _uiState.value = _uiState.value.copy(
                    isSavingConfiguration = false,
                    error = localizedMessage(
                        english = "The site's local configuration could not be saved.",
                        portugueseBrazilian = "Não foi possível salvar a configuração local do site.",
                    ),
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                isSavingConfiguration = false,
                message = localizedMessage(
                    english = "Configuration saved for ${configuration.normalizedHost}.",
                    portugueseBrazilian = "Configuração salva para ${configuration.normalizedHost}.",
                ),
                error = null,
            )
        }
    }

    private fun decodeSavedConfiguration(
        saved: SiteConfiguration?,
        expectedHost: String,
    ): ManualSiteConfiguration? {
        if (saved == null ||
            saved.parserId != MANUAL_PARSER_ID ||
            saved.formatVersion != CONFIGURATION_FORMAT_VERSION
        ) {
            return null
        }
        return when (val decoded = ManualSiteConfigurationCodec.decode(saved.configJson)) {
            is ManualConfigurationDecodeResult.Failure -> {
                _uiState.value = _uiState.value.copy(
                    error = localizedMessage(
                        english = "The saved configuration for $expectedHost could not be loaded: ${decoded.reason}",
                        portugueseBrazilian = "Não foi possível carregar a configuração salva para $expectedHost: ${decoded.reason}",
                    ),
                    isManualConfigurationVisible = true,
                )
                null
            }
            is ManualConfigurationDecodeResult.Success -> {
                if (decoded.configuration.normalizedHost == expectedHost) {
                    decoded.configuration
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = localizedMessage(
                            english = "The saved configuration's domain does not match the analyzed page.",
                            portugueseBrazilian = "O domínio da configuração salva não corresponde à página analisada.",
                        ),
                        isManualConfigurationVisible = true,
                    )
                    null
                }
            }
        }
    }

    private fun registrationFallbackMessage(
        inputUrl: String,
        result: SiteParseResult,
    ): String? {
        val candidate = SiteDiscoveryUiState(
            url = inputUrl,
            result = result,
        ).registrationCandidate ?: return null
        return latestWindowFallbackMessage()
            .takeIf { selectRegistrationChapters(inputUrl, candidate).usedLatestFallback }
    }

    private fun selectRegistrationChapters(
        inputUrl: String,
        page: ParsedSitePage,
    ): RegistrationChapterSelection {
        val currentChapter = checkNotNull(page.currentChapter)
        if (page.isLatestChapterKnown) {
            val latestFirstWindow = (listOf(currentChapter) + page.chapters)
                .distinctBy(ParsedChapter::sourceKey)
            val inputIdentity = chapterUrlIdentity(inputUrl)
            val matchingChapters = inputIdentity?.let { identity ->
                latestFirstWindow.filter { chapterUrlIdentity(it.url) == identity }
            }.orEmpty()
            val entryChapter = matchingChapters.singleOrNull()
            if (entryChapter == null) {
                return RegistrationChapterSelection(
                    entryChapter = currentChapter,
                    chapters = listOf(currentChapter),
                    usedLatestFallback = true,
                )
            }
            val entryIndex = latestFirstWindow.indexOfFirst { it.sourceKey == entryChapter.sourceKey }
            return RegistrationChapterSelection(
                entryChapter = entryChapter,
                chapters = latestFirstWindow.take(entryIndex + 1),
                usedLatestFallback = false,
            )
        }

        val chapters = if (page.isChapterListComplete) {
            (page.chapters + currentChapter).distinctBy(ParsedChapter::sourceKey)
        } else {
            listOf(currentChapter)
        }
        return RegistrationChapterSelection(
            entryChapter = currentChapter,
            chapters = chapters,
            usedLatestFallback = false,
        )
    }

    private fun withRegistrationFallbackNotice(
        successMessage: String,
        chapterSelection: RegistrationChapterSelection,
    ): String =
        if (chapterSelection.usedLatestFallback) {
            "$successMessage\n${latestWindowFallbackMessage()}"
        } else {
            successMessage
        }

    private fun latestWindowFallbackMessage(): String = localizedMessage(
        english = "The chapter from this link was not found in the site's recent window. Registration will use the latest chapter found as its baseline.",
        portugueseBrazilian = "O capítulo deste link não foi encontrado na janela recente do site. O cadastro usará o capítulo mais recente encontrado como referência.",
    )

    private fun chapterUrlIdentity(rawUrl: String): ChapterUrlIdentity? {
        val uri = try {
            URI(rawUrl.trim()).normalize()
        } catch (_: URISyntaxException) {
            return null
        }
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = DomainNormalizer.normalizedHostFromUrl(rawUrl) ?: return null
        val port = when {
            scheme == "http" && uri.port == 80 -> -1
            scheme == "https" && uri.port == 443 -> -1
            else -> uri.port
        }
        val path = uri.rawPath.orEmpty()
            .ifEmpty { "/" }
            .trimEnd('/')
            .ifEmpty { "/" }
        return ChapterUrlIdentity(
            scheme = scheme,
            host = host,
            port = port,
            path = path,
            query = uri.rawQuery,
        )
    }

    private companion object {
        const val CONFIGURATION_FORMAT_VERSION = 1
    }

    private data class RegistrationChapterSelection(
        val entryChapter: ParsedChapter,
        val chapters: List<ParsedChapter>,
        val usedLatestFallback: Boolean,
    )

    private data class ChapterUrlIdentity(
        val scheme: String,
        val host: String,
        val port: Int,
        val path: String,
        val query: String?,
    )
}
