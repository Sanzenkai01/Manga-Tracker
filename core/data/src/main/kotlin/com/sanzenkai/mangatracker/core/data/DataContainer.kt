package com.sanzenkai.mangatracker.core.data

import android.content.Context
import androidx.room.Room
import com.sanzenkai.mangatracker.core.data.local.MangaTrackerDatabase
import com.sanzenkai.mangatracker.core.data.local.MIGRATION_1_2
import com.sanzenkai.mangatracker.core.data.local.MIGRATION_2_3
import com.sanzenkai.mangatracker.core.data.network.SafeHtmlFetcher
import com.sanzenkai.mangatracker.core.data.network.SafeJsonFetcher
import com.sanzenkai.mangatracker.core.data.preferences.DataStoreLibraryPreferencesRepository
import com.sanzenkai.mangatracker.core.data.preferences.DataStoreSiteProblemNotificationRepository
import com.sanzenkai.mangatracker.core.data.repository.RoomMangaRepository
import com.sanzenkai.mangatracker.core.domain.repository.LibraryPreferencesRepository
import com.sanzenkai.mangatracker.core.domain.repository.MangaRepository
import com.sanzenkai.mangatracker.core.domain.repository.SiteProblemNotificationRepository

class DataContainer(context: Context) {
    val safeHtmlFetcher = SafeHtmlFetcher()
    val safeJsonFetcher = SafeJsonFetcher(
        requestHeadersByHost = mapOf(
            "api.imperiodabritannia.net" to mapOf("X-NoEncryptionBritta" to "1"),
        ),
    )

    private val database = Room.databaseBuilder(
        context.applicationContext,
        MangaTrackerDatabase::class.java,
        DATABASE_NAME,
    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
        .build()

    val mangaRepository: MangaRepository = RoomMangaRepository(database)
    val libraryPreferencesRepository: LibraryPreferencesRepository =
        DataStoreLibraryPreferencesRepository(context)
    val siteProblemNotificationRepository: SiteProblemNotificationRepository =
        DataStoreSiteProblemNotificationRepository(context)

    private companion object {
        const val DATABASE_NAME = "manga_tracker.db"
    }
}
