package com.sanzenkai.mangatracker.core.data.preferences

import android.app.Application
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class DataStoreSiteProblemNotificationRepositoryTest {
    @Test
    fun episodeClaimPersistsAcrossRepositoryInstancesAndAllowsANewEpisode() = runBlocking {
        val mangaId = UUID.randomUUID().toString()
        val firstRepository = DataStoreSiteProblemNotificationRepository(
            RuntimeEnvironment.getApplication(),
        )

        assertTrue(firstRepository.claimEpisodeNotification(mangaId, "episode-1"))
        assertFalse(
            DataStoreSiteProblemNotificationRepository(RuntimeEnvironment.getApplication())
                .claimEpisodeNotification(mangaId, "episode-1"),
        )
        assertTrue(firstRepository.claimEpisodeNotification(mangaId, "episode-2"))
    }
}
