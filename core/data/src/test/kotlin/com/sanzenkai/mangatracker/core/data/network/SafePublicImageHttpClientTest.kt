package com.sanzenkai.mangatracker.core.data.network

import java.io.IOException
import java.net.InetAddress
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafePublicImageHttpClientTest {
    @Test
    fun downloadsBoundedResponsesAndRejectsOversizedBodies() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "image/png")
                    .setBody("image"),
            )
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "image/png")
                    .setBody("image-is-too-large"),
            )
            val client = imageClient(server, maxResponseBytes = 8)

            val response = client.newCall(Request.Builder().url(server.url("/small")).build()).execute()
            assertEquals("image", response.body?.string())

            val oversized = try {
                client.newCall(Request.Builder().url(server.url("/large")).build()).execute()
                false
            } catch (_: IOException) {
                true
            }

            assertTrue(oversized)
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun refusesUnresolvedHostsAndUnapprovedPortsBeforeSendingRequests() {
        MockWebServer().use { server ->
            val url = server.url("/cover.png")
            val unresolvedClient = SafePublicImageHttpClient(
                hostResolver = PublicHostResolver { null },
                isAllowedPort = { true },
            ).create()
            val blockedPortClient = SafePublicImageHttpClient(
                hostResolver = PublicHostResolver {
                    InetAddress.getAllByName(it).toList()
                },
                isAllowedPort = { false },
            ).create()

            assertRequestFails(unresolvedClient, url.toString())
            assertRequestFails(blockedPortClient, url.toString())
            assertEquals(0, server.requestCount)
        }
    }

    @Test
    fun publicHostRedirectCannotResolveToAnUnapprovedPrivateDestination() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .addHeader("Location", "http://127.0.0.1/secret"),
            )
            val initialHost = server.url("/").host
            val client = SafePublicImageHttpClient(
                hostResolver = PublicHostResolver { host ->
                    if (host.equals(initialHost, ignoreCase = true)) {
                        InetAddress.getAllByName(host).toList()
                    } else {
                        null
                    }
                },
                isAllowedPort = { true },
            ).create()

            assertRequestFails(client, server.url("/redirect").toString())
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun redirectToUnapprovedPortIsRejectedBeforeRequestIsSent() {
        MockWebServer().use { source -> MockWebServer().use { target ->
            source.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .addHeader("Location", target.url("/cover.png")),
            )
            val sourcePort = source.url("/").port
            val client = SafePublicImageHttpClient(
                hostResolver = PublicHostResolver {
                    InetAddress.getAllByName(it).toList()
                },
                isAllowedPort = { it == sourcePort },
            ).create()

            assertRequestFails(client, source.url("/redirect").toString())

            assertEquals(1, source.requestCount)
            assertEquals(0, target.requestCount)
        } }
    }

    private fun imageClient(
        server: MockWebServer,
        maxResponseBytes: Long,
    ) = SafePublicImageHttpClient(
        hostResolver = PublicHostResolver { host ->
            if (host.equals(server.url("/").host, ignoreCase = true)) {
                InetAddress.getAllByName(host).toList()
            } else {
                null
            }
        },
        isAllowedPort = { it == server.url("/").port },
        maxResponseBytes = maxResponseBytes,
    ).create()

    private fun assertRequestFails(client: okhttp3.OkHttpClient, url: String) {
        val failed = try {
            client.newCall(Request.Builder().url(url).build()).execute().use { false }
        } catch (_: IOException) {
            true
        }
        assertTrue(failed)
    }
}
