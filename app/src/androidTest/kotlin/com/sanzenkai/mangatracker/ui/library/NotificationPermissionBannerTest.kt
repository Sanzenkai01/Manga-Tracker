package com.sanzenkai.mangatracker.ui.library

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationPermissionBannerTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun warningIsVisibleOnlyWhenMonitoredMangaCannotNotify() {
        val hasMonitoredManga = mutableStateOf(true)
        val notificationsAvailable = mutableStateOf(false)
        var openedSettings = false
        val warning = "Notifications are off or blocked. Turn them on to receive alerts for monitored manga."
        val settingsAction = "Notification settings"

        composeRule.setContent {
            MaterialTheme {
                NotificationPermissionBanner(
                    hasMonitoredManga = hasMonitoredManga.value,
                    notificationsAvailable = notificationsAvailable.value,
                    onOpenNotificationSettings = { openedSettings = true },
                )
            }
        }

        composeRule.onNodeWithText(warning).assertIsDisplayed()
        composeRule.onNodeWithText(settingsAction).assertIsDisplayed().performClick()
        assertTrue(openedSettings)

        composeRule.runOnIdle {
            hasMonitoredManga.value = false
        }
        composeRule.onAllNodesWithText(warning).assertCountEquals(0)

        composeRule.runOnIdle {
            hasMonitoredManga.value = true
            notificationsAvailable.value = true
        }
        composeRule.onAllNodesWithText(warning).assertCountEquals(0)
    }
}
