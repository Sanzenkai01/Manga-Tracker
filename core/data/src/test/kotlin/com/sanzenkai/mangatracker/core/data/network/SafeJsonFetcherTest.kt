package com.sanzenkai.mangatracker.core.data.network

import java.net.InetAddress
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SafeJsonFetcherTest {
    private val server = MockWebServer()

    @Before
    fun startServer() {
        server.start()
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    @Test
    fun fetchesJsonFromAnExplicitlyAllowedHost() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody("""{"sucesso":true}"""),
        )

        val result = fetcher().fetch(server.url("/api/series").toString())

        assertTrue(result is JsonFetchResult.Success)
        assertEquals("""{"sucesso":true}""", (result as JsonFetchResult.Success).json)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun refusesHostsOutsideTheAllowlistBeforeMakingARequest() = runBlocking {
        val result = fetcher(allowedHosts = setOf("api.example.test"))
            .fetch(server.url("/api/series").toString())

        assertEquals(
            HtmlFetchFailureCode.BLOCKED_HOST,
            (result as JsonFetchResult.Failure).code,
        )
        assertEquals(0, server.requestCount)
    }

    @Test
    fun sendsConfiguredHeadersOnlyToTheirAllowedHost() = runBlocking {
        val imperioHost = "api.imperiodabritannia.net"
        val roliaHost = "roliascan.com"
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"sucesso":true}"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"sucesso":true}"""),
        )
        val fetcher = SafeJsonFetcher(
            allowedHosts = setOf(imperioHost, roliaHost),
            requestHeadersByHost = mapOf(
                imperioHost.uppercase() to mapOf("X-NoEncryptionBritta" to "1"),
            ),
            client = OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build(),
            hostResolver = PublicHostResolver {
                listOf(InetAddress.getByName("127.0.0.1"))
            },
            isAllowedEndpoint = { url -> url.port == server.port },
        )
        val imperioUrl = server.url("/api/obras/by-slug/series")
            .newBuilder()
            .host(imperioHost)
            .build()
        val roliaUrl = server.url("/api/manga-chapters")
            .newBuilder()
            .host(roliaHost)
            .build()

        assertTrue(fetcher.fetch(imperioUrl.toString()) is JsonFetchResult.Success)
        assertEquals(
            "1",
            server.takeRequest().getHeader("X-NoEncryptionBritta"),
        )
        assertTrue(fetcher.fetch(roliaUrl.toString()) is JsonFetchResult.Success)
        assertNull(server.takeRequest().getHeader("X-NoEncryptionBritta"))
    }

    @Test
    fun rejectsRedirectsNonJsonResponsesAndOversizedJson() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .addHeader("Location", "/api/final"),
        )
        val redirect = fetcher().fetch(server.url("/api/start").toString())
        assertEquals(
            HtmlFetchFailureCode.REDIRECT_BLOCKED,
            (redirect as JsonFetchResult.Failure).code,
        )
        assertEquals(1, server.requestCount)

        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody("<html/>"),
        )
        val nonJson = fetcher().fetch(server.url("/api/html").toString())
        assertEquals(
            HtmlFetchFailureCode.NOT_JSON,
            (nonJson as JsonFetchResult.Failure).code,
        )

        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"payload":"too large"}"""),
        )
        val oversized = fetcher(maxBytes = 8).fetch(server.url("/api/large").toString())
        assertEquals(
            HtmlFetchFailureCode.RESPONSE_TOO_LARGE,
            (oversized as JsonFetchResult.Failure).code,
        )
    }

    private fun fetcher(
        allowedHosts: Set<String> = setOf(server.hostName),
        maxBytes: Long = 1_024,
    ) = SafeJsonFetcher(
        allowedHosts = allowedHosts,
        client = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build(),
        hostResolver = PublicHostResolver { listOf(InetAddress.getByName("127.0.0.1")) },
        isAllowedEndpoint = { url -> url.host == server.hostName && url.port == server.port },
        maxResponseBytes = maxBytes,
    )
}
