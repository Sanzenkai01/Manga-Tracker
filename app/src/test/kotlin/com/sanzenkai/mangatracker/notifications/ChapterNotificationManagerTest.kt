package com.sanzenkai.mangatracker.notifications

import android.app.Application
import android.Manifest
import android.app.Notification
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.BundleCompat
import com.sanzenkai.mangatracker.core.domain.model.Manga
import com.sanzenkai.mangatracker.core.domain.model.ReadingAction
import com.sanzenkai.mangatracker.core.domain.model.ReadingActionOrigin
import com.sanzenkai.mangatracker.core.domain.model.TrackedChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ChapterNotificationManagerTest {
    private val context = RuntimeEnvironment.getApplication()
    private val platformManager = NotificationManagerCompat.from(context)

    @Before
    fun setUp() {
        platformManager.cancelAll()
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun notificationHasOriginalCoverAndIndependentImmutablePendingIntents() = runBlocking {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val publisher = ChapterNotificationManager(
            context,
            CoverBitmapLoader { bitmap },
        )
        publisher.ensureChannel()

        val posted = publisher.publishNewChapters(testManga("Manga One"), listOf(testChapter("chapter-1")))

        assertEquals(setOf("chapter-1"), posted)
        val notification = platformManager.activeNotifications
            .single { it.tag == "${ChapterNotificationManager.CHAPTER_NOTIFICATION_TAG_PREFIX}chapter-1" }
            .notification
        assertEquals("Manga One", notification.extras.getCharSequence(Notification.EXTRA_TITLE))
        assertNotNull(
            BundleCompat.getParcelable(
                notification.extras,
                Notification.EXTRA_PICTURE,
                Bitmap::class.java,
            ),
        )
        assertEquals(2, notification.actions.size)

        val openPendingIntent = notification.actions[0].actionIntent
        val openIntent = Shadows.shadowOf(openPendingIntent).savedIntent
        assertEquals(Intent.ACTION_VIEW, openIntent.action)
        assertEquals("https://example.test/manga/chapter-1", openIntent.dataString)
        assertNull(openIntent.getStringExtra(MarkChapterReadReceiver.EXTRA_CHAPTER_ID))
        assertTrue(openPendingIntent.isImmutable)

        val markReadPendingIntent = notification.actions[1].actionIntent
        val markReadIntent = Shadows.shadowOf(markReadPendingIntent).savedIntent
        assertEquals(MarkChapterReadReceiver::class.java.name, markReadIntent.component?.className)
        assertEquals(MarkChapterReadReceiver.ACTION_MARK_READ, markReadIntent.action)
        assertEquals("chapter-1", markReadIntent.getStringExtra(MarkChapterReadReceiver.EXTRA_CHAPTER_ID))
        assertTrue(markReadPendingIntent.isImmutable)
        assertTrue(openPendingIntent !== markReadPendingIntent)
    }

    @Test
    fun groupsNotificationsWhenDifferentMangaReceiveUpdates() = runBlocking {
        val publisher = ChapterNotificationManager(context, CoverBitmapLoader { null })
        publisher.ensureChannel()

        publisher.publishNewChapters(testManga("Manga One"), listOf(testChapter("chapter-1")))
        publisher.publishNewChapters(testManga("Manga Two"), listOf(testChapter("chapter-2")))

        val activeNotifications = platformManager.activeNotifications
        val childNotifications = activeNotifications.filter {
            it.notification.group == ChapterNotificationManager.GROUP_KEY &&
                (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0
        }
        val groupSummary = activeNotifications.singleOrNull {
            (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        }

        assertEquals(2, childNotifications.size)
        assertTrue(childNotifications.all { it.notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT) != null })
        assertNotNull(groupSummary)
        assertEquals(
            2,
            groupSummary?.notification?.extras?.getCharSequence(Notification.EXTRA_TEXT)
                ?.toString()
                ?.substringBefore(' ')
                ?.toInt(),
        )
    }

    @Test
    fun cancelMangaNotificationsRemovesItsChildrenAndRefreshesTheGroup() = runBlocking {
        val publisher = ChapterNotificationManager(context, CoverBitmapLoader { null })
        publisher.ensureChannel()
        publisher.publishNewChapters(testManga("Manga One"), listOf(testChapter("chapter-1")))
        publisher.publishNewChapters(testManga("Manga Two"), listOf(testChapter("chapter-2")))

        publisher.cancelMangaNotifications(setOf("manga-one"))

        val remainingChildren = platformManager.activeNotifications.filter {
            it.notification.group == ChapterNotificationManager.GROUP_KEY &&
                (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0
        }
        assertEquals(1, remainingChildren.size)
        assertEquals("manga-two", remainingChildren.single().notification.extras.getString(
            "com.sanzenkai.mangatracker.extra.MANGA_ID",
        ))
        assertTrue(
            platformManager.activeNotifications.none {
                (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
            },
        )
    }

    @Test
    fun missingAndroid13NotificationPermissionKeepsNotificationUnposted() = runBlocking {
        Shadows.shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val publisher = ChapterNotificationManager(context, CoverBitmapLoader { null })

        val posted = publisher.publishNewChapters(testManga("Manga One"), listOf(testChapter("chapter-1")))

        assertTrue(posted.isEmpty())
        assertTrue(platformManager.activeNotifications.isEmpty())
    }

    @Test
    @Config(sdk = [26], qualifiers = "pt-rBR")
    fun createsStableNotificationChannelOnAndroidOreo() {
        val publisher = ChapterNotificationManager(context, CoverBitmapLoader { null })

        publisher.ensureChannel()

        val channel = platformManager.getNotificationChannel(ChapterNotificationManager.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals("Novos capítulos de mangá", channel?.name)
    }

    @Test
    fun markReadActionUsesExplicitReadTransitionAndDismissesOnlyThatChapter() = runBlocking {
        var savedAction: ReadingAction? = null
        var savedOrigin: ReadingActionOrigin? = null
        var savedAt: Long? = null
        var cancelledChapterId: String? = null
        val publisher = object : ChapterNotificationPublisher {
            override suspend fun publishNewChapters(
                manga: Manga,
                chapters: List<TrackedChapter>,
            ) = emptySet<String>()

            override fun ensureChannel() = Unit

            override fun cancelChapter(chapterId: String) {
                cancelledChapterId = chapterId
            }

            override fun cancelMangaNotifications(mangaIds: Set<String>) = Unit
        }
        val handler = ChapterNotificationActionHandler(
            updateChapterReadState = { _, action, origin, occurredAt ->
                savedAction = action
                savedOrigin = origin
                savedAt = occurredAt
                true
            },
            notificationPublisher = publisher,
            clock = { 123L },
        )

        assertTrue(handler.markChapterAsRead("chapter-1"))
        assertEquals(ReadingAction.READ, savedAction)
        assertEquals(ReadingActionOrigin.NOTIFICATION_ACTION, savedOrigin)
        assertEquals(123L, savedAt)
        assertEquals("chapter-1", cancelledChapterId)
    }

    private fun testManga(title: String) = Manga(
        id = title.lowercase().replace(" ", "-"),
        title = title,
        originalCoverUrl = "https://example.test/cover.png",
        seriesUrl = "https://example.test/manga",
        entryChapterUrl = "https://example.test/manga/chapter-1",
        normalizedDomain = "example.test",
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
    )

    private fun testChapter(id: String) = TrackedChapter(
        id = id,
        mangaId = "manga",
        sourceChapterKey = id,
        label = "Chapter 1",
        chapterKind = "NUMBERED",
        normalizedNumber = "1",
        url = Uri.parse("https://example.test/manga/$id").toString(),
        publishedAtEpochMillis = null,
        firstDetectedAtEpochMillis = 1_000L,
        notificationPostedAtEpochMillis = null,
        isRead = false,
    )
}
