package com.sanzenkai.mangatracker.monitoring

interface MonitoringScheduler {
    fun ensurePeriodicMonitoring()

    fun enqueueAutomaticMangaCheck(mangaId: String)

    fun cancelAutomaticMangaCheck(mangaId: String)
}

object NoOpMonitoringScheduler : MonitoringScheduler {
    override fun ensurePeriodicMonitoring() = Unit

    override fun enqueueAutomaticMangaCheck(mangaId: String) = Unit

    override fun cancelAutomaticMangaCheck(mangaId: String) = Unit
}
