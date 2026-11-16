package com.sanzenkai.mangatracker.navigation

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sanzenkai.mangatracker.R
import com.sanzenkai.mangatracker.data.AppContainer
import com.sanzenkai.mangatracker.notifications.hasNotificationPermission
import com.sanzenkai.mangatracker.notifications.openAppNotificationSettings
import com.sanzenkai.mangatracker.ui.details.MangaDetailScreen
import com.sanzenkai.mangatracker.ui.details.MangaDetailViewModel
import com.sanzenkai.mangatracker.ui.discovery.SiteDiscoveryScreen
import com.sanzenkai.mangatracker.ui.discovery.SiteDiscoveryViewModel
import com.sanzenkai.mangatracker.ui.library.LibraryScreen
import com.sanzenkai.mangatracker.ui.library.LibraryViewModel
import com.sanzenkai.mangatracker.ui.settings.SettingsScreen
import com.sanzenkai.mangatracker.ui.settings.SettingsViewModel

private const val LIBRARY_ROUTE = "library"
private const val SITE_DISCOVERY_ROUTE = "site-discovery"
private const val SETTINGS_ROUTE = "settings"
private const val DETAIL_ROUTE = "manga/{mangaId}"

@Composable
fun MangaTrackerNavigation(
    container: AppContainer,
    settingsViewModel: SettingsViewModel,
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = LIBRARY_ROUTE,
    ) {
        composable(LIBRARY_ROUTE) {
            val viewModel: LibraryViewModel = viewModel(
                factory = container.libraryViewModelFactory,
            )
            val state = viewModel.uiState.collectAsStateWithLifecycle()
            LibraryScreen(
                state = state.value,
                onDiscoverSites = { navController.navigate(SITE_DISCOVERY_ROUTE) },
                onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
                onLayoutChanged = viewModel::setLayout,
                onReadFilterChanged = viewModel::setReadFilter,
                onRefreshAll = viewModel::refreshAllMonitoredManga,
                onMangaLongPress = viewModel::selectManga,
                onMangaSelectionToggle = viewModel::toggleMangaSelection,
                onClearSelection = viewModel::clearSelection,
                onMarkSelectedAsRead = viewModel::markSelectedAsRead,
                onMarkSelectedAsUnread = viewModel::markSelectedAsUnread,
                onRemoveSelected = viewModel::removeSelectedMangas,
                onMangaClick = { mangaId ->
                    navController.navigate("manga/${Uri.encode(mangaId)}")
                },
            )
        }
        composable(SETTINGS_ROUTE) {
            val state = settingsViewModel.uiState.collectAsStateWithLifecycle()
            val lifecycleOwner = LocalLifecycleOwner.current
            var notificationsEnabled by remember(context) {
                mutableStateOf(hasNotificationPermission(context))
            }
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) {
                notificationsEnabled = hasNotificationPermission(context)
            }
            val backupDocumentLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json"),
            ) { uri ->
                uri?.let(settingsViewModel::exportBackup)
            }
            val backupOpenLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri ->
                uri?.let(settingsViewModel::importBackup)
            }
            DisposableEffect(lifecycleOwner, context) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        notificationsEnabled = hasNotificationPermission(context)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            SettingsScreen(
                state = state.value,
                notificationsEnabled = notificationsEnabled,
                onBack = { navController.popBackStack() },
                onLayoutChanged = settingsViewModel::setLibraryLayout,
                onThemeModeChanged = settingsViewModel::setThemeMode,
                onMonitoringIntervalChanged = settingsViewModel::setMonitoringInterval,
                onEnableNotifications = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        openAppNotificationSettings(context)
                    }
                },
                onOpenNotificationSettings = { openAppNotificationSettings(context) },
                onConfigureSite = { navController.navigate(SITE_DISCOVERY_ROUTE) },
                onExportBackup = {
                    backupDocumentLauncher.launch(
                        context.getString(R.string.settings_backup_file_name),
                    )
                },
                onImportBackup = {
                    backupOpenLauncher.launch(
                        arrayOf("application/json", "text/json", "application/octet-stream"),
                    )
                },
            )
        }
        composable(DETAIL_ROUTE) { backStackEntry ->
            val mangaId = requireNotNull(backStackEntry.arguments?.getString("mangaId"))
            val viewModel: MangaDetailViewModel = viewModel(
                factory = container.mangaDetailViewModelFactory(mangaId),
            )
            val state = viewModel.uiState.collectAsStateWithLifecycle()
            MangaDetailScreen(
                state = state.value,
                onBack = { navController.popBackStack() },
                onRefresh = viewModel::refreshManually,
                onMonitoringChanged = viewModel::setMonitoringEnabled,
                onChapterReadChanged = viewModel::setChapterReadState,
                onOpenChapter = { url ->
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                    } catch (_: ActivityNotFoundException) {
                        viewModel.reportOpenFailure()
                    }
                },
            )
        }
        composable(SITE_DISCOVERY_ROUTE) {
            val viewModel: SiteDiscoveryViewModel = viewModel(
                factory = container.siteDiscoveryViewModelFactory,
            )
            val state = viewModel.uiState.collectAsStateWithLifecycle()
            SiteDiscoveryScreen(
                state = state.value,
                onUrlChanged = viewModel::onUrlChanged,
                onAnalyze = viewModel::analyzePage,
                onConfigurationChanged = viewModel::onManualConfigurationChanged,
                onManualVisibilityChanged = viewModel::onManualConfigurationVisibilityChanged,
                onTestConfiguration = viewModel::testManualConfiguration,
                onSaveConfiguration = viewModel::saveManualConfiguration,
                onRegisterManga = viewModel::registerManga,
                onMangaRegistered = { mangaId ->
                    navController.navigate("manga/${Uri.encode(mangaId)}") {
                        popUpTo(SITE_DISCOVERY_ROUTE) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
