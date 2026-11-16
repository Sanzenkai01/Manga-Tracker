package com.sanzenkai.mangatracker.core.domain.repository

interface SiteProblemNotificationRepository {
    suspend fun claimEpisodeNotification(mangaId: String, episodeId: String): Boolean
}

object NoOpSiteProblemNotificationRepository : SiteProblemNotificationRepository {
    override suspend fun claimEpisodeNotification(mangaId: String, episodeId: String) = false
}
