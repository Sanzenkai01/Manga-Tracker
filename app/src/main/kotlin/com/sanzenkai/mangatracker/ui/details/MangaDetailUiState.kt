package com.sanzenkai.mangatracker.ui.details

import com.sanzenkai.mangatracker.core.domain.model.Manga
import com.sanzenkai.mangatracker.core.domain.model.MonitoringSettings
import com.sanzenkai.mangatracker.core.domain.model.TrackedChapter

data class MangaDetailUiState(
    val isLoading: Boolean = true,
    val manga: Manga? = null,
    val chapters: List<TrackedChapter> = emptyList(),
    val monitoringSettings: MonitoringSettings? = null,
    val isMonitoringProblem: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)
