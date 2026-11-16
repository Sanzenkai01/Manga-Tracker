package com.sanzenkai.mangatracker.core.data.network

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface JsonFetchResult {
    data class Success(val json: String) : JsonFetchResult

    data class Failure(
        val reason: String,
        val code: HtmlFetchFailureCode,
    ) : JsonFetchResult
}

fun interface JsonDocumentFetcher {
    suspend fun fetch(rawUrl: String): JsonFetchResult
}

class SafeJsonFetcher(
    allowedHosts: Set<String> = setOf("api.imperiodabritannia.net"),
    client: OkHttpClient = defaultClient(),
    private val hostResolver: PublicHostResolver = DnsPublicHostResolver(),
    private val isAllowedEndpoint: (HttpUrl) -> Boolean = {
        it.isHttps && it.port == 443
    },
    private val maxResponseBytes: Long = DEFAULT_MAX_RESPONSE_BYTES,
    requestHeadersByHost: Map<String, Map<String, String>> = emptyMap(),
) : JsonDocumentFetcher {
    private val client = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
    private val allowedHosts = allowedHosts
        .map { it.lowercase(Locale.ROOT) }
        .toSet()
    private val requestHeadersByHost = requestHeadersByHost
        .mapKeys { (host, _) -> host.lowercase(Locale.ROOT) }

    init {
        require(this.allowedHosts.isNotEmpty()) { "At least one JSON API host must be allowed." }
        require(maxResponseBytes > 0) { "Maximum response size must be positive." }
        require(this.requestHeadersByHost.keys.all { it in this.allowedHosts }) {
            "Additional request headers can only be configured for allowed API hosts."
        }
    }

    override suspend fun fetch(rawUrl: String): JsonFetchResult =
        withContext(Dispatchers.IO) {
            fetchBlocking(rawUrl)
        }

    private fun fetchBlocking(rawUrl: String): JsonFetchResult {
        val url = rawUrl.trim().toHttpUrlOrNull()
            ?: return failure(
                "The API address is invalid.",
                HtmlFetchFailureCode.INVALID_URL,
            )
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) {
            return failure(
                "API URLs with embedded credentials are not accepted.",
                HtmlFetchFailureCode.INVALID_URL,
            )
        }
        if (url.host.lowercase(Locale.ROOT) !in allowedHosts || !isAllowedEndpoint(url)) {
            return failure(
                "The API host or connection is not allowed.",
                HtmlFetchFailureCode.BLOCKED_HOST,
            )
        }
        val addresses = hostResolver.resolvePublicAddresses(url.host)
            ?.takeIf { it.isNotEmpty() }
            ?: return failure(
                "The API host is private or could not be resolved.",
                HtmlFetchFailureCode.BLOCKED_HOST,
            )
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .get()
        requestHeadersByHost[url.host.lowercase(Locale.ROOT)]
            ?.forEach { (name, value) -> requestBuilder.header(name, value) }
        val request = requestBuilder.build()
        val requestClient = client.newBuilder()
            .dns(
                object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> {
                        if (hostname.equals(url.host, ignoreCase = true)) return addresses
                        throw UnknownHostException("The request attempted to resolve an unvalidated host.")
                    }
                },
            )
            .build()
        val response = try {
            requestClient.newCall(request).execute()
        } catch (_: IOException) {
            return failure(
                "The chapter API could not be reached.",
                HtmlFetchFailureCode.NETWORK_ERROR,
            )
        }

        return response.use { openResponse ->
            when (openResponse.code) {
                in 300..399 -> failure(
                    "The API redirected the request; redirects are not followed.",
                    HtmlFetchFailureCode.REDIRECT_BLOCKED,
                )
                401, 403 -> failure(
                    "The API denied the request.",
                    HtmlFetchFailureCode.ACCESS_BLOCKED,
                )
                404, 410 -> failure(
                    "The requested API resource is unavailable.",
                    HtmlFetchFailureCode.PAGE_UNAVAILABLE,
                )
                429 -> failure(
                    "The API rate-limited the request.",
                    HtmlFetchFailureCode.RATE_LIMITED,
                )
                in 500..599 -> failure(
                    "The API returned HTTP ${openResponse.code}.",
                    HtmlFetchFailureCode.SERVER_ERROR,
                )
                in 200..299 -> readJson(openResponse)
                else -> failure(
                    "The API returned HTTP ${openResponse.code}.",
                    HtmlFetchFailureCode.HTTP_ERROR,
                )
            }
        }
    }

    private fun readJson(response: okhttp3.Response): JsonFetchResult {
        val body = response.body
            ?: return failure("The API returned no content.", HtmlFetchFailureCode.EMPTY_BODY)
        if (body.contentLength() > maxResponseBytes) {
            return failure("The API response is too large.", HtmlFetchFailureCode.RESPONSE_TOO_LARGE)
        }
        val mediaType = body.contentType()
        val mediaTypeName = mediaType?.let { "${it.type}/${it.subtype}".lowercase(Locale.ROOT) }
        if (mediaTypeName != "application/json" && mediaTypeName?.endsWith("+json") != true) {
            return failure("The API response is not JSON.", HtmlFetchFailureCode.NOT_JSON)
        }
        val bytes = try {
            body.byteStream().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxResponseBytes) {
                        return failure(
                            "The API response is too large.",
                            HtmlFetchFailureCode.RESPONSE_TOO_LARGE,
                        )
                    }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
        } catch (_: IOException) {
            return failure("The API response could not be read.", HtmlFetchFailureCode.NETWORK_ERROR)
        }
        if (bytes.isEmpty()) {
            return failure("The API returned an empty response.", HtmlFetchFailureCode.EMPTY_BODY)
        }
        val charset = mediaType?.charset(StandardCharsets.UTF_8) ?: StandardCharsets.UTF_8
        val json = String(bytes, charset)
        return if (json.isBlank()) {
            failure("The API returned an empty response.", HtmlFetchFailureCode.EMPTY_BODY)
        } else {
            JsonFetchResult.Success(json)
        }
    }

    private fun failure(reason: String, code: HtmlFetchFailureCode) =
        JsonFetchResult.Failure(reason, code)

    private companion object {
        const val DEFAULT_MAX_RESPONSE_BYTES = 2L * 1024 * 1024

        fun defaultClient() = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
