package com.sanzenkai.mangatracker.ui.opening

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpeningAnimationDecisionTest {
    @Test
    fun showsOnlyForANewActivityInstance() {
        assertTrue(
            shouldShowOpeningAnimation(
                isNewActivityInstance = true,
                isLauncherLaunch = true,
                animationsEnabled = true,
            ),
        )
        assertFalse(
            shouldShowOpeningAnimation(
                isNewActivityInstance = false,
                isLauncherLaunch = true,
                animationsEnabled = true,
            ),
        )
    }

    @Test
    fun showsOnlyForALauncherIntentNotANotificationIntent() {
        assertTrue(
            shouldShowOpeningAnimation(
                isNewActivityInstance = true,
                isLauncherLaunch = true,
                animationsEnabled = true,
            ),
        )
        assertFalse(
            shouldShowOpeningAnimation(
                isNewActivityInstance = true,
                isLauncherLaunch = false,
                animationsEnabled = true,
            ),
        )
    }

    @Test
    fun doesNotShowWhenSystemAnimationsAreDisabled() {
        assertFalse(
            shouldShowOpeningAnimation(
                isNewActivityInstance = true,
                isLauncherLaunch = true,
                animationsEnabled = false,
            ),
        )
    }
}
