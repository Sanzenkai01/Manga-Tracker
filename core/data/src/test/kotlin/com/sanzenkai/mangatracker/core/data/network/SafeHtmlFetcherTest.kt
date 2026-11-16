package com.sanzenkai.mangatracker.core.data.network

import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SafeHtmlFetcherTest {
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
    fun fetchesHtmlAndRecordsTheFinalAndRequestedUrls() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody("<html><title>Sample</title></html>"),
        )

        val result = fetcher().fetch(server.url("/chapter/1").toString())

        assertTrue(result is HtmlFetchResult.Success)
        val document = (result as HtmlFetchResult.Success).document
        assertEquals(server.url("/chapter/1").scheme, document.pageUrl.substringBefore(':'))
        assertTrue(document.pageUrl.endsWith("/chapter/1"))
        assertTrue(document.requestedUrl.endsWith("/chapter/1"))
        assertTrue(document.html.contains("Sample"))
    }

    @Test
    fun followsSameHostRedirectsButRejectsCrossHostRedirects() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .addHeader("Location", "/chapter/final"),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody("<html><title>Final</title></html>"),
        )
        val sameHostResult = fetcher().fetch(server.url("/start").toString())

        assertTrue(sameHostResult is HtmlFetchResult.Success)
        assertTrue((sameHostResult as HtmlFetchResult.Success).document.pageUrl.endsWith("/chapter/final"))
        assertEquals(2, server.requestCount)

        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .addHeader("Location", "https://blocked.example/chapter"),
        )
        val crossHostResult = fetcher().fetch(server.url("/cross-host").toString())

        assertTrue(crossHostResult is HtmlFetchResult.Failure)
        assertEquals(
            HtmlFetchFailureCode.REDIRECT_BLOCKED,
            (crossHostResult as HtmlFetchResult.Failure).code,
        )
        assertEquals(3, server.requestCount)
    }

    @Test
    fun surfacesAccessFailuresAndOversizedResponses() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("denied"))
        val denied = fetcher().fetch(server.url("/blocked").toString())
        assertEquals(
            HtmlFetchFailureCode.ACCESS_BLOCKED,
            (denied as HtmlFetchResult.Failure).code,
        )

        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody("<html>this body is too large</html>"),
        )
        val oversized = fetcher(maxBytes = 16).fetch(server.url("/large").toString())
        assertEquals(
            HtmlFetchFailureCode.RESPONSE_TOO_LARGE,
            (oversized as HtmlFetchResult.Failure).code,
        )
    }

    @Test
    fun distinguishesRateLimitsAndServerErrorsForRetryPolicy() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("slow down"))
        val rateLimited = fetcher().fetch(server.url("/rate-limited").toString())
        assertEquals(
            HtmlFetchFailureCode.RATE_LIMITED,
            (rateLimited as HtmlFetchResult.Failure).code,
        )

        server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))
        val serverError = fetcher().fetch(server.url("/server-error").toString())
        assertEquals(
            HtmlFetchFailureCode.SERVER_ERROR,
            (serverError as HtmlFetchResult.Failure).code,
        )
    }

    @Test
    fun refusesHostsThatFailPublicAddressValidationBeforeMakingARequest() = runBlocking {
        val fetcher = SafeHtmlFetcher(
            client = OkHttpClient(),
            hostResolver = PublicHostResolver { null },
            isAllowedPort = { true },
        )

        val result = fetcher.fetch(server.url("/private").toString())

        assertEquals(
            HtmlFetchFailureCode.BLOCKED_HOST,
            (result as HtmlFetchResult.Failure).code,
        )
        assertEquals(0, server.requestCount)
    }

    @Test
    fun publicHostResolverRejectsLoopbackAddresses() {
        assertEquals(null, DnsPublicHostResolver().resolvePublicAddresses("127.0.0.1"))
        assertEquals(null, DnsPublicHostResolver().resolvePublicAddresses("localhost"))
    }

    @Test
    fun publicHostResolverRejectsSpecialIpv6AndAllowsOther192Addresses() {
        val resolver = DnsPublicHostResolver()

        assertEquals(null, resolver.resolvePublicAddresses("64:ff9b::7f00:1"))
        assertEquals(null, resolver.resolvePublicAddresses("2002:7f00:1::"))
        assertEquals(null, resolver.resolvePublicAddresses("192.0.2.1"))
        assertTrue(resolver.resolvePublicAddresses("192.0.1.1")?.isNotEmpty() == true)
    }

    private fun fetcher(maxBytes: Long = 1024) = SafeHtmlFetcher(
        client = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build(),
        hostResolver = PublicHostResolver { host -> InetAddress.getAllByName(host).toList() },
        isAllowedPort = { true },
        maxResponseBytes = maxBytes,
    )
}
