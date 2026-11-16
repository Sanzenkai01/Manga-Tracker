package com.sanzenkai.mangatracker.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.sanzenkai.mangatracker.R
import com.sanzenkai.mangatracker.core.domain.model.Manga
import com.sanzenkai.mangatracker.core.domain.model.TrackedChapter
import java.net.URI
import java.net.URISyntaxException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class ChapterNotificationManager(
    context: Context,
    private val coverBitmapLoader: CoverBitmapLoader = SafeCoverBitmapLoader(),
) : ChapterNotificationPublisher {
    private val context = context.applicationContext
    private val notificationManager = NotificationManagerCompat.from(this.context)
    private val platformManager = this.context.getSystemService(NotificationManager::class.java)

    override fun ensureChannel() {
        platformManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_channel_description)
            },
        )
    }

    override suspend fun publishNewChapters(
        manga: Manga,
        chapters: List<TrackedChapter>,
    ): Set<String> {
        if (chapters.isEmpty() || !canPostNotifications()) return emptySet()
        ensureChannel()

        val postedIds = mutableSetOf<String>()
        val eligibleChapters = chapters.filter { !it.isRead && it.notificationPostedAtEpochMillis == null }
        val validChapters = eligibleChapters.mapNotNull { chapter ->
            validatedChapterUri(chapter.url)
                ?.let { chapter to it }
                ?: run {
                    Log.w(TAG, "Skipped chapter notification with an invalid chapter link.")
                    null
                }
        }
        if (validChapters.isEmpty()) return emptySet()
        val cover = manga.originalCoverUrl?.let { coverBitmapLoader.load(it) }
        validChapters.forEach { (chapter, chapterUri) ->
            val notification = buildNotification(manga, chapter, chapterUri, cover)
            try {
                notificationManager.notify(chapterNotificationTag(chapter.id), CHILD_NOTIFICATION_ID, notification)
                postedIds += chapter.id
            } catch (error: SecurityException) {
                Log.w(TAG, "Android denied posting a chapter notification.", error)
            }
        }
        refreshGroupSummary()
        return postedIds
    }

    override fun cancelChapter(chapterId: String) {
        notificationManager.cancel(chapterNotificationTag(chapterId), CHILD_NOTIFICATION_ID)
        refreshGroupSummary()
    }

    override fun cancelMangaNotifications(mangaIds: Set<String>) {
        if (mangaIds.isEmpty()) return

        try {
            notificationManager.activeNotifications
                .filter { statusBarNotification ->
                    statusBarNotification.notification.extras
                        ?.getString(EXTRA_MANGA_ID)
                        ?.let(mangaIds::contains) == true
                }
                .forEach { statusBarNotification ->
                    notificationManager.cancel(
                        statusBarNotification.tag,
                        statusBarNotification.id,
                    )
                }
        } catch (error: SecurityException) {
            Log.w(TAG, "Android denied clearing notifications for removed manga.", error)
            return
        }
        refreshGroupSummary()
    }

    private fun chapterNotificationTag(chapterId: String) = "$CHAPTER_NOTIFICATION_TAG_PREFIX$chapterId"

    private fun buildNotification(
        manga: Manga,
        chapter: TrackedChapter,
        chapterUri: Uri,
        cover: android.graphics.Bitmap?,
    ): Notification {
        val title = manga.title.notificationSafeText()
        val chapterLabel = chapter.label.notificationSafeText()
        val siteName = manga.normalizedDomain
            .removePrefix("www.")
            .notificationSafeText()
        val detectedAt = chapter.firstDetectedAtEpochMillis
        val formattedTime = Instant.ofEpochMilli(detectedAt)
            .atZone(ZoneId.systemDefault())
            .format(
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                    .withLocale(Locale.getDefault()),
            )
        val content = context.getString(
            R.string.notification_chapter_content,
            chapterLabel,
            siteName,
            formattedTime,
        )
        val openIntent = createOpenChapterPendingIntent(chapter.id, chapterUri)
        val markReadIntent = createMarkReadPendingIntent(chapter.id)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_manga)
            .setContentTitle(title)
            .setContentText(content)
            .setWhen(detectedAt)
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setContentIntent(openIntent)
            .addExtras(
                Bundle().apply {
                    putString(EXTRA_MANGA_ID, manga.id)
                },
            )
            .addAction(
                R.drawable.ic_notification_manga,
                context.getString(R.string.notification_open_chapter),
                openIntent,
            )
            .addAction(
                R.drawable.ic_notification_manga,
                context.getString(R.string.notification_mark_read),
                markReadIntent,
            )

        if (cover != null) {
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(cover)
                    .setSummaryText(content),
            )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(content))
        }
        return builder.build()
    }

    private fun createOpenChapterPendingIntent(
        chapterId: String,
        chapterUri: Uri,
    ): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, chapterUri)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            requestCode(chapterId, OPEN_ACTION_SUFFIX),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createMarkReadPendingIntent(chapterId: String): PendingIntent {
        val intent = Intent(context, MarkChapterReadReceiver::class.java)
            .setAction(MarkChapterReadReceiver.ACTION_MARK_READ)
            .setData(
                Uri.Builder()
                    .scheme(INTENT_SCHEME)
                    .authority(INTENT_AUTHORITY)
                    .appendPath(chapterId)
                    .appendPath(MARK_READ_ACTION_SUFFIX)
                    .build(),
            )
            .putExtra(MarkChapterReadReceiver.EXTRA_CHAPTER_ID, chapterId)
        return PendingIntent.getBroadcast(
            context,
            requestCode(chapterId, MARK_READ_ACTION_SUFFIX),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun refreshGroupSummary() {
        if (!canPostNotifications()) return
        val activeChildren = notificationManager.activeNotifications
            .filter {
                it.notification.group == GROUP_KEY &&
                    (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0
            }
        val mangaCount = activeChildren
            .mapNotNull { it.notification.extras?.getString(EXTRA_MANGA_ID) }
            .distinct()
            .size
        if (mangaCount < 2) {
            notificationManager.cancel(GROUP_SUMMARY_TAG, GROUP_SUMMARY_ID)
            return
        }

        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle(context.getString(R.string.notification_group_title))
            .setSummaryText(
                context.resources.getQuantityString(
                    R.plurals.notification_group_count,
                    mangaCount,
                    mangaCount,
                ),
            )
        activeChildren.take(MAX_GROUP_LINES).forEach { statusBarNotification ->
            val extras = statusBarNotification.notification.extras
            val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            inboxStyle.addLine("$title: $text")
        }
        val summary = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_manga)
            .setContentTitle(context.getString(R.string.notification_group_title))
            .setContentText(
                context.resources.getQuantityString(
                    R.plurals.notification_group_count,
                    mangaCount,
                    mangaCount,
                ),
            )
            .setStyle(inboxStyle)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .build()
        try {
            notificationManager.notify(GROUP_SUMMARY_TAG, GROUP_SUMMARY_ID, summary)
        } catch (error: SecurityException) {
            Log.w(TAG, "Android denied posting the chapter notification group summary.", error)
        }
    }

    private fun canPostNotifications(): Boolean {
        if (!notificationManager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val channel = notificationManager.getNotificationChannel(CHANNEL_ID)
        if (channel?.importance == NotificationManager.IMPORTANCE_NONE) return false
        return true
    }

    private fun validatedChapterUri(rawUrl: String): Uri? =
        try {
            val uri = URI(rawUrl)
            val scheme = uri.scheme?.lowercase()
            if (scheme !in setOf("http", "https") ||
                uri.host.isNullOrBlank() ||
                uri.userInfo != null ||
                (uri.port != -1 && uri.port !in setOf(80, 443))
            ) {
                null
            } else {
                uri.toASCIIString().toUri()
            }
        } catch (_: URISyntaxException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun String.notificationSafeText(): String =
        filterNot { it.isISOControl() }
            .trim()
            .take(MAX_NOTIFICATION_TEXT_LENGTH)
            .ifBlank { context.getString(R.string.notification_unknown_value) }

    private fun requestCode(chapterId: String, action: String): Int =
        "$chapterId:$action".hashCode()

    companion object {
        const val CHANNEL_ID = "manga_new_chapters"
        const val GROUP_KEY = "com.sanzenkai.mangatracker.NEW_CHAPTERS"
        const val CHILD_NOTIFICATION_ID = 0
        const val GROUP_SUMMARY_ID = 1
        const val GROUP_SUMMARY_TAG = "new-chapter-group-summary"
        const val CHAPTER_NOTIFICATION_TAG_PREFIX = "chapter:"
        private const val EXTRA_MANGA_ID = "com.sanzenkai.mangatracker.extra.MANGA_ID"
        private const val TAG = "ChapterNotifications"
        private const val MAX_NOTIFICATION_TEXT_LENGTH = 200
        private const val MAX_GROUP_LINES = 5
        private const val OPEN_ACTION_SUFFIX = "open"
        private const val MARK_READ_ACTION_SUFFIX = "mark-read"
        private const val INTENT_SCHEME = "manga-tracker"
        private const val INTENT_AUTHORITY = "chapter"
    }
}

fun openAppNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun hasNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return false
    }
    val manager = NotificationManagerCompat.from(context)
    if (!manager.areNotificationsEnabled()) return false
    return manager.getNotificationChannel(ChapterNotificationManager.CHANNEL_ID)
        ?.importance != NotificationManager.IMPORTANCE_NONE
}
