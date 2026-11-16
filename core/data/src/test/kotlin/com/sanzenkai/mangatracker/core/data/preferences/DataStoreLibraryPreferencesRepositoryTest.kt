package com.sanzenkai.mangatracker.core.data.preferences

import com.sanzenkai.mangatracker.core.domain.model.AppThemeMode
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
}
