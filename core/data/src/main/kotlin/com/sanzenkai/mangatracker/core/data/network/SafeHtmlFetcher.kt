package com.sanzenkai.mangatracker.core.data.network

import com.sanzenkai.mangatracker.core.localization.localizedMessage
import com.sanzenkai.mangatracker.core.parser.api.ParserDocument
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface HtmlFetchResult {
    data class Success(val document: ParserDocument) : HtmlFetchResult

    data class Failure(
        val reason: String,
        val code: HtmlFetchFailureCode,
    ) : HtmlFetchResult
}

fun interface HtmlDocumentFetcher {
    suspend fun fetch(rawUrl: String): HtmlFetchResult
}

enum class HtmlFetchFailureCode {
    INVALID_URL,
    BLOCKED_HOST,
    REDIRECT_BLOCKED,
    TOO_MANY_REDIRECTS,
    NETWORK_ERROR,
    ACCESS_BLOCKED,
    RATE_LIMITED,
    PAGE_UNAVAILABLE,
    SERVER_ERROR,
    HTTP_ERROR,
    NOT_HTML,
    NOT_JSON,
    EMPTY_BODY,
    RESPONSE_TOO_LARGE,
}

fun interface PublicHostResolver {
    fun resolvePublicAddresses(host: String): List<InetAddress>?
}

class DnsPublicHostResolver : PublicHostResolver {
    override fun resolvePublicAddresses(host: String): List<InetAddress>? {
        if (host.equals("localhost", ignoreCase = true) ||
            host.endsWith(".localhost", ignoreCase = true) ||
            host.endsWith(".local", ignoreCase = true)
        ) {
            return null
        }
        return try {
            InetAddress.getAllByName(host)
                .toList()
                .takeIf { addresses -> addresses.isNotEmpty() && addresses.all(::isPublicAddress) }
        } catch (_: UnknownHostException) {
            null
        }
    }

    private fun isPublicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) {
            return false
        }
        val bytes = address.address
        if (bytes.size == 4) {
            val first = bytes[0].toInt() and 0xff
            val second = bytes[1].toInt() and 0xff
            val third = bytes[2].toInt() and 0xff
            return first != 0 &&
                first < 224 &&
                !(first == 100 && second in 64..127) &&
                !(first == 192 &&
                    ((second == 0 && (third == 0 || third == 2)) ||
                        (second == 88 && third == 99))) &&
                !(first == 198 && (second == 18 || second == 19 || second == 51 && third == 100)) &&
                !(first == 203 && second == 0 && third == 113) &&
                first < 240
        }
        if (address is Inet6Address) {
            val first = bytes[0].toInt() and 0xff
            val second = bytes[1].toInt() and 0xff
            if (first == 0xfc || first == 0xfd || (first == 0xfe && second in 0x80..0xbf)) {
                return false
            }
            if (first == 0x00 &&
                (bytes[1].toInt() and 0xff) == 0x64 &&
                (bytes[2].toInt() and 0xff) == 0xff &&
                (bytes[3].toInt() and 0xff) == 0x9b
            ) {
                return false
            }
            if (first == 0x20 &&
                ((bytes[1].toInt() and 0xff) == 0x02 ||
                    ((bytes[1].toInt() and 0xff) == 0x01 &&
                        bytes[2].toInt() == 0 &&
                        bytes[3].toInt() == 0))
            ) {
                return false
            }
            if (first == 0x20 &&
                (bytes[1].toInt() and 0xff) == 0x01 &&
                (bytes[2].toInt() and 0xff) == 0x0d &&
                (bytes[3].toInt() and 0xff) == 0xb8
            ) {
                return false
            }
            if (bytes.take(10).all { it.toInt() == 0 } &&
                bytes[10].toInt() == -1 &&
                bytes[11].toInt() == -1
            ) {
                return isPublicAddress(InetAddress.getByAddress(bytes.copyOfRange(12, 16)))
            }
        }
        return true
    }
}

class SafeHtmlFetcher(
    client: OkHttpClient = defaultClient(),
    private val hostResolver: PublicHostResolver = DnsPublicHostResolver(),
    private val isAllowedPort: (Int) -> Boolean = { it == 80 || it == 443 },
    private val maxResponseBytes: Long = DEFAULT_MAX_RESPONSE_BYTES,
    private val maxRedirects: Int = DEFAULT_MAX_REDIRECTS,
) : HtmlDocumentFetcher {
    private val client = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private sealed interface UrlValidation {
        data class Valid(val addresses: List<InetAddress>) : UrlValidation
        data class Rejected(val failure: HtmlFetchResult.Failure) : UrlValidation
    }

    init {
        require(maxResponseBytes > 0) { "Maximum response size must be positive." }
        require(maxRedirects >= 0) { "Maximum redirects cannot be negative." }
    }

    override suspend fun fetch(rawUrl: String): HtmlFetchResult =
        withContext(Dispatchers.IO) {
            fetchBlocking(rawUrl)
        }

    private fun fetchBlocking(rawUrl: String): HtmlFetchResult {
        val initialUrl = try {
            rawUrl.trim().toHttpUrlOrNull()
        } catch (_: IllegalArgumentException) {
            return failure(
                localizedMessage("Enter a valid HTTP or HTTPS URL.", "Informe uma URL HTTP ou HTTPS válida."),
                HtmlFetchFailureCode.INVALID_URL,
            )
        } ?: return failure(
            localizedMessage("Enter a valid HTTP or HTTPS URL.", "Informe uma URL HTTP ou HTTPS válida."),
            HtmlFetchFailureCode.INVALID_URL,
        )
        val initialValidation = validateUrl(initialUrl)
        if (initialValidation is UrlValidation.Rejected) return initialValidation.failure
        val visitedUrls = mutableSetOf<String>()
        var currentUrl = initialUrl
        var currentAddresses = (initialValidation as UrlValidation.Valid).addresses

        for (redirectCount in 0..maxRedirects) {
            if (!visitedUrls.add(currentUrl.toString())) {
                return failure(
                    localizedMessage("The page entered a redirect loop.", "A página entrou em um ciclo de redirecionamento."),
                    HtmlFetchFailureCode.REDIRECT_BLOCKED,
                )
            }
            val request = Request.Builder()
                .url(currentUrl)
                .header("Accept", "text/html,application/xhtml+xml")
                .get()
                .build()
            val requestClient = client.newBuilder()
                .dns(
                    object : Dns {
                        override fun lookup(hostname: String): List<InetAddress> {
                            if (hostname.equals(currentUrl.host, ignoreCase = true)) {
                                return currentAddresses
                            }
                            throw UnknownHostException(
                                localizedMessage(
                                    "The request attempted to resolve an unvalidated host.",
                                    "A solicitação tentou resolver um domínio que não foi validado.",
                                ),
                            )
                        }
                    }
                )
                .build()
            val response = try {
                requestClient.newCall(request).execute()
            } catch (_: IOException) {
                return failure(
                    localizedMessage(
                        "The page could not be reached. Check the URL and connection.",
                        "Não foi possível acessar a página. Verifique a URL e a conexão.",
                    ),
                    HtmlFetchFailureCode.NETWORK_ERROR,
                )
            }

            var redirectUrl: HttpUrl? = null
            var redirectAddresses: List<InetAddress>? = null
            val result = response.use { openResponse ->
                when (openResponse.code) {
                    301, 302, 303, 307, 308 -> {
                        if (redirectCount == maxRedirects) {
                            return failure(
                                localizedMessage(
                                    "The page exceeded the redirect limit.",
                                    "A página excedeu o limite de redirecionamentos.",
                                ),
                                HtmlFetchFailureCode.TOO_MANY_REDIRECTS,
                            )
                        }
                        val location = openResponse.header("Location")
                            ?: return failure(
                                localizedMessage(
                                    "The page redirected without a destination.",
                                    "A página redirecionou sem informar o destino.",
                                ),
                                HtmlFetchFailureCode.REDIRECT_BLOCKED,
                            )
                        val destination = currentUrl.resolve(location)
                            ?: return failure(
                                localizedMessage(
                                    "The redirect destination is invalid.",
                                    "O destino do redirecionamento é inválido.",
                                ),
                                HtmlFetchFailureCode.REDIRECT_BLOCKED,
                            )
                        if (!isAllowedRedirect(currentUrl, destination)) {
                            return failure(
                                localizedMessage(
                                    "Redirects to another host, a non-standard port, or a less secure protocol are not followed.",
                                    "Redirecionamentos para outro domínio, porta não padrão ou protocolo menos seguro não são seguidos.",
                                ),
                                HtmlFetchFailureCode.REDIRECT_BLOCKED,
                            )
                        }
                        when (val validation = validateUrl(destination)) {
                            is UrlValidation.Rejected -> return validation.failure
                            is UrlValidation.Valid -> redirectAddresses = validation.addresses
                        }
                        redirectUrl = destination
                        null
                    }
                    401, 403 -> failure(
                        localizedMessage("The site denied the request.", "O site recusou a solicitação."),
                        HtmlFetchFailureCode.ACCESS_BLOCKED,
                    )
                    429 -> failure(
                        localizedMessage(
                            "The site denied the request or rate-limited access.",
                            "O site recusou a solicitação ou limitou o acesso.",
                        ),
                        HtmlFetchFailureCode.RATE_LIMITED,
                    )
                    404, 410 -> failure(
                        localizedMessage("The requested page is unavailable.", "A página solicitada está indisponível."),
                        HtmlFetchFailureCode.PAGE_UNAVAILABLE,
                    )
                    in 200..299 -> readHtml(openResponse, currentUrl, initialUrl)
                    in 500..599 -> failure(
                        localizedMessage(
                            "The site returned HTTP ${openResponse.code}.",
                            "O site retornou o erro HTTP ${openResponse.code}.",
                        ),
                        HtmlFetchFailureCode.SERVER_ERROR,
                    )
                    else -> failure(
                        localizedMessage(
                            "The site returned HTTP ${openResponse.code}.",
                            "O site retornou o erro HTTP ${openResponse.code}.",
                        ),
                        HtmlFetchFailureCode.HTTP_ERROR,
                    )
                }
            }
            if (redirectUrl != null) {
                currentUrl = redirectUrl
                currentAddresses = redirectAddresses
                    ?: return failure(
                        localizedMessage(
                            "The redirect destination could not be validated.",
                            "Não foi possível validar o destino do redirecionamento.",
                        ),
                        HtmlFetchFailureCode.REDIRECT_BLOCKED,
                    )
                continue
            }
            return result ?: failure(
                localizedMessage("The response could not be read.", "Não foi possível ler a resposta."),
                HtmlFetchFailureCode.NETWORK_ERROR,
            )
        }
        return failure(
            localizedMessage("The page exceeded the redirect limit.", "A página excedeu o limite de redirecionamentos."),
            HtmlFetchFailureCode.TOO_MANY_REDIRECTS,
        )
    }

    private fun readHtml(
        response: okhttp3.Response,
        pageUrl: HttpUrl,
        requestedUrl: HttpUrl,
    ): HtmlFetchResult {
        val body = response.body
            ?: return failure(
                localizedMessage("The page returned no content.", "A página não retornou conteúdo."),
                HtmlFetchFailureCode.EMPTY_BODY,
            )
        if (body.contentLength() > maxResponseBytes) {
            return failure(
                localizedMessage("The HTML response is too large.", "A resposta HTML é muito grande."),
                HtmlFetchFailureCode.RESPONSE_TOO_LARGE,
            )
        }
        val mediaType = body.contentType()
        val mediaTypeName = mediaType?.let {
            "${it.type}/${it.subtype}".lowercase(Locale.ROOT)
        }
        if (mediaTypeName != null &&
            mediaTypeName != "text/html" &&
            mediaTypeName != "application/xhtml+xml"
        ) {
            return failure(
                localizedMessage("The response is not an HTML document.", "A resposta não é um documento HTML."),
                HtmlFetchFailureCode.NOT_HTML,
            )
        }

        val bodyBytes = try {
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
                            localizedMessage("The HTML response is too large.", "A resposta HTML é muito grande."),
                            HtmlFetchFailureCode.RESPONSE_TOO_LARGE,
                        )
                    }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
        } catch (_: IOException) {
            return failure(
                localizedMessage(
                    "The HTML response could not be read.",
                    "Não foi possível ler a resposta HTML.",
                ),
                HtmlFetchFailureCode.NETWORK_ERROR,
            )
        }
        if (bodyBytes.isEmpty()) {
            return failure(
                localizedMessage("The page returned an empty HTML document.", "A página retornou um documento HTML vazio."),
                HtmlFetchFailureCode.EMPTY_BODY,
            )
        }
        if (mediaTypeName == null &&
            !String(bodyBytes, StandardCharsets.UTF_8).trimStart().startsWith("<")
        ) {
            return failure(
                localizedMessage("The response was not identified as HTML.", "A resposta não foi identificada como HTML."),
                HtmlFetchFailureCode.NOT_HTML,
            )
        }
        val charset = mediaType?.charset(StandardCharsets.UTF_8) ?: StandardCharsets.UTF_8
        val html = String(bodyBytes, charset)
        if (html.isBlank()) {
            return failure(
                localizedMessage("The page returned an empty HTML document.", "A página retornou um documento HTML vazio."),
                HtmlFetchFailureCode.EMPTY_BODY,
            )
        }
        return HtmlFetchResult.Success(
            ParserDocument(
                pageUrl = pageUrl.toString(),
                contentType = mediaTypeName,
                html = html,
                requestedUrl = requestedUrl.toString(),
            ),
        )
    }

    private fun validateUrl(url: HttpUrl): UrlValidation {
        if (url.scheme != "http" && url.scheme != "https") {
            return UrlValidation.Rejected(
                failure(
                    localizedMessage("Only HTTP and HTTPS pages can be analyzed.", "Somente páginas HTTP e HTTPS podem ser analisadas."),
                    HtmlFetchFailureCode.INVALID_URL,
                ),
            )
        }
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) {
            return UrlValidation.Rejected(
                failure(
                    localizedMessage("URLs with embedded credentials are not accepted.", "URLs com credenciais incorporadas não são aceitas."),
                    HtmlFetchFailureCode.INVALID_URL,
                ),
            )
        }
        if (!isAllowedPort(url.port)) {
            return UrlValidation.Rejected(
                failure(
                    localizedMessage("The page uses a non-standard network port.", "A página usa uma porta de rede não padrão."),
                    HtmlFetchFailureCode.BLOCKED_HOST,
                ),
            )
        }
        val addresses = hostResolver.resolvePublicAddresses(url.host)
            ?.takeIf { it.isNotEmpty() }
            ?.toList()
            ?: return UrlValidation.Rejected(
                failure(
                    localizedMessage(
                        "The host is private or could not be resolved; no request was sent.",
                        "O domínio é privado ou não pôde ser resolvido; nenhuma solicitação foi enviada.",
                    ),
                    HtmlFetchFailureCode.BLOCKED_HOST,
                ),
            )
        return UrlValidation.Valid(addresses)
    }

    private fun isAllowedRedirect(current: HttpUrl, destination: HttpUrl): Boolean =
        current.host.equals(destination.host, ignoreCase = true) &&
            isAllowedPort(destination.port) &&
            !(current.scheme == "https" && destination.scheme != "https")

    private fun failure(reason: String, code: HtmlFetchFailureCode) =
        HtmlFetchResult.Failure(reason, code)

    private companion object {
        const val DEFAULT_MAX_RESPONSE_BYTES = 2L * 1024 * 1024
        const val DEFAULT_MAX_REDIRECTS = 5

        fun defaultClient() = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }
}
