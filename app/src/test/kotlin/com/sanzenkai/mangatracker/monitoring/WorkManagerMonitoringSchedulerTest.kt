package com.sanzenkai.mangatracker.monitoring

import android.app.Application
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.sanzenkai.mangatracker.core.domain.model.MonitoringInterval
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class WorkManagerMonitoringSchedulerTest {
    @Test
    fun updatesUniquePeriodicDispatchForSelectedIntervalAndKeepsConstraints() {
        val context = RuntimeEnvironment.getApplication()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val workManager = WorkManager.getInstance(context)
        val scheduler = WorkManagerMonitoringScheduler(context)

        assertEquals(NetworkType.CONNECTED, WorkManagerMonitoringScheduler.monitoringConstraints().requiredNetworkType)
        assertTrue(WorkManagerMonitoringScheduler.monitoringConstraints().requiresBatteryNotLow())

        scheduler.ensurePeriodicMonitoring(MonitoringInterval.ONE_HOUR)
        scheduler.ensurePeriodicMonitoring(MonitoringInterval.THREE_HOURS)
        val periodicWork = workManager
            .getWorkInfosForUniqueWork(WorkManagerMonitoringScheduler.UNIQUE_PERIODIC_MONITORING)
            .get()
        assertEquals(1, periodicWork.size)
        assertTrue(periodicWork.single().state == WorkInfo.State.ENQUEUED ||
            periodicWork.single().state == WorkInfo.State.RUNNING)
        assertEquals(
            TimeUnit.HOURS.toMillis(6),
            scheduler.periodicMonitoringRequest(MonitoringInterval.SIX_HOURS).workSpec.intervalDuration,
        )

        val mangaId = "manga-1"
        scheduler.enqueueAutomaticMangaCheck(mangaId)
        scheduler.enqueueAutomaticMangaCheck(mangaId)
        val workName = WorkManagerMonitoringScheduler.automaticMangaWorkName(mangaId)
        val automaticWork = workManager.getWorkInfosForUniqueWork(workName).get()
        assertEquals(1, automaticWork.size)
        assertTrue(automaticWork.single().tags.contains(WorkManagerMonitoringScheduler.TAG_AUTOMATIC_MANGA_CHECK))

        scheduler.cancelAutomaticMangaCheck(mangaId)
        val canceledWork = workManager.getWorkInfosForUniqueWork(workName).get()
        assertEquals(WorkInfo.State.CANCELLED, canceledWork.single().state)
    }
}
