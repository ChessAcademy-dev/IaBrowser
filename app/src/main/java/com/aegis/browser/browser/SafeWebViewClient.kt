package com.aegis.browser.browser

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

class SafeWebViewClient(
    private val blocker: AdBlockerEngine,
    private val getCurrentPageHost: () -> String,
    private val onUrlChanged: (String) -> Unit,
    private val onTitleChanged: (String) -> Unit,
    private val onBlockedEvent: () -> Unit,
    private val onSecurityBlock: (String) -> Unit
) : WebViewClient() {

    private val emptyResponse = WebResourceResponse(
        "text/plain",
        "UTF-8",
        ByteArrayInputStream(ByteArray(0))
    )

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

        // 1. Validate URL scheme (blocks javascript:, file:, intent:, data:, etc.)
        when (val validation = UrlValidator.validateNavigationUrl(rawUrl)) {
            is UrlValidator.ValidationResult.Blocked -> {
                onSecurityBlock(validation.reason)
                return true
            }
            is UrlValidator.ValidationResult.Allowed -> Unit
        }

        // 2. Block unwanted popup / ad-redirect navigations
        val pageHost = getCurrentPageHost()
        if (blocker.shouldBlockRequest(rawUrl, pageHost)) {
            onBlockedEvent()
            return true
        }

        // 3. Strip click-tracking query parameters on top-level navigation
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
