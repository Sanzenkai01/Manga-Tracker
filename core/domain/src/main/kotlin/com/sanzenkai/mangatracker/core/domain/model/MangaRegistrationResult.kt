package com.sanzenkai.mangatracker.core.domain.model

sealed interface MangaRegistrationResult {
    data class Added(
        val mangaId: String,
        val chapterCount: Int,
    ) : MangaRegistrationResult

    data class AlreadyTracked(
        val mangaId: String,
    ) : MangaRegistrationResult
}
