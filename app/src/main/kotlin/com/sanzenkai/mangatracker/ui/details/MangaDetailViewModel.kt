package com.sanzenkai.mangatracker.ui.details

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanzenkai.mangatracker.core.localization.localizedMessage
import com.sanzenkai.mangatracker.core.domain.model.ReadingAction
import com.sanzenkai.mangatracker.core.domain.model.ReadingActionOrigin
import com.sanzenkai.mangatracker.core.domain.repository.MangaRepository
import com.sanzenkai.mangatracker.monitoring.MangaCheckCoordinator
import com.sanzenkai.mangatracker.monitoring.MangaCheckResult
import com.sanzenkai.mangatracker.monitoring.MANGA_CHECK_ERROR_INCOMPLETE_CHAPTER_LIST
import com.sanzenkai.mangatracker.monitoring.MonitoringScheduler
import com.sanzenkai.mangatracker.monitoring.NoOpMonitoringScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MangaDetailViewModel(
    private val mangaId: String,
    private val mangaRepository: MangaRepository,
    private val mangaCheckCoordinator: MangaCheckCoordinator,
    private val monitoringScheduler: MonitoringScheduler = NoOpMonitoringScheduler,
) : ViewModel() {
    private val operations = MutableStateFlow(DetailOperationState())

    val uiState: StateFlow<MangaDetailUiState> = combine(
        mangaRepository.observeManga(mangaId),
        mangaRepository.observeChapters(mangaId),
        mangaRepository.observeMonitoringSettings(mangaId),
        operations,
    ) { manga, chapters, monitoringSettings, operation ->
        MangaDetailUiState(
            isLoading = false,
            manga = manga,
            chapters = chapters,
            monitoringSettings = monitoringSettings,
            isRefreshing = operation.isRefreshing,
            error = operation.error,
            message = operation.message,
        )
    }
        .catch { error ->
            emit(
                MangaDetailUiState(
                    isLoading = false,
                    error = localizedMessage(
                        english = "Manga details could not be loaded.",
                        portugueseBrazilian = "Não foi possível carregar os detalhes do mangá.",
                    ),
                ),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MangaDetailUiState(),
        )

    fun setChapterReadState(chapterId: String, isRead: Boolean) {
        viewModelScope.launch {
            try {
                val changed = mangaRepository.setChapterReadState(
                    chapterId = chapterId,
                    action = if (isRead) ReadingAction.READ else ReadingAction.UNREAD,
                    origin = ReadingActionOrigin.DETAIL_SCREEN,
                    occurredAtEpochMillis = System.currentTimeMillis(),
                )
                operations.value = if (changed) {
                    DetailOperationState()
                } else {
                    DetailOperationState(
                        error = localizedMessage(
                            english = "The chapter could not be updated.",
                            portugueseBrazilian = "Não foi possível atualizar o capítulo.",
                        ),
                    )
                }
            } catch (error: SQLiteException) {
                operations.value = DetailOperationState(
                    error = localizedMessage(
                        english = "The reading status could not be saved.",
                        portugueseBrazilian = "Não foi possível salvar o status de leitura.",
                    ),
                )
            }
        }
    }

    fun setMonitoringEnabled(isEnabled: Boolean) {
        viewModelScope.launch {
            try {
                mangaRepository.setMonitoringEnabled(mangaId, isEnabled)
                if (isEnabled) {
                    monitoringScheduler.enqueueAutomaticMangaCheck(mangaId)
                } else {
                    monitoringScheduler.cancelAutomaticMangaCheck(mangaId)
                }
                operations.value = DetailOperationState(
                    message = if (isEnabled) {
                        localizedMessage(
                            english = "Automatic checks are back on. The app will look for new chapters when internet and battery allow.",
                            portugueseBrazilian = "A busca automática foi reativada. O app procura capítulos novos quando há internet e bateria suficiente.",
                        )
                    } else {
                        localizedMessage(
                            english = "Automatic checks are paused. Your chapters and reading history are still saved.",
                            portugueseBrazilian = "A busca automática foi pausada. Seus capítulos e histórico de leitura continuam salvos.",
                        )
                    },
                )
            } catch (error: SQLiteException) {
                operations.value = DetailOperationState(
                    error = localizedMessage(
                        english = "The automatic check setting could not be saved.",
                        portugueseBrazilian = "Não foi possível salvar a configuração da busca automática.",
                    ),
                )
            } catch (error: IllegalStateException) {
                operations.value = DetailOperationState(
                    error = localizedMessage(
                        english = "The setting was saved, but automatic checks could not be updated.",
                        portugueseBrazilian = "A configuração foi salva, mas não foi possível atualizar as buscas automáticas.",
                    ),
                )
            }
        }
    }

    fun reportOpenFailure() {
        operations.value = DetailOperationState(
            error = localizedMessage(
                english = "No installed app can open this chapter link.",
                portugueseBrazilian = "Nenhum aplicativo instalado pode abrir este link de capítulo.",
            ),
        )
    }

    fun refreshManually() {
        if (operations.value.isRefreshing) return
        viewModelScope.launch {
            operations.value = DetailOperationState(isRefreshing = true)
            when (
                val result = mangaCheckCoordinator.check(
                    mangaId = mangaId,
                    trigger = TRIGGER_MANUAL,
                    requireMonitoringEnabled = false,
                )
            ) {
                is MangaCheckResult.Success -> {
                    operations.value = DetailOperationState(
                        message = if (result.newChapterCount == 1) {
                            localizedMessage(
                                english = "1 new chapter found.",
                                portugueseBrazilian = "1 novo capítulo encontrado.",
                            )
                        } else {
                            localizedMessage(
                                english = "${result.newChapterCount} new chapters found.",
                                portugueseBrazilian = "${result.newChapterCount} novos capítulos encontrados.",
                            )
                        },
                    )
                }
                is MangaCheckResult.Failure -> {
                    operations.value = if (
                        result.errorCode == MANGA_CHECK_ERROR_INCOMPLETE_CHAPTER_LIST
                    ) {
                        DetailOperationState(message = result.message)
                    } else {
                        DetailOperationState(error = result.message)
                    }
                }
                is MangaCheckResult.Skipped -> {
                    operations.value = DetailOperationState(error = result.reason)
                }
            }
        }
    }

    private data class DetailOperationState(
        val isRefreshing: Boolean = false,
        val error: String? = null,
        val message: String? = null,
    )

    private companion object {
        const val TRIGGER_MANUAL = "MANUAL"
    }
}
