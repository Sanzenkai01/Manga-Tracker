package com.sanzenkai.mangatracker.monitoring

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.sanzenkai.mangatracker.core.domain.model.MonitoringInterval
import java.util.concurrent.TimeUnit

class WorkManagerMonitoringScheduler(
    context: Context,
) : MonitoringScheduler {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun ensurePeriodicMonitoring() {
        ensurePeriodicMonitoring(MonitoringInterval.DEFAULT)
    }

    override fun ensurePeriodicMonitoring(interval: MonitoringInterval) {
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_MONITORING,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicMonitoringRequest(interval),
        )
    }

    internal fun periodicMonitoringRequest(interval: MonitoringInterval) =
        PeriodicWorkRequestBuilder<MonitoringDispatchWorker>(
            interval.hours,
            TimeUnit.HOURS,
            PERIOD_FLEX_MINUTES,
            TimeUnit.MINUTES,
        )
            .setConstraints(monitoringConstraints())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                RETRY_BACKOFF_MINUTES,
                TimeUnit.MINUTES,
            )
            .addTag(TAG_PERIODIC_MONITORING)
            .build()

    override fun enqueueAutomaticMangaCheck(mangaId: String) {
        val request = OneTimeWorkRequestBuilder<MangaUpdateWorker>()
            .setInputData(
                workDataOf(
                    MangaUpdateWorker.KEY_MANGA_ID to mangaId,
                    MangaUpdateWorker.KEY_TRIGGER to TRIGGER_AUTOMATIC,
                ),
            )
            .setConstraints(monitoringConstraints())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                RETRY_BACKOFF_MINUTES,
                TimeUnit.MINUTES,
            )
            .addTag(TAG_AUTOMATIC_MANGA_CHECK)
            .build()
        workManager.enqueueUniqueWork(
            automaticMangaWorkName(mangaId),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    override fun cancelAutomaticMangaCheck(mangaId: String) {
        workManager.cancelUniqueWork(automaticMangaWorkName(mangaId))
    }

    companion object {
        const val UNIQUE_PERIODIC_MONITORING = "manga-tracker-periodic-monitoring"
        const val TAG_PERIODIC_MONITORING = "manga-tracker-periodic"
        const val TAG_AUTOMATIC_MANGA_CHECK = "manga-tracker-automatic-check"
        const val PERIOD_FLEX_MINUTES = 15L
        const val RETRY_BACKOFF_MINUTES = 30L
        const val TRIGGER_AUTOMATIC = "AUTOMATIC"

        fun automaticMangaWorkName(mangaId: String) = "automatic-manga-check:$mangaId"

        fun monitoringConstraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
    }
}
