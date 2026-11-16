package com.sanzenkai.mangatracker.notifications

import android.content.Context
import android.content.SharedPreferences

internal class NotificationPermissionPromptStore(context: Context) {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun hasPrompted(): Boolean =
        preferences.getBoolean(PROMPT_SHOWN_KEY, false)

    fun markPrompted() {
        preferences.edit().putBoolean(PROMPT_SHOWN_KEY, true).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "notification_permission_prompt"
        const val PROMPT_SHOWN_KEY = "contextual_prompt_shown"
    }
}
