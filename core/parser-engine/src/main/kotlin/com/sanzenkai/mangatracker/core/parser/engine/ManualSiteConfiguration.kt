package com.sanzenkai.mangatracker.core.parser.engine

import com.google.re2j.Pattern
import com.google.re2j.PatternSyntaxException
import com.sanzenkai.mangatracker.core.localization.localizedMessage
import org.json.JSONException
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.select.Selector
import java.net.IDN
import java.net.URI
import java.net.URISyntaxException

data class ExtractionRule(
    val selector: String = "",
    val attribute: String = "text",
    val regex: String = "",
    val captureGroup: Int = 1,
)

data class ManualSiteConfiguration(
    val normalizedHost: String,
    val titleRule: ExtractionRule = ExtractionRule(),
    val coverRule: ExtractionRule = ExtractionRule(attribute = "src"),
    val seriesUrlRule: ExtractionRule = ExtractionRule(attribute = "href"),
    val chapterNumberRule: ExtractionRule = ExtractionRule(),
    val publishedAtRule: ExtractionRule = ExtractionRule(attribute = "datetime"),
    val chapterLinkRule: ExtractionRule = ExtractionRule(attribute = "href"),
    val chapterListItemSelector: String = "",
) {
    companion object {
        fun empty(normalizedHost: String) = ManualSiteConfiguration(normalizedHost = normalizedHost)
    }
}

sealed interface ManualConfigurationDecodeResult {
    data class Success(val configuration: ManualSiteConfiguration) : ManualConfigurationDecodeResult
    data class Failure(val reason: String) : ManualConfigurationDecodeResult
}

object DomainNormalizer {
    fun normalizedHostFromUrl(rawUrl: String): String? {
        val uri = try {
            URI(rawUrl.trim())
        } catch (_: URISyntaxException) {
            return null
        }
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.rawUserInfo != null) return null
        val rawHost = uri.host ?: hostFromAuthority(uri.rawAuthority) ?: return null
        return normalizeHost(rawHost)
    }

    fun normalizeHost(rawHost: String): String? {
        val host = rawHost.trim().trimEnd('.')
        if (host.isEmpty() || host.any { it.isWhitespace() }) return null
        val asciiHost = try {
            IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).lowercase()
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (asciiHost.length > 253 || asciiHost.split('.').any { it.isEmpty() || it.length > 63 }) {
            return null
        }
        return asciiHost
    }

    private fun hostFromAuthority(authority: String?): String? {
        val value = authority ?: return null
        if (value.startsWith("[")) {
            val closeBracket = value.indexOf(']')
            return if (closeBracket > 1) value.substring(1, closeBracket) else null
        }
        val colon = value.lastIndexOf(':')
        if (colon < 0) return value
        val port = value.substring(colon + 1)
        return if (port.all(Char::isDigit)) value.substring(0, colon) else value
    }
}

object ManualConfigurationValidator {
    private const val MAX_SELECTOR_LENGTH = 512
    private const val MAX_REGEX_LENGTH = 256
    private const val MAX_CAPTURE_GROUP = 20

    fun validate(configuration: ManualSiteConfiguration): List<String> {
        val errors = mutableListOf<String>()
        if (DomainNormalizer.normalizeHost(configuration.normalizedHost) != configuration.normalizedHost) {
            errors += localizedMessage(
                "The configuration host is invalid.",
                "O domínio da configuração é inválido.",
            )
        }

        validateRule(localizedMessage("Title", "Título"), configuration.titleRule, required = true, errors)
        validateRule(localizedMessage("Cover", "Capa"), configuration.coverRule, required = false, errors)
        validateRule(
            localizedMessage("Series page", "Página da série"),
            configuration.seriesUrlRule,
            required = false,
            errors,
        )
        validateRule(
            localizedMessage("Chapter number", "Número do capítulo"),
            configuration.chapterNumberRule,
            required = true,
            errors,
        )
        validateRule(
            localizedMessage("Publication date", "Data de publicação"),
            configuration.publishedAtRule,
            required = false,
            errors,
        )
        validateRule(
            localizedMessage("Chapter link", "Link do capítulo"),
            configuration.chapterLinkRule,
            required = true,
            errors,
        )
        validateSelector(
            localizedMessage("Chapter list", "Lista de capítulos"),
            configuration.chapterListItemSelector,
            required = false,
            errors,
        )
        return errors
    }

    private fun validateRule(
        name: String,
        rule: ExtractionRule,
        required: Boolean,
        errors: MutableList<String>,
    ) {
        validateSelector(localizedMessage("field of $name", "campo de $name"), rule.selector, required, errors)
        if (rule.selector.isBlank()) return
        if (rule.attribute.length > MAX_SELECTOR_LENGTH) {
            errors += localizedMessage(
                "The $name attribute name is too long.",
                "O nome do atributo de $name é muito longo.",
            )
        }
        if (rule.regex.length > MAX_REGEX_LENGTH) {
            errors += localizedMessage(
                "The $name regular expression is too long.",
                "A expressão regular de $name é muito longa.",
            )
        } else if (rule.regex.isNotBlank()) {
            val pattern = try {
                Pattern.compile(rule.regex)
            } catch (_: PatternSyntaxException) {
                errors += localizedMessage(
                    "The $name regular expression is invalid.",
                    "A expressão regular de $name é inválida.",
                )
                return
            }
            if (rule.captureGroup < 0 ||
                rule.captureGroup > pattern.matcher("").groupCount() ||
                rule.captureGroup > MAX_CAPTURE_GROUP
            ) {
                errors += localizedMessage(
                    "The $name capture group is invalid.",
                    "O grupo de captura de $name é inválido.",
                )
            }
        }
    }

    private fun validateSelector(
        name: String,
        selector: String,
        required: Boolean,
        errors: MutableList<String>,
    ) {
        if (selector.isBlank()) {
            if (required) {
                errors += localizedMessage(
                    "The $name CSS selector is required.",
                    "O seletor CSS de $name é obrigatório.",
                )
            }
            return
        }
        if (selector.length > MAX_SELECTOR_LENGTH) {
            errors += localizedMessage(
                "The $name CSS selector is too long.",
                "O seletor CSS de $name é muito longo.",
            )
            return
        }
        try {
            Jsoup.parse("").select(selector)
        } catch (_: Selector.SelectorParseException) {
            errors += localizedMessage(
                "The $name CSS selector is invalid.",
                "O seletor CSS de $name é inválido.",
            )
        }
    }
}

object ManualSiteConfigurationCodec {
    fun encode(configuration: ManualSiteConfiguration): String =
        JSONObject()
            .put("format", 1)
            .put("normalizedHost", configuration.normalizedHost)
            .put("titleRule", configuration.titleRule.toJson())
            .put("coverRule", configuration.coverRule.toJson())
            .put("seriesUrlRule", configuration.seriesUrlRule.toJson())
            .put("chapterNumberRule", configuration.chapterNumberRule.toJson())
            .put("publishedAtRule", configuration.publishedAtRule.toJson())
            .put("chapterLinkRule", configuration.chapterLinkRule.toJson())
            .put("chapterListItemSelector", configuration.chapterListItemSelector)
            .toString()

    fun decode(encoded: String): ManualConfigurationDecodeResult {
        val root = try {
            JSONObject(encoded)
        } catch (_: JSONException) {
            return ManualConfigurationDecodeResult.Failure(
                localizedMessage(
                    "The saved configuration is not valid JSON.",
                    "A configuração salva não é um JSON válido.",
                ),
            )
        }
        if (root.optInt("format", -1) != 1) {
            return ManualConfigurationDecodeResult.Failure(
                localizedMessage(
                    "The saved configuration format is unsupported.",
                    "O formato da configuração salva não é compatível.",
                ),
            )
        }
        val configuration = try {
            ManualSiteConfiguration(
                normalizedHost = root.getString("normalizedHost"),
                titleRule = root.getJSONObject("titleRule").toRule(),
                coverRule = root.getJSONObject("coverRule").toRule(),
                seriesUrlRule = root.getJSONObject("seriesUrlRule").toRule(),
                chapterNumberRule = root.getJSONObject("chapterNumberRule").toRule(),
                publishedAtRule = root.getJSONObject("publishedAtRule").toRule(),
                chapterLinkRule = root.getJSONObject("chapterLinkRule").toRule(),
                chapterListItemSelector = root.getString("chapterListItemSelector"),
            )
        } catch (_: JSONException) {
            return ManualConfigurationDecodeResult.Failure(
                localizedMessage(
                    "The saved configuration is incomplete.",
                    "A configuração salva está incompleta.",
                ),
            )
        }
        return ManualConfigurationDecodeResult.Success(configuration)
    }

    private fun ExtractionRule.toJson() =
        JSONObject()
            .put("selector", selector)
            .put("attribute", attribute)
            .put("regex", regex)
            .put("captureGroup", captureGroup)

    private fun JSONObject.toRule() =
        ExtractionRule(
            selector = getString("selector"),
            attribute = getString("attribute"),
            regex = getString("regex"),
            captureGroup = getInt("captureGroup"),
        )
}
