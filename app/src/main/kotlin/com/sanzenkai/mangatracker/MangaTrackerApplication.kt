package com.sanzenkai.mangatracker

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.sanzenkai.mangatracker.data.AppContainer
import com.sanzenkai.mangatracker.core.data.network.SafePublicImageHttpClient

class MangaTrackerApplication : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient { SafePublicImageHttpClient().create() }
            .build()

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.ensureMonitoringScheduled()
        container.ensureNotificationChannel()
    }
}
