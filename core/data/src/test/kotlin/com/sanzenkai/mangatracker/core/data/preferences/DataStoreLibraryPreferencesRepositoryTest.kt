package com.sanzenkai.mangatracker.core.data.preferences

import com.sanzenkai.mangatracker.core.domain.model.AppThemeMode
import com.sanzenkai.mangatracker.core.domain.model.MonitoringInterval
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DataStoreLibraryPreferencesRepositoryTest {
    @Test
    fun themeModeDefaultsToSystemAndPersistsAcrossRepositoryInstances() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val repository = DataStoreLibraryPreferencesRepository(context)

        assertEquals(AppThemeMode.SYSTEM, repository.observeThemeMode().first())

        repository.setThemeMode(AppThemeMode.DARK)

        assertEquals(
            AppThemeMode.DARK,
            DataStoreLibraryPreferencesRepository(context).observeThemeMode().first(),
        )
    }

    @Test
    fun monitoringIntervalDefaultsToOneHourAndPersistsAcrossRepositoryInstances() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val repository = DataStoreLibraryPreferencesRepository(context)

        assertEquals(MonitoringInterval.ONE_HOUR, repository.observeMonitoringInterval().first())

        repository.setMonitoringInterval(MonitoringInterval.SIX_HOURS)

        assertEquals(
            MonitoringInterval.SIX_HOURS,
            DataStoreLibraryPreferencesRepository(context).observeMonitoringInterval().first(),
        )
    }
}
