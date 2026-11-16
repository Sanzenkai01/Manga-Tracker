package com.sanzenkai.mangatracker.core.data.network

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface CoverImageFetchResult {
    data class Success(
        val bytes: ByteArray,
        val contentType: String,
    ) : CoverImageFetchResult

    data class Failure(val reason: String) : CoverImageFetchResult
}

class SafeCoverImageFetcher(
    private val hostResolver: PublicHostResolver = DnsPublicHostResolver(),
    private val isAllowedPort: (Int) -> Boolean = { it == 80 || it == 443 },
    private val maxResponseBytes: Long = DEFAULT_MAX_RESPONSE_BYTES,
    private val maxRedirects: Int = DEFAULT_MAX_REDIRECTS,
) {
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    init {
        require(maxResponseBytes > 0) { "Maximum image response size must be positive." }
        require(maxRedirects >= 0) { "Maximum redirects cannot be negative." }
    }

    suspend fun fetch(rawUrl: String): CoverImageFetchResult =
        withContext(Dispatchers.IO) {
            fetchBlocking(rawUrl)
        }

    private fun fetchBlocking(rawUrl: String): CoverImageFetchResult {
        var currentUrl = rawUrl.trim().toHttpUrlOrNull()
            ?: return failure("The cover URL is invalid.")
        var addresses = resolvePublicHost(currentUrl)
            ?: return failure("The cover host is not a public, resolvable host.")
        val visitedUrls = mutableSetOf<String>()

        for (redirectCount in 0..maxRedirects) {
            if (!visitedUrls.add(currentUrl.toString())) {
                return failure("The cover redirected in a loop.")
            }
            val request = Request.Builder().url(currentUrl).get().build()
            val requestClient = client.newBuilder()
                .dns(
                    object : Dns {
                        override fun lookup(hostname: String): List<InetAddress> {
                            if (hostname.equals(currentUrl.host, ignoreCase = true)) return addresses
                            throw UnknownHostException("The cover request attempted an unvalidated host.")
                        }
                    },
                )
                .build()
            val response = try {
                requestClient.newCall(request).execute()
            } catch (error: IOException) {
                return failure(error.message ?: "The cover could not be downloaded.")
            }

            var redirectUrl: HttpUrl? = null
            var redirectAddresses: List<InetAddress>? = null
            val result = response.use { openResponse ->
                when {
                    openResponse.code in REDIRECT_CODES -> {
                        if (redirectCount == maxRedirects) return failure("The cover exceeded the redirect limit.")
                        val destination = openResponse.header("Location")
                            ?.let(currentUrl::resolve)
                            ?: return failure("The cover returned an invalid redirect.")
                        if (!isAllowedRedirect(currentUrl, destination)) {
                            return failure("Cross-host, non-standard-port, or HTTPS-downgrade redirects are blocked.")
                        }
                        redirectAddresses = resolvePublicHost(destination)
                            ?: return failure("The cover redirect host is not public.")
                        redirectUrl = destination
                        null
                    }
                    openResponse.code !in 200..299 ->
                        failure("The cover returned HTTP ${openResponse.code}.")
                    else -> readImage(openResponse)
                }
            }
            if (redirectUrl != null) {
                currentUrl = redirectUrl
                addresses = redirectAddresses
                    ?: return failure("The cover redirect destination could not be validated.")
                continue
            }
            return result ?: failure("The cover response could not be read.")
        }
        return failure("The cover exceeded the redirect limit.")
    }

    private fun readImage(response: okhttp3.Response): CoverImageFetchResult {
        val body = response.body ?: return failure("The cover response was empty.")
        val contentType = body.contentType()?.let { "${it.type}/${it.subtype}" }
            ?: return failure("The cover response did not declare an image content type.")
        if (!contentType.startsWith("image/", ignoreCase = true)) {
            return failure("The cover response was not an image.")
        }
        if (body.contentLength() > maxResponseBytes) {
            return failure("The cover image is too large.")
        }
        return try {
            val output = ByteArrayOutputStream()
            body.byteStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxResponseBytes) return failure("The cover image is too large.")
                    output.write(buffer, 0, read)
                }
            }
            val bytes = output.toByteArray()
            if (bytes.isEmpty()) failure("The cover image was empty.")
            else CoverImageFetchResult.Success(bytes, contentType)
        } catch (error: IOException) {
            failure(error.message ?: "The cover image could not be read.")
        }
    }

    private fun resolvePublicHost(url: HttpUrl): List<InetAddress>? {
        if ((url.scheme != "http" && url.scheme != "https") ||
            url.username.isNotEmpty() ||
            url.password.isNotEmpty() ||
            !isAllowedPort(url.port)
        ) {
            return null
        }
        return hostResolver.resolvePublicAddresses(url.host)
            ?.takeIf { it.isNotEmpty() }
            ?.toList()
    }

    private fun isAllowedRedirect(current: HttpUrl, destination: HttpUrl): Boolean =
        current.host.equals(destination.host, ignoreCase = true) &&
            isAllowedPort(destination.port) &&
            !(current.scheme == "https" && destination.scheme != "https")

    private fun failure(reason: String) = CoverImageFetchResult.Failure(reason)

    private companion object {
        const val DEFAULT_MAX_RESPONSE_BYTES = 2L * 1024 * 1024
        const val DEFAULT_MAX_REDIRECTS = 3
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}
