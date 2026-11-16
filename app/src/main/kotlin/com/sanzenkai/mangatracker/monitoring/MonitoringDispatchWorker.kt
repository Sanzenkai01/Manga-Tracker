package com.sanzenkai.mangatracker.monitoring

import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sanzenkai.mangatracker.MangaTrackerApplication
import kotlinx.coroutines.flow.first

class MonitoringDispatchWorker(
    appContext: android.content.Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as? MangaTrackerApplication)?.container
            ?: return Result.failure()
        val activeManga = try {
            container.mangaRepository.observeLibrary().first()
                .filter { it.monitoringEnabled }
        } catch (error: SQLiteException) {
            Log.w(TAG, "Could not load active manga for periodic monitoring.", error)
            return Result.retry()
        }

        var enqueuedCount = 0
        try {
            activeManga.forEach { manga ->
                if (isStopped) return Result.success()
                val stillEnabled = container.mangaRepository
                    .observeMonitoringSettings(manga.id)
                    .first()
                    ?.isEnabled == true
                if (stillEnabled) {
                    container.monitoringScheduler.enqueueAutomaticMangaCheck(manga.id)
                    enqueuedCount++
                }
            }
        } catch (error: SQLiteException) {
            Log.w(TAG, "Could not verify per-manga monitoring settings.", error)
            return Result.retry()
        } catch (error: IllegalStateException) {
            Log.w(TAG, "Could not enqueue an automatic manga check.", error)
            return Result.retry()
        }
        Log.i(TAG, "Periodic dispatch queued $enqueuedCount active manga checks.")
        return Result.success()
    }

    private companion object {
        const val TAG = "MangaMonitorDispatch"
    }
}
