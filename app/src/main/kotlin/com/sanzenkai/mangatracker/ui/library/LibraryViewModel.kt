package com.sanzenkai.mangatracker.ui.library

import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanzenkai.mangatracker.core.localization.localizedMessage
import com.sanzenkai.mangatracker.core.domain.model.LibraryLayout
import com.sanzenkai.mangatracker.core.domain.model.MangaSummary
import com.sanzenkai.mangatracker.core.domain.model.ReadingAction
import com.sanzenkai.mangatracker.core.domain.model.ReadingActionOrigin
import com.sanzenkai.mangatracker.core.domain.repository.LibraryPreferencesRepository
import com.sanzenkai.mangatracker.core.domain.repository.MangaRepository
import com.sanzenkai.mangatracker.monitoring.MangaCheckResult
import com.sanzenkai.mangatracker.monitoring.MonitoringScheduler
import com.sanzenkai.mangatracker.monitoring.NoOpMonitoringScheduler
import com.sanzenkai.mangatracker.notifications.ChapterNotificationPublisher
import com.sanzenkai.mangatracker.notifications.NoOpChapterNotificationPublisher
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val mangaRepository: MangaRepository,
    private val preferencesRepository: LibraryPreferencesRepository,
    private val monitoringScheduler: MonitoringScheduler = NoOpMonitoringScheduler,
    private val notificationPublisher: ChapterNotificationPublisher =
        NoOpChapterNotificationPublisher,
    private val checkMonitoredManga: suspend (String) -> MangaCheckResult,
) : ViewModel() {
    private val preferenceError = MutableStateFlow<String?>(null)
    private val interactionState = MutableStateFlow(LibraryInteractionState())

    val uiState = combine(
        mangaRepository.observeLibrary(),
        preferencesRepository.observeLayout(),
        preferenceError,
        interactionState,
    ) { mangas, layout, error, interaction ->
        LibraryUiState(
            isLoading = false,
            mangas = mangas,
            layout = layout,
            readFilter = interaction.readFilter,
            isRefreshing = interaction.isRefreshing,
            refreshSummary = interaction.refreshSummary,
            refreshError = interaction.refreshError,
            selectedMangaIds = interaction.selectedMangaIds,
            isActionInProgress = interaction.isActionInProgress,
            actionError = interaction.actionError,
            error = error,
        )
    }
        .catch { error ->
            emit(
                LibraryUiState(
                    isLoading = false,
                    error = localizedMessage(
                        english = "The library could not be loaded.",
                        portugueseBrazilian = "Não foi possível carregar a biblioteca.",
                    ),
                ),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LibraryUiState(),
        )

    fun setLayout(layout: LibraryLayout) {
        viewModelScope.launch {
            try {
                preferencesRepository.setLayout(layout)
                preferenceError.value = null
            } catch (error: IOException) {
                preferenceError.value = localizedMessage(
                    english = "The library layout could not be saved.",
                    portugueseBrazilian = "Não foi possível salvar a visualização da biblioteca.",
                )
            }
        }
    }

    fun setReadFilter(filter: LibraryReadFilter) {
        interactionState.value = interactionState.value.copy(readFilter = filter)
    }

    fun refreshAllMonitoredManga() {
        val current = interactionState.value
        if (current.isRefreshing || current.isActionInProgress) return

        val mangaIds = uiState.value.mangas
            .filter(MangaSummary::monitoringEnabled)
            .map(MangaSummary::id)
        if (mangaIds.isEmpty()) return

        interactionState.value = current.copy(
            isRefreshing = true,
            refreshSummary = null,
            refreshError = null,
        )
        viewModelScope.launch {
            var updatedCount = 0
            var failedCount = 0
            var currentMangaId: String? = null
            var refreshError: String? = null
            var completed = false
            try {
                mangaIds.forEach { mangaId ->
                    currentMangaId = mangaId
                    when (checkMonitoredManga(mangaId)) {
                        is MangaCheckResult.Success -> updatedCount++
                        is MangaCheckResult.Failure -> failedCount++
                        is MangaCheckResult.Skipped -> Unit
                    }
                    currentMangaId = null
                }
                completed = true
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (currentMangaId != null) failedCount++
                Log.e(TAG, "Could not finish refreshing monitored manga.", error)
                refreshError = localizedMessage(
                    english = "The refresh could not be completed.",
                    portugueseBrazilian = "Não foi possível concluir a atualização.",
                )
            } finally {
                interactionState.value = interactionState.value.copy(
                    isRefreshing = false,
                    refreshSummary = if (completed || refreshError != null) {
                        LibraryRefreshSummary(updatedCount, failedCount)
                    } else {
                        null
                    },
                    refreshError = refreshError,
                )
            }
        }
    }

    fun selectManga(mangaId: String) {
        val current = interactionState.value
        if (current.isActionInProgress) return
        interactionState.value = current.copy(
            selectedMangaIds = current.selectedMangaIds + mangaId,
            actionError = null,
        )
    }

    fun toggleMangaSelection(mangaId: String) {
        val current = interactionState.value
        if (current.isActionInProgress) return
        val selected = current.selectedMangaIds
        interactionState.value = current.copy(
            selectedMangaIds = if (mangaId in selected) selected - mangaId else selected + mangaId,
            actionError = null,
        )
    }

    fun clearSelection() {
        val current = interactionState.value
        if (current.isActionInProgress) return
        interactionState.value = current.copy(
            selectedMangaIds = emptySet(),
            actionError = null,
        )
    }

    fun markSelectedAsRead() {
        updateSelectedReadState(ReadingAction.READ)
    }

    fun markSelectedAsUnread() {
        updateSelectedReadState(ReadingAction.UNREAD)
    }

    fun removeSelectedMangas() {
        val selectedIds = interactionState.value.selectedMangaIds
        if (selectedIds.isEmpty() || interactionState.value.isActionInProgress) return

        viewModelScope.launch {
            interactionState.value = interactionState.value.copy(
                isActionInProgress = true,
                actionError = null,
            )
            try {
                mangaRepository.removeMangas(selectedIds)
                selectedIds.forEach(monitoringScheduler::cancelAutomaticMangaCheck)
                notificationPublisher.cancelMangaNotifications(selectedIds)
                interactionState.value = interactionState.value.copy(
                    selectedMangaIds = emptySet(),
                )
            } catch (_: SQLiteException) {
                interactionState.value = interactionState.value.copy(
                    actionError = localizedMessage(
                        english = "The selected manga could not be removed.",
                        portugueseBrazilian = "Não foi possível remover os mangás selecionados.",
                    ),
                )
            } finally {
                interactionState.value = interactionState.value.copy(isActionInProgress = false)
            }
        }
    }

    private fun updateSelectedReadState(action: ReadingAction) {
        val selectedIds = interactionState.value.selectedMangaIds
        if (selectedIds.isEmpty() || interactionState.value.isActionInProgress) return

        viewModelScope.launch {
            interactionState.value = interactionState.value.copy(
                isActionInProgress = true,
                actionError = null,
            )
            try {
                mangaRepository.setMangaReadState(
                    mangaIds = selectedIds,
                    action = action,
                    origin = ReadingActionOrigin.LIBRARY_BULK_ACTION,
                    occurredAtEpochMillis = System.currentTimeMillis(),
                )
                interactionState.value = interactionState.value.copy(
                    selectedMangaIds = emptySet(),
                )
            } catch (_: SQLiteException) {
                interactionState.value = interactionState.value.copy(
                    actionError = localizedMessage(
                        english = "The reading status could not be updated.",
                        portugueseBrazilian = "Não foi possível atualizar o status de leitura.",
                    ),
                )
            } finally {
                interactionState.value = interactionState.value.copy(isActionInProgress = false)
            }
        }
    }

    private data class LibraryInteractionState(
        val readFilter: LibraryReadFilter = LibraryReadFilter.ALL,
        val isRefreshing: Boolean = false,
        val refreshSummary: LibraryRefreshSummary? = null,
        val refreshError: String? = null,
        val selectedMangaIds: Set<String> = emptySet(),
        val isActionInProgress: Boolean = false,
        val actionError: String? = null,
    )

    private companion object {
        const val TAG = "LibraryViewModel"
    }
}
