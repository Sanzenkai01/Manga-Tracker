package com.sanzenkai.mangatracker.core.domain.model

data class MonitoringProblemSignals(
    val mangaId: String,
    val siteFailureCount: Int,
    val lastSuccessAtEpochMillis: Long?,
    val episodeId: String?,
    val firstSiteFailureAtEpochMillis: Long?,
) {
    fun isProblem(nowEpochMillis: Long): Boolean {
        if (siteFailureCount <= 0) return false
        if (siteFailureCount >= FAILURE_THRESHOLD) return true

        val lastKnownSuccess = lastSuccessAtEpochMillis ?: firstSiteFailureAtEpochMillis
            ?: return false
        return nowEpochMillis - lastKnownSuccess > STALE_SUCCESS_THRESHOLD_MILLIS
    }

    companion object {
        const val FAILURE_THRESHOLD = 5
        const val STALE_SUCCESS_THRESHOLD_MILLIS = 3L * 24 * 60 * 60 * 1000

        fun empty(mangaId: String) = MonitoringProblemSignals(
            mangaId = mangaId,
            siteFailureCount = 0,
            lastSuccessAtEpochMillis = null,
            episodeId = null,
            firstSiteFailureAtEpochMillis = null,
        )
    }
}
