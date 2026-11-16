package com.sanzenkai.mangatracker.monitoring

import com.sanzenkai.mangatracker.core.domain.model.MonitoringInterval

interface MonitoringScheduler {
    fun ensurePeriodicMonitoring()

    fun ensurePeriodicMonitoring(interval: MonitoringInterval) = ensurePeriodicMonitoring()

    fun enqueueAutomaticMangaCheck(mangaId: String)

    fun cancelAutomaticMangaCheck(mangaId: String)
}

object NoOpMonitoringScheduler : MonitoringScheduler {
    override fun ensurePeriodicMonitoring() = Unit

    override fun enqueueAutomaticMangaCheck(mangaId: String) = Unit

    override fun cancelAutomaticMangaCheck(mangaId: String) = Unit
}
