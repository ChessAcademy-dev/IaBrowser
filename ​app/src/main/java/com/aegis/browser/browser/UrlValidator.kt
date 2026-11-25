package com.aegis.browser.browser

import java.net.URI

object UrlValidator {

    private val ALLOWED_SCHEMES = setOf("https", "http", "about")
    private val DANGEROUS_SCHEMES = setOf(
        "javascript", "data", "vbscript", "file", "content", "intent", "blob"
    )

    sealed class ValidationResult {
        data class Allowed(val normalizedUrl: String) : ValidationResult()
        data class Blocked(val reason: String) : ValidationResult()
    }

    fun normalizeUserInput(raw: String, searchEngineTemplate: String = "https://duckduckgo.com/?q=%s"): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == "about:blank") return "about:blank"

        val lower = trimmed.lowercase()
        for (scheme in DANGEROUS_SCHEMES) {
            if (lower.startsWith("$scheme:")) {
                return "about:blank"
            }
        }

        if (lower.startsWith("https://") || lower.startsWith("http://")) {
            return trimmed
        }

        val looksLikeDomain = !trimmed.contains(" ") &&
            trimmed.contains(".") &&
            !trimmed.startsWith(".") &&
            !trimmed.endsWith(".")

        return if (looksLikeDomain) {
            "https://$trimmed"
        } else {
            val encoded = java.net.URLEncoder.encode(trimmed, "UTF-8")
            searchEngineTemplate.replace("%s", encoded)
        }
    }

    fun validateNavigationUrl(url: String): ValidationResult {
        val trimmed = url.trim()
        if (trimmed == "about:blank") return ValidationResult.Allowed(trimmed)

        return try {
            val uri = URI(trimmed)
            val scheme = uri.scheme?.lowercase()
                ?: return ValidationResult.Blocked("Missing URL scheme")

            if (scheme in DANGEROUS_SCHEMES || scheme !in ALLOWED_SCHEMES) {
                return ValidationResult.Blocked("Blocked unsafe scheme: $scheme")
            }

            if ((scheme == "http" || scheme == "https") && uri.host.isNullOrBlank()) {
                return ValidationResult.Blocked("Malformed host")
            }

            ValidationResult.Allowed(trimmed)
        } catch (_: Exception) {
            ValidationResult.Blocked("Invalid URI syntax")
        }
    }

    fun extractHost(url: String): String {
        return try {
            URI(url).host?.lowercase()?.removePrefix("www.") ?: ""
        } catch (_: Exception) {
            ""
        }
    }
}
