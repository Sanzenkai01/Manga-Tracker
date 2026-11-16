package com.sanzenkai.mangatracker.data

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sanzenkai.mangatracker.core.data.DataContainer
import com.sanzenkai.mangatracker.core.data.network.HtmlDocumentFetcher
import com.sanzenkai.mangatracker.core.data.network.SafeJsonFetcher
import com.sanzenkai.mangatracker.core.domain.repository.LibraryPreferencesRepository
import com.sanzenkai.mangatracker.core.domain.repository.MangaRepository
import com.sanzenkai.mangatracker.data.backup.JsonMangaBackupExporter
import com.sanzenkai.mangatracker.data.backup.JsonMangaBackupImporter
import com.sanzenkai.mangatracker.data.backup.MangaBackupExporter
import com.sanzenkai.mangatracker.data.backup.MangaBackupImporter
import com.sanzenkai.mangatracker.core.parser.engine.ParserRegistry
import com.sanzenkai.mangatracker.core.parser.engine.SiteDiscoveryEngine
import com.sanzenkai.mangatracker.parsers.site.MangaSiteParsers
import com.sanzenkai.mangatracker.ui.details.MangaDetailViewModel
import com.sanzenkai.mangatracker.ui.discovery.SiteDiscoveryViewModel
import com.sanzenkai.mangatracker.ui.library.LibraryViewModel
import com.sanzenkai.mangatracker.monitoring.MangaCheckCoordinator
import com.sanzenkai.mangatracker.monitoring.MonitoringScheduler
import com.sanzenkai.mangatracker.monitoring.WorkManagerMonitoringScheduler
import com.sanzenkai.mangatracker.notifications.ChapterNotificationActionHandler
import com.sanzenkai.mangatracker.notifications.ChapterNotificationManager
import com.sanzenkai.mangatracker.notifications.ChapterNotificationPublisher
import com.sanzenkai.mangatracker.ui.settings.SettingsViewModel
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dataContainer = DataContainer(context)
    private val discoveryEngine = SiteDiscoveryEngine(ParserRegistry(MangaSiteParsers.default()))
    private val siteDocumentFetcher = RoliaScanDocumentFetcher(
        htmlFetcher = MairimashitaIrumaKunDocumentFetcher(
            htmlFetcher = ImperioDaBritanniaDocumentFetcher(
                htmlFetcher = dataContainer.safeHtmlFetcher,
                jsonFetcher = dataContainer.safeJsonFetcher,
            ),
        ),
        jsonFetcher = SafeJsonFetcher(allowedHosts = setOf("roliascan.com")),
    )
    val mangaRepository: MangaRepository = dataContainer.mangaRepository
    private val siteProblemNotificationRepository = dataContainer.siteProblemNotificationRepository
    val libraryPreferencesRepository: LibraryPreferencesRepository =
        dataContainer.libraryPreferencesRepository
    private val mangaBackupExporter: MangaBackupExporter = JsonMangaBackupExporter(
        mangaRepository = mangaRepository,
        contentResolver = context.contentResolver,
    )
    private val mangaBackupImporter: MangaBackupImporter = JsonMangaBackupImporter(
        mangaRepository = mangaRepository,
        contentResolver = context.contentResolver,
    )
    val monitoringScheduler: MonitoringScheduler = WorkManagerMonitoringScheduler(context)
    val chapterNotificationPublisher = ChapterNotificationManager(context)
    val chapterNotificationActionHandler = ChapterNotificationActionHandler(
        updateChapterReadState = mangaRepository::setChapterReadState,
        notificationPublisher = chapterNotificationPublisher,
    )
    val mangaCheckCoordinator = MangaCheckCoordinator(
        mangaRepository = mangaRepository,
        htmlFetcher = siteDocumentFetcher,
        discoveryEngine = discoveryEngine,
        notificationPublisher = chapterNotificationPublisher,
        siteProblemNotificationRepository = siteProblemNotificationRepository,
    )
    val siteDiscoveryViewModelFactory: ViewModelProvider.Factory =
        SiteDiscoveryViewModelFactory(
            mangaRepository = mangaRepository,
            htmlFetcher = siteDocumentFetcher,
            discoveryEngine = discoveryEngine,
            monitoringScheduler = monitoringScheduler,
        )
    val libraryViewModelFactory: ViewModelProvider.Factory =
            LibraryViewModelFactory(
                mangaRepository = mangaRepository,
                preferencesRepository = libraryPreferencesRepository,
                monitoringScheduler = monitoringScheduler,
                notificationPublisher = chapterNotificationPublisher,
                mangaCheckCoordinator = mangaCheckCoordinator,
            )
    val settingsViewModelFactory: ViewModelProvider.Factory =
        SettingsViewModelFactory(
            libraryPreferencesRepository,
            mangaBackupExporter,
            mangaBackupImporter,
            monitoringScheduler,
        )

    fun mangaDetailViewModelFactory(mangaId: String): ViewModelProvider.Factory =
        MangaDetailViewModelFactory(
            mangaId = mangaId,
            mangaRepository = mangaRepository,
            mangaCheckCoordinator = mangaCheckCoordinator,
            monitoringScheduler = monitoringScheduler,
        )

    fun ensureMonitoringScheduled() {
        applicationScope.launch {
            try {
                monitoringScheduler.ensurePeriodicMonitoring(
                    libraryPreferencesRepository.observeMonitoringInterval().first(),
                )
            } catch (error: IOException) {
                Log.e(TAG, "Could not load the saved monitoring interval.", error)
            } catch (error: IllegalStateException) {
                Log.e(TAG, "Could not schedule periodic monitoring.", error)
            }
        }
    }

    fun ensureNotificationChannel() {
        chapterNotificationPublisher.ensureChannel()
    }

    private companion object {
        const val TAG = "MangaTrackerApp"
    }
}

private class LibraryViewModelFactory(
    private val mangaRepository: MangaRepository,
    private val preferencesRepository: LibraryPreferencesRepository,
    private val monitoringScheduler: MonitoringScheduler,
    private val notificationPublisher: ChapterNotificationPublisher,
    private val mangaCheckCoordinator: MangaCheckCoordinator,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LibraryViewModel(
                mangaRepository = mangaRepository,
                preferencesRepository = preferencesRepository,
                monitoringScheduler = monitoringScheduler,
                notificationPublisher = notificationPublisher,
                checkMonitoredManga = { mangaId ->
                    mangaCheckCoordinator.check(
                        mangaId = mangaId,
                        trigger = "MANUAL",
                        requireMonitoringEnabled = true,
                    )
                },
            ) as T
        }
        throw IllegalArgumentException("Unsupported ViewModel: ${modelClass.name}")
    }
}

private class SettingsViewModelFactory(
    private val preferencesRepository: LibraryPreferencesRepository,
    private val mangaBackupExporter: MangaBackupExporter,
    private val mangaBackupImporter: MangaBackupImporter,
    private val monitoringScheduler: MonitoringScheduler,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(
                preferencesRepository,
                mangaBackupExporter,
                mangaBackupImporter,
                monitoringScheduler,
            ) as T
        }
        throw IllegalArgumentException("Unsupported ViewModel: ${modelClass.name}")
    }
}

private class MangaDetailViewModelFactory(
    private val mangaId: String,
    private val mangaRepository: MangaRepository,
    private val mangaCheckCoordinator: MangaCheckCoordinator,
    private val monitoringScheduler: MonitoringScheduler,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MangaDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MangaDetailViewModel(
                mangaId = mangaId,
                mangaRepository = mangaRepository,
                mangaCheckCoordinator = mangaCheckCoordinator,
                monitoringScheduler = monitoringScheduler,
            ) as T
        }
        throw IllegalArgumentException("Unsupported ViewModel: ${modelClass.name}")
    }
}

private class SiteDiscoveryViewModelFactory(
    private val mangaRepository: MangaRepository,
    private val htmlFetcher: HtmlDocumentFetcher,
    private val discoveryEngine: SiteDiscoveryEngine,
    private val monitoringScheduler: MonitoringScheduler,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SiteDiscoveryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SiteDiscoveryViewModel(
                mangaRepository = mangaRepository,
                htmlFetcher = htmlFetcher,
                discoveryEngine = discoveryEngine,
                monitoringScheduler = monitoringScheduler,
            ) as T
        }
        throw IllegalArgumentException("Unsupported ViewModel: ${modelClass.name}")
    }
}
