package com.sanzenkai.mangatracker.ui.opening

internal fun shouldShowOpeningAnimation(
    isNewActivityInstance: Boolean,
    isLauncherLaunch: Boolean,
    animationsEnabled: Boolean,
): Boolean = isNewActivityInstance && isLauncherLaunch && animationsEnabled
