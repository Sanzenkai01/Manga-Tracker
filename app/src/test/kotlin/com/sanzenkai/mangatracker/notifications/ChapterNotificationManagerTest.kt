package com.sanzenkai.mangatracker.notifications

import android.app.Application
import android.Manifest
import android.app.Notification
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.BundleCompat
import com.sanzenkai.mangatracker.MainActivity
import com.sanzenkai.mangatracker.R
import com.sanzenkai.mangatracker.core.domain.model.Manga
import com.sanzenkai.mangatracker.core.domain.model.ReadingAction
import com.sanzenkai.mangatracker.core.domain.model.ReadingActionOrigin
import com.sanzenkai.mangatracker.core.domain.model.TrackedChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        val manga = testManga("Manga One")
        val posted = publisher.publishNewChapters(manga, listOf(testChapter("chapter-1", manga.id)))

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

        val mangaOne = testManga("Manga One")
        val mangaTwo = testManga("Manga Two")
        publisher.publishNewChapters(
            mangaOne,
            (4..6).map { number ->
                testChapter("chapter-one-$number", mangaOne.id).copy(
                    label = "Chapter $number",
                    normalizedNumber = number.toString(),
                )
            },
        )
        publisher.publishNewChapters(mangaTwo, listOf(testChapter("chapter-2", mangaTwo.id)))

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
    fun threeNewChaptersForOneMangaUseOneNotificationAndReturnAllIds() = runBlocking {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val publisher = ChapterNotificationManager(context, CoverBitmapLoader { bitmap })
        val manga = testManga("Manga One")
        publisher.ensureChannel()
        val chapters = (4..6).map { number ->
            testChapter("chapter-$number", manga.id).copy(
                label = "Chapter $number",
                normalizedNumber = number.toString(),
            )
        }

        val posted = publisher.publishNewChapters(manga, chapters)

        assertEquals(chapters.map { it.id }.toSet(), posted)
        val children = platformManager.activeNotifications.filter {
            it.notification.group == ChapterNotificationManager.GROUP_KEY &&
                (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0
        }
        assertEquals(1, children.size)
        val grouped = children.single()
        assertEquals(
            "${ChapterNotificationManager.MANGA_NOTIFICATION_TAG_PREFIX}${manga.id}",
            grouped.tag,
        )
        assertEquals("Manga One", grouped.notification.extras.getCharSequence(Notification.EXTRA_TITLE))
        assertEquals(
            "3 new chapters: 4–6",
            grouped.notification.extras.getCharSequence(Notification.EXTRA_TEXT),
        )
        assertEquals(
            chapters.map { it.id },
            grouped.notification.extras.getStringArrayList(MarkChapterReadReceiver.EXTRA_CHAPTER_IDS),
        )
        assertNotNull(
            BundleCompat.getParcelable(
                grouped.notification.extras,
                Notification.EXTRA_PICTURE,
                Bitmap::class.java,
            ),
        )
        assertEquals(2, grouped.notification.actions.size)

        val openLatestIntent = Shadows.shadowOf(grouped.notification.actions[0].actionIntent).savedIntent
        assertEquals(Intent.ACTION_VIEW, openLatestIntent.action)
        assertEquals("https://example.test/manga/chapter-6", openLatestIntent.dataString)
        assertNull(openLatestIntent.getStringExtra(MarkChapterReadReceiver.EXTRA_CHAPTER_ID))
        assertNull(openLatestIntent.getStringArrayListExtra(MarkChapterReadReceiver.EXTRA_CHAPTER_IDS))
        assertTrue(grouped.notification.actions[0].actionIntent.isImmutable)
        val markReadPendingIntent = grouped.notification.actions[1].actionIntent
        val markReadIntent = Shadows.shadowOf(markReadPendingIntent).savedIntent
        assertEquals(
            chapters.map { it.id },
            markReadIntent.getStringArrayListExtra(MarkChapterReadReceiver.EXTRA_CHAPTER_IDS),
        )
        assertTrue(markReadPendingIntent.isImmutable)
        assertNull(markReadIntent.getStringExtra(MarkChapterReadReceiver.EXTRA_CHAPTER_ID))
    }

    @Test
    @Config(qualifiers = "pt-rBR")
    fun groupedNotificationUsesPortuguesePluralText() = runBlocking {
        val publisher = ChapterNotificationManager(context, CoverBitmapLoader { null })
        val manga = testManga("Manga One")
        publisher.ensureChannel()

        publisher.publishNewChapters(
            manga,
            listOf(
                testChapter("chapter-4", manga.id).copy(label = "Capítulo 4", normalizedNumber = "4"),
                testChapter("chapter-5", manga.id).copy(label = "Capítulo 5", normalizedNumber = "5"),
                testChapter("chapter-6", manga.id).copy(label = "Capítulo 6", normalizedNumber = "6"),
            ),
        )

        val grouped = platformManager.activeNotifications.single {
            it.tag == "${ChapterNotificationManager.MANGA_NOTIFICATION_TAG_PREFIX}${manga.id}"
        }
        assertEquals(
            "3 novos capítulos: 4–6",
            grouped.notification.extras.getCharSequence(Notification.EXTRA_TEXT),
        )
    }

    @Test
    fun cancelChapterUpdatesGroupedNotificationAndCancelsItWhenEmpty() = runBlocking {
        val publisher = ChapterNotificationManager(context, CoverBitmapLoader { null })
        val manga = testManga("Manga One")
        publisher.ensureChannel()
        publisher.publishNewChapters(
            manga,
            (4..6).map { number ->
                testChapter("chapter-$number", manga.id).copy(
                    label = "Chapter $number",
                    normalizedNumber = number.toString(),
                )
            },
        )

        publisher.cancelChapter("chapter-5")

        val updated = platformManager.activeNotifications.single {
            it.notification.group == ChapterNotificationManager.GROUP_KEY &&
                (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0
        }
        assertEquals(
            listOf("chapter-4", "chapter-6"),
            updated.notification.extras.getStringArrayList(MarkChapterReadReceiver.EXTRA_CHAPTER_IDS),
        )
        assertEquals(
            "2 new chapters: 4, 6",
            updated.notification.extras.getCharSequence(Notification.EXTRA_TEXT),
        )

        publisher.cancelChapter("chapter-4")

        val singleChapter = platformManager.activeNotifications.single {
            it.tag == "${ChapterNotificationManager.CHAPTER_NOTIFICATION_TAG_PREFIX}chapter-6"
        }
        assertEquals(
            listOf("chapter-6"),
            singleChapter.notification.extras.getStringArrayList(MarkChapterReadReceiver.EXTRA_CHAPTER_IDS),
        )

        publisher.cancelChapter("chapter-6")

        assertFalse(
            platformManager.activeNotifications.any {
                it.notification.group == ChapterNotificationManager.GROUP_KEY &&
                    (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0
            },
        )
    }

    @Test
    fun cancelMangaNotificationsRemovesItsChildrenAndRefreshesTheGroup() = runBlocking {
        val publisher = ChapterNotificationManager(context, CoverBitmapLoader { null })
        publisher.ensureChannel()
        val mangaOne = testManga("Manga One")
        val mangaTwo = testManga("Manga Two")
        publisher.publishNewChapters(
            mangaOne,
            listOf(
                testChapter("chapter-1", mangaOne.id),
                testChapter("chapter-1-new", mangaOne.id).copy(normalizedNumber = "2"),
            ),
        )
        publisher.publishNewChapters(mangaTwo, listOf(testChapter("chapter-2", mangaTwo.id)))

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
        assertNotNull(platformManager.getNotificationChannel(ChapterNotificationManager.SITE_PROBLEM_CHANNEL_ID))
    }

    @Test
    fun siteMonitoringProblemUsesItsOwnChannelAndCanBeDismissedByManga() = runBlocking {
        val publisher = ChapterNotificationManager(context, CoverBitmapLoader { null })
        publisher.ensureChannel()

        assertTrue(publisher.canPostSiteProblemNotifications())
        assertTrue(publisher.publishSiteMonitoringProblem(testManga("Manga One"), "episode-1"))

        val active = platformManager.activeNotifications.single {
            it.tag == "${ChapterNotificationManager.SITE_PROBLEM_NOTIFICATION_TAG_PREFIX}manga-one"
        }
        val notification = active.notification
        assertEquals(ChapterNotificationManager.SITE_PROBLEM_CHANNEL_ID, notification.channelId)
        assertEquals(
            context.getString(R.string.notification_monitoring_problem_title, "Manga One"),
            notification.extras.getCharSequence(Notification.EXTRA_TITLE),
        )
        assertTrue(notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        val openIntent = Shadows.shadowOf(notification.contentIntent).savedIntent
        assertEquals(MainActivity::class.java.name, openIntent.component?.className)
        assertEquals("manga-one", openIntent.getStringExtra("com.sanzenkai.mangatracker.extra.MANGA_ID"))
        assertTrue(notification.contentIntent.isImmutable)

        publisher.clearSiteMonitoringProblem("manga-one")

        assertTrue(
            platformManager.activeNotifications.none {
                it.tag == "${ChapterNotificationManager.SITE_PROBLEM_NOTIFICATION_TAG_PREFIX}manga-one"
            },
        )
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

    @Test
    fun groupedMarkReadActionUpdatesAndDismissesOnlyItsChapterIds() = runBlocking {
        val savedIds = mutableListOf<String>()
        val cancelledIds = mutableListOf<String>()
        val publisher = object : ChapterNotificationPublisher {
            override suspend fun publishNewChapters(
                manga: Manga,
                chapters: List<TrackedChapter>,
            ) = emptySet<String>()

            override fun ensureChannel() = Unit

            override fun cancelChapter(chapterId: String) {
                cancelledIds += chapterId
            }

            override fun cancelMangaNotifications(mangaIds: Set<String>) = Unit
        }
        val handler = ChapterNotificationActionHandler(
            updateChapterReadState = { chapterId, action, origin, occurredAt ->
                assertEquals(ReadingAction.READ, action)
                assertEquals(ReadingActionOrigin.NOTIFICATION_ACTION, origin)
                assertEquals(123L, occurredAt)
                savedIds += chapterId
                true
            },
            notificationPublisher = publisher,
            clock = { 123L },
        )

        assertTrue(handler.markChaptersAsRead(listOf("chapter-4", "chapter-5", "chapter-6")))
        assertEquals(listOf("chapter-4", "chapter-5", "chapter-6"), savedIds)
        assertEquals(savedIds, cancelledIds)
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

    private fun testChapter(id: String, mangaId: String = "manga") = TrackedChapter(
        id = id,
        mangaId = mangaId,
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
