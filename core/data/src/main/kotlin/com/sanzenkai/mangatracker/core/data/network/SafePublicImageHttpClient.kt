package com.sanzenkai.mangatracker.core.data.network

import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody

class SafePublicImageHttpClient(
    private val hostResolver: PublicHostResolver = DnsPublicHostResolver(),
    private val isAllowedPort: (Int) -> Boolean = { it == 80 || it == 443 },
    private val maxResponseBytes: Long = DEFAULT_MAX_RESPONSE_BYTES,
) {
    init {
        require(maxResponseBytes in 1 until Long.MAX_VALUE) {
            "Maximum image response size must be positive and bounded."
        }
    }

    fun create(): OkHttpClient =
        OkHttpClient.Builder()
            .dns(
                object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> =
                        hostResolver.resolvePublicAddresses(hostname)
                            ?.takeIf { it.isNotEmpty() }
                            ?.toList()
                            ?: throw UnknownHostException("The image host is not public or resolvable.")
                },
            )
            .addInterceptor { chain ->
                val url = chain.request().url
                if ((url.scheme != "http" && url.scheme != "https") ||
                    url.username.isNotEmpty() ||
                    url.password.isNotEmpty() ||
                    !isAllowedPort(url.port)
                ) {
                    throw IOException("The image URL uses a blocked scheme, credential, or port.")
                }
                chain.proceed(chain.request())
            }
            .addNetworkInterceptor { chain ->
                val url = chain.request().url
                if ((url.scheme != "http" && url.scheme != "https") ||
                    url.username.isNotEmpty() ||
                    url.password.isNotEmpty() ||
                    !isAllowedPort(url.port)
                ) {
                    throw IOException("The image URL uses a blocked scheme, credential, or port.")
                }
                val response = chain.proceed(chain.request())
                val body = response.body
                if (body == null) {
                    response.close()
                    throw IOException("The image response has no body.")
                }
                if (body.contentLength() > maxResponseBytes) {
                    response.close()
                    throw IOException("The image response exceeds the configured size limit.")
                }
                val bytes = try {
                    val source = body.source()
                    if (source.request(maxResponseBytes + 1)) {
                        response.close()
                        throw IOException("The image response exceeds the configured size limit.")
                    }
                    source.readByteArray()
                } catch (error: IOException) {
                    response.close()
                    throw error
                }
                response.newBuilder()
                    .body(bytes.toResponseBody(body.contentType()))
                    .build()
            }
            .followSslRedirects(false)
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    private companion object {
        const val DEFAULT_MAX_RESPONSE_BYTES = 2L * 1024 * 1024
        const val CONNECT_TIMEOUT_SECONDS = 5L
        const val READ_TIMEOUT_SECONDS = 5L
        const val CALL_TIMEOUT_SECONDS = 10L
    }
}
