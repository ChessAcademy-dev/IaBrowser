package com.aegis.browser.browser

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.net.URI

class SafeWebViewClient(
    private val blocker: AdBlockerEngine,
    private val getCurrentPageHost: () -> String,
    private val onUrlChanged: (String) -> Unit,
    private val onTitleChanged: (String) -> Unit,
    private val onBlockedEvent: () -> Unit,
    private val onSecurityBlock: (String) -> Unit
) : WebViewClient() {

    private val allowedSchemes = setOf("https", "http", "about")
    private val blockedSchemes = setOf(
        "javascript", "data", "vbscript", "file", "content", "intent", "blob"
    )

    private val emptyResponse = WebResourceResponse(
        "text/plain",
        "UTF-8",
        ByteArrayInputStream(ByteArray(0))
    )

    private fun checkNavigationUrl(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        if (trimmed == "about:blank") return null

        val lower = trimmed.lowercase()
        for (scheme in blockedSchemes) {
            if (lower.startsWith("$scheme:")) {
                return "Blocked unsafe scheme: $scheme"
            }
        }

        return try {
            val uri = URI(trimmed)
            val scheme = uri.scheme?.lowercase() ?: return "Missing URL scheme"
            if (scheme !in allowedSchemes) {
                return "Blocked scheme: $scheme"
            }
            if ((scheme == "http" || scheme == "https") && uri.host.isNullOrBlank()) {
                return "Malformed host"
            }
            null
        } catch (_: Exception) {
            "Invalid URI syntax"
        }
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val url = request?.url?.toString() ?: return null
        val pageHost = getCurrentPageHost()

        if (blocker.shouldBlockRequest(url, pageHost)) {
            onBlockedEvent()
            return emptyResponse
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?
    ): Boolean {
        val rawUrl = request?.url?.toString() ?: return true

        val blockReason = checkNavigationUrl(rawUrl)
        if (blockReason != null) {
            onSecurityBlock(blockReason)
            return true
        }

        val pageHost = getCurrentPageHost()
        if (blocker.shouldBlockRequest(rawUrl, pageHost)) {
            onBlockedEvent()
            return true
        }

        if (request.isForMainFrame) {
            val cleaned = blocker.stripTrackingParameters(rawUrl)
            if (cleaned != rawUrl) {
                view?.loadUrl(cleaned)
                return true
            }
        }

        return false
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        if (!url.isNullOrBlank()) {
            onUrlChanged(url)
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (!url.isNullOrBlank()) {
            onUrlChanged(url)
        }
        val title = view?.title
        if (!title.isNullOrBlank()) {
            onTitleChanged(title)
        }
    }
}
