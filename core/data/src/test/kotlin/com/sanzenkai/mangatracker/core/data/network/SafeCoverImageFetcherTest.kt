package com.sanzenkai.mangatracker.core.data.network

import java.net.InetAddress
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeCoverImageFetcherTest {
    @Test
    fun downloadsOnlyBoundedImageResponsesAndRejectsHtml() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "image/png")
                    .setBody("image-bytes"),
            )
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "text/html")
                    .setBody("<html>not an image</html>"),
            )
            val fetcher = fetcher(server)
            val url = server.url("/cover.png").toString()

            val image = fetcher.fetch(url)
            val html = fetcher.fetch(url)

            assertTrue(image is CoverImageFetchResult.Success)
            assertArrayEquals(
                "image-bytes".toByteArray(),
                (image as CoverImageFetchResult.Success).bytes,
            )
            assertTrue(html is CoverImageFetchResult.Failure)
        }
    }

    @Test
    fun rejectsOversizedResponsesAndHostsThatCannotBeResolvedPublicly() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "image/png")
                    .setBody("too many bytes"),
            )
            val url = server.url("/large.png").toString()
            val limited = fetcher(server, maxResponseBytes = 4)
            val unresolved = SafeCoverImageFetcher(
                hostResolver = PublicHostResolver { null },
                isAllowedPort = { true },
            )

            assertTrue(limited.fetch(url) is CoverImageFetchResult.Failure)
            assertTrue(unresolved.fetch(url) is CoverImageFetchResult.Failure)
            assertTrue(server.takeRequest().path == "/large.png")
        }
    }

    private fun fetcher(
        server: MockWebServer,
        maxResponseBytes: Long = 1_024,
    ) = SafeCoverImageFetcher(
        hostResolver = PublicHostResolver {
            listOf(InetAddress.getByName(server.hostName))
        },
        isAllowedPort = { true },
        maxResponseBytes = maxResponseBytes,
    )
}
