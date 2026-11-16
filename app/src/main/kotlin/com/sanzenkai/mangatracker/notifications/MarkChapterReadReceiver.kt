package com.sanzenkai.mangatracker.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.util.Log
import com.sanzenkai.mangatracker.MangaTrackerApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MarkChapterReadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val chapterId = intent.getStringExtra(EXTRA_CHAPTER_ID)
            ?.takeIf { it.isNotBlank() }
        if (intent.action != ACTION_MARK_READ || chapterId == null) {
            Log.w(TAG, "Ignored a chapter notification action with invalid input.")
            return
        }
        val application = context.applicationContext as? MangaTrackerApplication
        if (application == null) {
            Log.e(TAG, "The Manga Tracker application container is unavailable.")
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val changed = application.container.chapterNotificationActionHandler
                    .markChapterAsRead(chapterId)
                Log.i(TAG, if (changed) "Chapter marked as read from notification."
                else "Chapter was already read; notification dismissed.")
            } catch (error: SQLiteException) {
                Log.e(TAG, "Could not save the notification read action.", error)
            } catch (error: IllegalStateException) {
                Log.e(TAG, "The notification referred to an unavailable chapter.", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_MARK_READ = "com.sanzenkai.mangatracker.action.MARK_CHAPTER_READ"
        const val EXTRA_CHAPTER_ID = "chapter_id"
        private const val TAG = "ChapterReadAction"
    }
}
