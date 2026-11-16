package com.sanzenkai.mangatracker.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.os.BundleCompat
import androidx.core.net.toUri
import com.sanzenkai.mangatracker.MainActivity
import com.sanzenkai.mangatracker.R
import com.sanzenkai.mangatracker.core.domain.model.Manga
import com.sanzenkai.mangatracker.core.domain.model.TrackedChapter
import java.math.BigDecimal
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
        platformManager.createNotificationChannel(
            NotificationChannel(
                SITE_PROBLEM_CHANNEL_ID,
                context.getString(R.string.notification_channel_monitoring_problem_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_channel_monitoring_problem_description)
            },
        )
    }

    override suspend fun publishNewChapters(
        manga: Manga,
        chapters: List<TrackedChapter>,
    ): Set<String> {
        if (chapters.isEmpty() || !canPostNotifications()) return emptySet()
        ensureChannel()

        val eligibleChapters = chapters.filter { !it.isRead && it.notificationPostedAtEpochMillis == null }
        val validChapters = eligibleChapters.mapNotNull { chapter ->
            validatedChapterUri(chapter.url)
                ?.let { chapterUri ->
                    NotificationChapter(
                        mangaId = chapter.mangaId,
                        id = chapter.id,
                        label = chapter.label,
                        normalizedNumber = chapter.normalizedNumber,
                        url = chapter.url,
                        detectedAtEpochMillis = chapter.firstDetectedAtEpochMillis,
                        uri = chapterUri,
                    )
                }
                ?: run {
                    Log.w(TAG, "Skipped chapter notification with an invalid chapter link.")
                    null
                }
        }.distinctBy { it.id }
        if (validChapters.isEmpty()) return emptySet()

        val postedIds = mutableSetOf<String>()
        validChapters.groupBy { it.mangaId }.forEach { (mangaId, mangaChapters) ->
            if (mangaId != manga.id) {
                Log.w(TAG, "Skipped chapter notifications whose manga did not match the supplied manga.")
                return@forEach
            }
            val orderedChapters = orderChaptersForDisplay(mangaChapters)
            val cover = manga.originalCoverUrl?.let { coverBitmapLoader.load(it) }
            val notification = buildNotification(
                mangaId = manga.id,
                mangaTitle = manga.title,
                normalizedDomain = manga.normalizedDomain,
                chapters = orderedChapters,
                cover = cover,
            )
            val tag = if (orderedChapters.size > 1) {
                mangaNotificationTag(manga.id)
            } else {
                chapterNotificationTag(orderedChapters.single().id)
            }
            try {
                notificationManager.notify(tag, CHILD_NOTIFICATION_ID, notification)
                postedIds += orderedChapters.map { it.id }
            } catch (error: SecurityException) {
                Log.w(TAG, "Android denied posting new chapter notifications.", error)
            }
        }
        refreshGroupSummary()
        return postedIds
    }

    override fun cancelChapter(chapterId: String) {
        notificationManager.cancel(chapterNotificationTag(chapterId), CHILD_NOTIFICATION_ID)
        try {
            notificationManager.activeNotifications
                .filter { statusBarNotification ->
                    statusBarNotification.tag?.startsWith(MANGA_NOTIFICATION_TAG_PREFIX) == true &&
                        statusBarNotification.notification.extras
                            ?.getStringArrayList(MarkChapterReadReceiver.EXTRA_CHAPTER_IDS)
                            ?.contains(chapterId) == true
                }
                .forEach { statusBarNotification ->
                    val notification = statusBarNotification.notification
                    val extras = notification.extras
                    val storedChapterIds = extras
                        .getStringArrayList(MarkChapterReadReceiver.EXTRA_CHAPTER_IDS)
                        .orEmpty()
                    val storedChapters = notificationChapters(extras)
                    if (storedChapters.size != storedChapterIds.size) {
                        Log.w(TAG, "Could not update a grouped notification with incomplete chapter metadata.")
                        return@forEach
                    }
                    val remainingChapters = storedChapters
                        .filterNot { it.id == chapterId }
                    if (remainingChapters.isEmpty()) {
                        notificationManager.cancel(statusBarNotification.tag, statusBarNotification.id)
                        return@forEach
                    }

                    val mangaId = extras.getString(EXTRA_MANGA_ID)
                    val mangaTitle = extras.getString(EXTRA_MANGA_TITLE)
                    val normalizedDomain = extras.getString(EXTRA_SITE_NAME)
                    if (mangaId == null || mangaTitle == null || normalizedDomain == null) {
                        Log.w(TAG, "Could not update a grouped notification with incomplete metadata.")
                        return@forEach
                    }
                    val cover = BundleCompat.getParcelable(
                        extras,
                        Notification.EXTRA_PICTURE,
                        Bitmap::class.java,
                    )
                    val updated = buildNotification(
                        mangaId = mangaId,
                        mangaTitle = mangaTitle,
                        normalizedDomain = normalizedDomain,
                        chapters = remainingChapters,
                        cover = cover,
                        silentUpdate = true,
                    )
                    val updatedTag = if (remainingChapters.size > 1) {
                        mangaNotificationTag(mangaId)
                    } else {
                        chapterNotificationTag(remainingChapters.single().id)
                    }
                    notificationManager.cancel(statusBarNotification.tag, statusBarNotification.id)
                    notificationManager.notify(updatedTag, CHILD_NOTIFICATION_ID, updated)
                }
        } catch (error: SecurityException) {
            Log.w(TAG, "Android denied updating a grouped chapter notification.", error)
        }
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

    override fun canPostSiteProblemNotifications(): Boolean =
        canPostNotifications(SITE_PROBLEM_CHANNEL_ID)

    override suspend fun publishSiteMonitoringProblem(manga: Manga, episodeId: String): Boolean {
        if (!canPostSiteProblemNotifications()) return false
        ensureChannel()

        val content = context.getString(R.string.notification_monitoring_problem_body)
        val notification = NotificationCompat.Builder(context, SITE_PROBLEM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_manga)
            .setContentTitle(
                context.getString(
                    R.string.notification_monitoring_problem_title,
                    manga.title.notificationSafeText(),
                ),
            )
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(createOpenMangaPendingIntent(manga.id, episodeId))
            .addExtras(
                Bundle().apply {
                    putString(EXTRA_MANGA_ID, manga.id)
                },
            )
            .build()

        return try {
            notificationManager.notify(siteProblemNotificationTag(manga.id), SITE_PROBLEM_NOTIFICATION_ID, notification)
            true
        } catch (error: SecurityException) {
            Log.w(TAG, "Android denied posting the site monitoring problem notification.", error)
            false
        }
    }

    override fun clearSiteMonitoringProblem(mangaId: String) {
        notificationManager.cancel(siteProblemNotificationTag(mangaId), SITE_PROBLEM_NOTIFICATION_ID)
    }

    private fun chapterNotificationTag(chapterId: String) = "$CHAPTER_NOTIFICATION_TAG_PREFIX$chapterId"

    private fun mangaNotificationTag(mangaId: String) = "$MANGA_NOTIFICATION_TAG_PREFIX$mangaId"

    private fun siteProblemNotificationTag(mangaId: String) = "$SITE_PROBLEM_NOTIFICATION_TAG_PREFIX$mangaId"

    private fun createOpenMangaPendingIntent(mangaId: String, episodeId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(
                Uri.Builder()
                    .scheme(INTENT_SCHEME)
                    .authority(SITE_PROBLEM_INTENT_AUTHORITY)
                    .appendPath(mangaId)
                    .appendPath(episodeId)
                    .build(),
            )
            .putExtra(EXTRA_MANGA_ID, mangaId)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            requestCode(mangaId, episodeId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun buildNotification(
        mangaId: String,
        mangaTitle: String,
        normalizedDomain: String,
        chapters: List<NotificationChapter>,
        cover: Bitmap?,
        silentUpdate: Boolean = false,
    ): Notification {
        val grouped = chapters.size > 1
        val latestChapter = latestChapter(chapters)
        val title = mangaTitle.notificationSafeText()
        val siteName = normalizedDomain
            .removePrefix("www.")
            .notificationSafeText()
        val detectedAt = latestChapter.detectedAtEpochMillis
        val formattedTime = Instant.ofEpochMilli(detectedAt)
            .atZone(ZoneId.systemDefault())
            .format(
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                    .withLocale(Locale.getDefault()),
            )
        val content = if (grouped) {
            context.resources.getQuantityString(
                R.plurals.notification_grouped_chapter_count,
                chapters.size,
                chapters.size,
                formatChapterRange(chapters),
            )
        } else {
            context.getString(
                R.string.notification_chapter_content,
                latestChapter.label.notificationSafeText(),
                siteName,
                formattedTime,
            )
        }
        val openIntent = createOpenChapterPendingIntent(latestChapter.id, latestChapter.uri)
        val markReadIntent = createMarkReadPendingIntent(mangaId, chapters)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_manga)
            .setContentTitle(title)
            .setContentText(content)
            .setWhen(detectedAt)
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setOnlyAlertOnce(grouped || silentUpdate)
            .setGroup(GROUP_KEY)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setContentIntent(openIntent)
            .addExtras(
                notificationExtras(mangaId, title, siteName, chapters),
            )
            .addAction(
                R.drawable.ic_notification_manga,
                context.getString(
                    if (grouped) R.string.notification_open_latest_chapter
                    else R.string.notification_open_chapter,
                ),
                openIntent,
            )
            .addAction(
                R.drawable.ic_notification_manga,
                context.getString(
                    if (grouped) R.string.notification_mark_group_read
                    else R.string.notification_mark_read,
                ),
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

    private fun notificationExtras(
        mangaId: String,
        mangaTitle: String,
        siteName: String,
        chapters: List<NotificationChapter>,
    ) = Bundle().apply {
        putString(EXTRA_MANGA_ID, mangaId)
        putString(EXTRA_MANGA_TITLE, mangaTitle)
        putString(EXTRA_SITE_NAME, siteName)
        putStringArrayList(
            MarkChapterReadReceiver.EXTRA_CHAPTER_IDS,
            ArrayList(chapters.map { it.id }),
        )
        putStringArrayList(EXTRA_CHAPTER_LABELS, ArrayList(chapters.map { it.label }))
        putStringArrayList(EXTRA_CHAPTER_NUMBERS, ArrayList(chapters.map { it.normalizedNumber.orEmpty() }))
        putStringArrayList(EXTRA_CHAPTER_URLS, ArrayList(chapters.map { it.url }))
        putLongArray(
            EXTRA_CHAPTER_DETECTED_AT,
            chapters.map { it.detectedAtEpochMillis }.toLongArray(),
        )
    }

    private fun notificationChapters(extras: Bundle): List<NotificationChapter> {
        val ids = extras.getStringArrayList(MarkChapterReadReceiver.EXTRA_CHAPTER_IDS) ?: return emptyList()
        val labels = extras.getStringArrayList(EXTRA_CHAPTER_LABELS) ?: return emptyList()
        val numbers = extras.getStringArrayList(EXTRA_CHAPTER_NUMBERS) ?: return emptyList()
        val urls = extras.getStringArrayList(EXTRA_CHAPTER_URLS) ?: return emptyList()
        val detectedAt = extras.getLongArray(EXTRA_CHAPTER_DETECTED_AT) ?: return emptyList()
        if (ids.size != labels.size ||
            ids.size != numbers.size ||
            ids.size != urls.size ||
            ids.size != detectedAt.size
        ) {
            Log.w(TAG, "Could not update a grouped notification with incomplete chapter metadata.")
            return emptyList()
        }
        return ids.indices.mapNotNull { index ->
            val uri = validatedChapterUri(urls[index]) ?: return@mapNotNull null
            NotificationChapter(
                mangaId = extras.getString(EXTRA_MANGA_ID).orEmpty(),
                id = ids[index],
                label = labels[index],
                normalizedNumber = numbers[index].ifBlank { null },
                url = urls[index],
                detectedAtEpochMillis = detectedAt[index],
                uri = uri,
            )
        }
    }

    private fun orderChaptersForDisplay(chapters: List<NotificationChapter>): List<NotificationChapter> =
        chapters.sortedWith(
            compareBy<NotificationChapter> { it.normalizedNumber?.toBigDecimalOrNull() == null }
                .thenBy { it.normalizedNumber?.toBigDecimalOrNull() }
                .thenBy { it.detectedAtEpochMillis }
                .thenBy { it.id },
        )

    private fun latestChapter(chapters: List<NotificationChapter>): NotificationChapter =
        chapters.maxWithOrNull(
            compareBy<NotificationChapter> { it.normalizedNumber?.toBigDecimalOrNull() }
                .thenBy { it.detectedAtEpochMillis },
        ) ?: chapters.last()

    private fun formatChapterRange(chapters: List<NotificationChapter>): String {
        val numbers = chapters.map { it.normalizedNumber?.toBigDecimalOrNull() }
        if (numbers.all { it != null } && numbers.size > 1) {
            val numericValues = numbers.filterNotNull()
            val consecutive = numericValues.zipWithNext().all { (first, second) ->
                second.subtract(first).compareTo(BigDecimal.ONE) == 0
            }
            if (consecutive) {
                return "${numericValues.first().normalizedChapterNumber()}–${numericValues.last().normalizedChapterNumber()}"
            }
        }
        return chapters.joinToString(", ") { chapter ->
            chapter.normalizedNumber?.toBigDecimalOrNull()
                ?.normalizedChapterNumber()
                ?: chapter.label.notificationSafeText()
        }
    }

    private fun BigDecimal.normalizedChapterNumber(): String =
        stripTrailingZeros().toPlainString()

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

    private fun createMarkReadPendingIntent(
        mangaId: String,
        chapters: List<NotificationChapter>,
    ): PendingIntent {
        val grouped = chapters.size > 1
        val chapterId = chapters.singleOrNull()?.id
        val chapterIds = chapters.map { it.id }
        val intent = Intent(context, MarkChapterReadReceiver::class.java)
            .setAction(MarkChapterReadReceiver.ACTION_MARK_READ)
            .setData(
                Uri.Builder()
                    .scheme(INTENT_SCHEME)
                    .authority(if (grouped) MANGA_INTENT_AUTHORITY else INTENT_AUTHORITY)
                    .appendPath(if (grouped) mangaId else chapterId.orEmpty())
                    .appendPath(MARK_READ_ACTION_SUFFIX)
                    .build(),
            )
            .putStringArrayListExtra(
                MarkChapterReadReceiver.EXTRA_CHAPTER_IDS,
                ArrayList(chapterIds),
            )
        if (!grouped && chapterId != null) {
            intent.putExtra(MarkChapterReadReceiver.EXTRA_CHAPTER_ID, chapterId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(if (grouped) mangaId else chapterId.orEmpty(), MARK_READ_ACTION_SUFFIX),
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

    private fun canPostNotifications(): Boolean = canPostNotifications(CHANNEL_ID)

    private fun canPostNotifications(channelId: String): Boolean {
        if (!notificationManager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val channel = notificationManager.getNotificationChannel(channelId)
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
        const val SITE_PROBLEM_CHANNEL_ID = "manga_site_monitoring_problems"
        const val GROUP_KEY = "com.sanzenkai.mangatracker.NEW_CHAPTERS"
        const val CHILD_NOTIFICATION_ID = 0
        const val GROUP_SUMMARY_ID = 1
        const val GROUP_SUMMARY_TAG = "new-chapter-group-summary"
        const val CHAPTER_NOTIFICATION_TAG_PREFIX = "chapter:"
        const val MANGA_NOTIFICATION_TAG_PREFIX = "manga:"
        const val SITE_PROBLEM_NOTIFICATION_TAG_PREFIX = "site-problem:"
        private const val EXTRA_MANGA_ID = "com.sanzenkai.mangatracker.extra.MANGA_ID"
        private const val EXTRA_MANGA_TITLE = "com.sanzenkai.mangatracker.extra.MANGA_TITLE"
        private const val EXTRA_SITE_NAME = "com.sanzenkai.mangatracker.extra.SITE_NAME"
        private const val EXTRA_CHAPTER_LABELS = "com.sanzenkai.mangatracker.extra.CHAPTER_LABELS"
        private const val EXTRA_CHAPTER_NUMBERS = "com.sanzenkai.mangatracker.extra.CHAPTER_NUMBERS"
        private const val EXTRA_CHAPTER_URLS = "com.sanzenkai.mangatracker.extra.CHAPTER_URLS"
        private const val EXTRA_CHAPTER_DETECTED_AT = "com.sanzenkai.mangatracker.extra.CHAPTER_DETECTED_AT"
        private const val TAG = "ChapterNotifications"
        private const val MAX_NOTIFICATION_TEXT_LENGTH = 200
        private const val MAX_GROUP_LINES = 5
        private const val OPEN_ACTION_SUFFIX = "open"
        private const val MARK_READ_ACTION_SUFFIX = "mark-read"
        private const val INTENT_SCHEME = "manga-tracker"
        private const val INTENT_AUTHORITY = "chapter"
        private const val MANGA_INTENT_AUTHORITY = "manga"
        private const val SITE_PROBLEM_INTENT_AUTHORITY = "monitoring-problem"
        private const val SITE_PROBLEM_NOTIFICATION_ID = 2
    }

    private data class NotificationChapter(
        val mangaId: String,
        val id: String,
        val label: String,
        val normalizedNumber: String?,
        val url: String,
        val detectedAtEpochMillis: Long,
        val uri: Uri,
    )
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

fun hasMonitoringProblemNotificationPermission(context: Context): Boolean {
    if (!hasNotificationPermission(context)) return false
    return NotificationManagerCompat.from(context)
        .getNotificationChannel(ChapterNotificationManager.SITE_PROBLEM_CHANNEL_ID)
        ?.importance != NotificationManager.IMPORTANCE_NONE
}
