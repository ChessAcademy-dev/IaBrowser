package com.aegis.browser

import com.aegis.browser.browser.AdBlockerEngine
import com.aegis.browser.browser.UrlValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockerEngineTest {

    @Test
    fun blocksKnownAdAndTrackerDomainsAndSubdomains() {
        val engine = AdBlockerEngine()
        assertTrue(engine.shouldBlockRequest("https://stats.g.doubleclick.net/r/collect", "example.com"))
        assertTrue(engine.shouldBlockRequest("https://www.google-analytics.com/analytics.js", "example.com"))
        assertTrue(engine.shouldBlockRequest("https://connect.facebook.net/en_US/fbevents.js", "news.org"))
        assertEquals(3L, engine.totalBlockedCount.get().toLong())
    }

    @Test
    fun allowsFirstPartyAssetsAndRespectsSiteAllowlist() {
        val engine = AdBlockerEngine()
        assertFalse(engine.shouldBlockRequest("https://cdn.example.com/app.js", "example.com"))

        // Allowlist example.com
        val protectedAfterToggle = engine.toggleSiteAllowlist("example.com")
        assertFalse(protectedAfterToggle)
        assertFalse(engine.shouldBlockRequest("https://doubleclick.net/ad.js", "example.com"))

        // Re-enable shield for example.com
        assertTrue(engine.toggleSiteAllowlist("example.com"))
        assertTrue(engine.shouldBlockRequest("https://doubleclick.net/ad.js", "example.com"))
    }

    @Test
    fun stripsClickTrackingParameters() {
        val engine = AdBlockerEngine()
        val dirty = "https://example.com/article?id=42&utm_source=newsletter&fbclid=IwAR123"
        val cleaned = engine.stripTrackingParameters(dirty)
        assertEquals("https://example.com/article?id=42", cleaned)
    }

    @Test
    fun blocksDangerousUrlSchemes() {
        val jsResult = UrlValidator.validateNavigationUrl("javascript:alert(document.cookie)")
        assertTrue(jsResult is UrlValidator.ValidationResult.Blocked)

        val fileResult = UrlValidator.validateNavigationUrl("file:///etc/passwd")
        assertTrue(fileResult is UrlValidator.ValidationResult.Blocked)

        val validHttps = UrlValidator.validateNavigationUrl("https://duckduckgo.com")
        assertTrue(validHttps is UrlValidator.ValidationResult.Allowed)
    }
}
