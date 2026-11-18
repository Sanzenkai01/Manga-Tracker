package com.sanzenkai.mangatracker.ui.settings

import android.database.sqlite.SQLiteException
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanzenkai.mangatracker.core.localization.localizedMessage
import com.sanzenkai.mangatracker.core.domain.model.AppThemeMode
import com.sanzenkai.mangatracker.core.domain.model.LibraryLayout
import com.sanzenkai.mangatracker.core.domain.model.MonitoringInterval
import com.sanzenkai.mangatracker.core.domain.repository.LibraryPreferencesRepository
import com.sanzenkai.mangatracker.data.backup.MangaBackupExporter
import com.sanzenkai.mangatracker.data.backup.MangaBackupImporter
import com.sanzenkai.mangatracker.monitoring.MonitoringScheduler
import com.sanzenkai.mangatracker.monitoring.NoOpMonitoringScheduler
import java.io.IOException
import org.json.JSONException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferencesRepository: LibraryPreferencesRepository,
    private val mangaBackupExporter: MangaBackupExporter,
    private val mangaBackupImporter: MangaBackupImporter,
    private val monitoringScheduler: MonitoringScheduler = NoOpMonitoringScheduler,
) : ViewModel() {
    private val actionError = MutableStateFlow<String?>(null)
    private val backupState = MutableStateFlow(BackupUiState())

    val uiState = combine(
        preferencesRepository.observeLayout(),
        preferencesRepository.observeThemeMode(),
        preferencesRepository.observeMonitoringInterval(),
        actionError,
        backupState,
    ) { layout, themeMode, monitoringInterval, error, backup ->
        SettingsUiState(
            isLoading = false,
            isThemePreferenceLoaded = true,
            libraryLayout = layout,
            themeMode = themeMode,
            monitoringInterval = monitoringInterval,
            error = error,
            isBackupInProgress = backup.isInProgress,
            isBackupImporting = backup.isImporting,
            backupMessage = backup.message,
            isBackupError = backup.isError,
        )
    }
        .catch { error ->
            emit(
                SettingsUiState(
                    isLoading = false,
                    error = localizedMessage(
                        english = "Settings could not be loaded.",
                        portugueseBrazilian = "Não foi possível carregar as configurações.",
                    ),
                ),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(),
        )

    fun setLibraryLayout(layout: LibraryLayout) {
        viewModelScope.launch {
            try {
                preferencesRepository.setLayout(layout)
                actionError.value = null
            } catch (error: IOException) {
                actionError.value = localizedMessage(
                    english = "The library layout could not be saved.",
                    portugueseBrazilian = "Não foi possível salvar a visualização da biblioteca.",
                )
            }
        }
    }

    fun setThemeMode(themeMode: AppThemeMode) {
        viewModelScope.launch {
            try {
                preferencesRepository.setThemeMode(themeMode)
                actionError.value = null
            } catch (error: IOException) {
                actionError.value = localizedMessage(
                    english = "The app theme could not be saved.",
                    portugueseBrazilian = "Não foi possível salvar o tema do aplicativo.",
                )
            }
        }
    }

    fun setMonitoringInterval(interval: MonitoringInterval) {
        viewModelScope.launch {
            try {
                preferencesRepository.setMonitoringInterval(interval)
            } catch (error: IOException) {
                actionError.value = localizedMessage(
                    english = "The monitoring interval could not be saved.",
                    portugueseBrazilian = "Não foi possível salvar o intervalo de monitoramento.",
                )
                return@launch
            }

            try {
                monitoringScheduler.ensurePeriodicMonitoring(interval)
                actionError.value = null
            } catch (error: IllegalStateException) {
                actionError.value = localizedMessage(
                    english = "The interval was saved, but the periodic schedule could not be updated.",
                    portugueseBrazilian = "O intervalo foi salvo, mas não foi possível atualizar o agendamento periódico.",
                )
            }
        }
    }

    fun exportBackup(destination: Uri) {
        if (backupState.value.isInProgress) return
        backupState.value = BackupUiState(isInProgress = true)
        viewModelScope.launch {
            try {
                mangaBackupExporter.export(destination)
                backupState.value = BackupUiState(
                    message = localizedMessage(
                        english = "Backup saved.",
                        portugueseBrazilian = "Backup salvo.",
                    ),
                )
            } catch (error: IOException) {
                backupState.value = BackupUiState(
                    message = localizedMessage(
                        english = "The backup could not be saved. Choose another location and try again.",
                        portugueseBrazilian = "Não foi possível salvar o backup. Escolha outro local e tente novamente.",
                    ),
                    isError = true,
                )
            } catch (error: SecurityException) {
                backupState.value = BackupUiState(
                    message = localizedMessage(
                        english = "The selected location did not allow the backup to be saved.",
                        portugueseBrazilian = "O local escolhido não permitiu salvar o backup.",
                    ),
                    isError = true,
                )
            } catch (error: SQLiteException) {
                backupState.value = BackupUiState(
                    message = localizedMessage(
                        english = "The saved library could not be read for the backup.",
                        portugueseBrazilian = "Não foi possível ler a biblioteca salva para criar o backup.",
                    ),
                    isError = true,
                )
            } catch (error: JSONException) {
                backupState.value = BackupUiState(
                    message = localizedMessage(
                        english = "The backup file could not be created.",
                        portugueseBrazilian = "Não foi possível criar o arquivo de backup.",
                    ),
                    isError = true,
                )
            } catch (error: IllegalArgumentException) {
                backupState.value = BackupUiState(
                    message = localizedMessage(
                        english = "The selected location could not be used for the backup.",
                        portugueseBrazilian = "Não foi possível usar o local escolhido para o backup.",
                    ),
                    isError = true,
                )
            } catch (error: IllegalStateException) {
                backupState.value = BackupUiState(
                    message = localizedMessage(
                        english = "The saved library could not be read for the backup.",
                        portugueseBrazilian = "Não foi possível ler a biblioteca salva para criar o backup.",
                    ),
                    isError = true,
                )
            }
        }
    }

    fun importBackup(source: Uri) {
        if (backupState.value.isInProgress) return
        backupState.value = BackupUiState(isInProgress = true, isImporting = true)
        viewModelScope.launch {
            try {
                mangaBackupImporter.importBackup(source)
                backupState.value = BackupUiState(
                    message = localizedMessage(
                        english = "Backup loaded. Existing library data was preserved and merged.",
                        portugueseBrazilian = "Backup carregado. Os dados existentes foram mantidos e mesclados.",
                    ),
                )
            } catch (error: IOException) {
                backupState.value = BackupUiState(
                    message = localizedMessage(
                        english = "The backup could not be opened. No library data was changed.",
                        portugueseBrazilian = "Não foi possível abrir o backup. Nenhum dado da biblioteca foi alterado.",
                    ),
                    isError = true,
                )
            } catch (error: SecurityException) {
                backupState.value = BackupUiState(
                    message = localizedMessage(
                        english = "The selected file could not be accessed. No library data was changed.",
                        portugueseBrazilian = "Não foi possível acessar o arquivo selecionado. Nenhum dado da biblioteca foi alterado.",
                    ),
                    isError = true,
                )
            } catch (error: SQLiteException) {
                backupState.value = BackupUiState(
                    message = localizedMessage(
                        english = "The backup could not be merged. Your current library was kept unchanged.",
                        portugueseBrazilian = "Não foi possível mesclar o backup. Sua biblioteca atual foi mantida sem alterações.",
                    ),
                    isError = true,
                )
            } catch (error: JSONException) {
                backupState.value = BackupUiState(
                    message = localizedMessage(
                        english = "The file is not a valid Manga Tracker backup. No library data was changed.",
                        portugueseBrazilian = "O arquivo não é um backup válido do Manga Tracker. Nenhum dado da biblioteca foi alterado.",
                    ),
                    isError = true,
                )
            } catch (error: IllegalArgumentException) {
                backupState.value = BackupUiState(
                    message = localizedMessage(
                        english = "The backup is invalid or unsupported. No library data was changed.",
                        portugueseBrazilian = "O backup é inválido ou incompatível. Nenhum dado da biblioteca foi alterado.",
                    ),
                    isError = true,
                )
            } catch (error: IllegalStateException) {
                backupState.value = BackupUiState(
                    message = localizedMessage(
                        english = "The backup could not be merged. Your current library was kept unchanged.",
                        portugueseBrazilian = "Não foi possível mesclar o backup. Sua biblioteca atual foi mantida sem alterações.",
                    ),
                    isError = true,
                )
            }
        }
    }

    private data class BackupUiState(
        val isInProgress: Boolean = false,
        val isImporting: Boolean = false,
        val message: String? = null,
        val isError: Boolean = false,
    )
}
