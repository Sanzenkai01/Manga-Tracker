package com.sanzenkai.mangatracker.monitoring

import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sanzenkai.mangatracker.MangaTrackerApplication

class MangaUpdateWorker(
    appContext: android.content.Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val mangaId = inputData.getString(KEY_MANGA_ID)
            ?: return Result.failure(workDataOf(KEY_ERROR_CODE to "MISSING_MANGA_ID"))
        val trigger = inputData.getString(KEY_TRIGGER) ?: TRIGGER_AUTOMATIC
        val container = (applicationContext as? MangaTrackerApplication)?.container
            ?: return Result.failure(workDataOf(KEY_ERROR_CODE to "APPLICATION_NOT_READY"))

        return when (
            val result = container.mangaCheckCoordinator.check(
                mangaId = mangaId,
                trigger = trigger,
                requireMonitoringEnabled = true,
            )
        ) {
            is MangaCheckResult.Success -> {
                Log.i(
                    TAG,
                    "Check succeeded for manga=${result.mangaId} host=${result.normalizedHost} " +
                        "newChapters=${result.newChapterCount}.",
                )
                Result.success(workDataOf(KEY_NEW_CHAPTER_COUNT to result.newChapterCount))
            }
            is MangaCheckResult.Failure -> {
                Log.w(
                    TAG,
                    "Check failed for manga=$mangaId host=${result.normalizedHost} " +
                        "code=${result.errorCode} retryable=${result.retryable} " +
                        "attempt=$runAttemptCount.",
                )
                if (result.retryable) {
                    Result.retry()
                } else {
                    Result.failure(workDataOf(KEY_ERROR_CODE to result.errorCode))
                }
            }
            is MangaCheckResult.Skipped -> {
                Log.i(TAG, "Skipped check for manga=$mangaId: ${result.reason}")
                Result.success(workDataOf(KEY_SKIPPED to true))
            }
        }
    }

    companion object {
        const val KEY_MANGA_ID = "manga_id"
        const val KEY_TRIGGER = "trigger"
        const val KEY_NEW_CHAPTER_COUNT = "new_chapter_count"
        const val KEY_ERROR_CODE = "error_code"
        const val KEY_SKIPPED = "skipped"
        const val TRIGGER_AUTOMATIC = "AUTOMATIC"
        private const val TAG = "MangaUpdateWorker"
    }
}
