package com.sanzenkai.mangatracker.notifications

import android.app.Application
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class NotificationPermissionPromptStoreTest {
    @Test
    fun promptStatePersistsAcrossStoreInstances() {
        val context = RuntimeEnvironment.getApplication()
        val store = NotificationPermissionPromptStore(context)

        assertFalse(store.hasPrompted())

        store.markPrompted()

        assertTrue(NotificationPermissionPromptStore(context).hasPrompted())
    }
}
