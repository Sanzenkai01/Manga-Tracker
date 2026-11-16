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
        val chapterIds = if (intent.hasExtra(EXTRA_CHAPTER_IDS)) {
            intent.getStringArrayListExtra(EXTRA_CHAPTER_IDS)
                ?.takeIf { ids ->
                    ids.isNotEmpty() &&
                        ids.all(String::isNotBlank) &&
                        ids.distinct().size == ids.size
                }
        } else {
            intent.getStringExtra(EXTRA_CHAPTER_ID)
                ?.takeIf { it.isNotBlank() }
                ?.let(::listOf)
        }
        if (intent.action != ACTION_MARK_READ || chapterIds == null) {
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
                    .markChaptersAsRead(chapterIds)
                Log.i(TAG, if (changed) "Chapters marked as read from notification."
                else "Chapters were already read; notifications dismissed.")
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
        const val EXTRA_CHAPTER_IDS = "chapter_ids"
        private const val TAG = "ChapterReadAction"
    }
}
