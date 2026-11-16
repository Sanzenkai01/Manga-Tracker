package com.sanzenkai.mangatracker.core.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringProblemSignalsTest {
    @Test
    fun fiveEligibleFailuresIndicateAProblem() {
        assertFalse(signals(siteFailureCount = 4).isProblem(NOW))
        assertTrue(signals(siteFailureCount = 5).isProblem(NOW))
    }

    @Test
    fun staleSuccessThresholdIsStrictlyMoreThanThreeDays() {
        val exactlyThreeDays = signals(
            siteFailureCount = 1,
            lastSuccessAtEpochMillis = NOW - MonitoringProblemSignals.STALE_SUCCESS_THRESHOLD_MILLIS,
        )
        val moreThanThreeDays = exactlyThreeDays.copy(
            lastSuccessAtEpochMillis = exactlyThreeDays.lastSuccessAtEpochMillis!! - 1,
        )

        assertFalse(exactlyThreeDays.isProblem(NOW))
        assertTrue(moreThanThreeDays.isProblem(NOW))
    }

    @Test
    fun firstEligibleFailureIsTheFallbackWhenThereHasNeverBeenASuccess() {
        val firstFailure = signals(
            siteFailureCount = 1,
            lastSuccessAtEpochMillis = null,
            firstSiteFailureAtEpochMillis = NOW - MonitoringProblemSignals.STALE_SUCCESS_THRESHOLD_MILLIS - 1,
        )

        assertTrue(firstFailure.isProblem(NOW))
        assertFalse(MonitoringProblemSignals.empty("manga-1").isProblem(NOW))
    }

    private fun signals(
        siteFailureCount: Int,
        lastSuccessAtEpochMillis: Long? = NOW,
        firstSiteFailureAtEpochMillis: Long? = NOW,
    ) = MonitoringProblemSignals(
        mangaId = "manga-1",
        siteFailureCount = siteFailureCount,
        lastSuccessAtEpochMillis = lastSuccessAtEpochMillis,
        episodeId = "failure-1",
        firstSiteFailureAtEpochMillis = firstSiteFailureAtEpochMillis,
    )

    private companion object {
        const val NOW = 1_000_000_000L
    }
}
