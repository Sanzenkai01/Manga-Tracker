package com.sanzenkai.mangatracker.monitoring

const val MANGA_CHECK_ERROR_INCOMPLETE_CHAPTER_LIST = "INCOMPLETE_CHAPTER_LIST"
const val MANGA_CHECK_OUTCOME_INCOMPLETE_LIST = "INCOMPLETE_LIST"

sealed interface MangaCheckResult {
    data class Success(
        val mangaId: String,
        val normalizedHost: String,
        val newChapterCount: Int,
        val checkedAtEpochMillis: Long,
    ) : MangaCheckResult

    data class Failure(
        val mangaId: String,
        val normalizedHost: String?,
        val errorCode: String,
        val message: String,
        val retryable: Boolean,
    ) : MangaCheckResult

    data class Skipped(
        val mangaId: String,
        val reason: String,
    ) : MangaCheckResult
}
