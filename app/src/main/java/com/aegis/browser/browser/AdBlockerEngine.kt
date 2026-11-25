package com.aegis.browser.browser

import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class AdBlockerEngine {

    val globalEnabled = AtomicBoolean(true)
    val totalBlockedCount = AtomicInteger(0)

    private val customBlocklist = ConcurrentHashMap.newKeySet<String>()
    private val siteAllowlist = ConcurrentHashMap.newKeySet<String>()
    private val perSiteBlockedCounts = ConcurrentHashMap<String, Int>()

    // Curated high-signal ad networks, tracking pixels, telemetry & fingerprinting endpoints
    private val builtInBlockedDomains: Set<String> = setOf(
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "google-analytics.com",
        "googletagmanager.com",
        "googletagservices.com",
        "adservice.google.com",
        "pagead2.googlesyndication.com",
        "connect.facebook.net",
        "pixel.facebook.com",
        "an.facebook.com",
        "ads-twitter.com",
        "analytics.twitter.com",
        "ads.linkedin.com",
        "snap.licdn.com",
        "analytics.tiktok.com",
        "ads.tiktok.com",
        "amazon-adsystem.com",
        "criteo.com",
        "criteo.net",
        "outbrain.com",
        "taboola.com",
        "scorecardresearch.com",
        "quantserve.com",
        "hotjar.com",
        "mixpanel.com",
        "segment.io",
        "segment.com",
        "amplitude.com",
        "appsflyer.com",
        "adjust.com",
        "branch.io",
        "bugsnag.com",
        "sentry.io",
        "newrelic.com",
        "nr-data.net",
        "fullstory.com",
        "mouseflow.com",
        "crazyegg.com",
        "clarity.ms",
        "adnxs.com",
        "rubiconproject.com",
        "pubmatic.com",
        "openx.net",
        "casalemedia.com",
        "advertising.com",
        "moatads.com",
        "doubleverify.com",
        "iasds01.com",
        "zedo.com",
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "exoclick.com",
        "juicyads.com",
        "fingerprintjs.com",
        "fpjs.io"
    )

    private val trackerPathPatterns = listOf(
        "/pixel.gif",
        "/pixel.png",
        "/beacon.js",
        "/analytics.js",
        "/gtag/js",
        "/fbevents.js",
        "/fingerprint.js",
        "/fp.min.js",
        "/collect?",
        "/pagead/",
        "/ads/ga-audiences"
    )

    private val trackingQueryParams = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "fbclid", "gclid", "dclid", "msclkid", "mc_eid", "_hsenc", "_hsmi", "yclid"
    )

    fun setRules(blocklist: Set<String>, allowlist: Set<String>) {
        customBlocklist.clear()
        customBlocklist.addAll(blocklist.map { it.lowercase().trim() }.filter { it.isNotEmpty() })
        siteAllowlist.clear()
        siteAllowlist.addAll(allowlist.map { it.lowercase().trim() }.filter { it.isNotEmpty() })
    }

    fun addCustomBlockDomain(domain: String) {
        val clean = domain.lowercase().trim().removePrefix("www.")
        if (clean.isNotEmpty()) customBlocklist.add(clean)
    }

    fun removeCustomBlockDomain(domain: String) {
        customBlocklist.remove(domain.lowercase().trim().removePrefix("www."))
    }

    fun toggleSiteAllowlist(siteHost: String): Boolean {
        val clean = siteHost.lowercase().trim().removePrefix("www.")
        if (clean.isEmpty()) return false
        return if (siteAllowlist.contains(clean)) {
            siteAllowlist.remove(clean)
            true // Shield is now active for this site
        } else {
            siteAllowlist.add(clean)
            false // Site is allowlisted (shield paused for this site)
        }
    }

    fun isSiteProtected(siteHost: String): Boolean {
        if (!globalEnabled.get()) return false
        val clean = siteHost.lowercase().trim().removePrefix("www.")
        return !siteAllowlist.contains(clean)
    }

    fun getCustomBlocklist(): Set<String> = customBlocklist.toSet()
    fun getAllowlist(): Set<String> = siteAllowlist.toSet()
    fun getBlockedCountForSite(siteHost: String): Int =
        perSiteBlockedCounts[siteHost.lowercase().removePrefix("www.")] ?: 0

    fun shouldBlockRequest(requestUrl: String, pageHost: String): Boolean {
        if (!globalEnabled.get()) return false
        val normalizedPageHost = pageHost.lowercase().removePrefix("www.")
        if (normalizedPageHost.isNotEmpty() && siteAllowlist.contains(normalizedPageHost)) {
            return false
        }

        val uri = try {
            URI(requestUrl)
        } catch (_: Exception) {
            return false
        }

        val reqHost = uri.host?.lowercase()?.removePrefix("www.") ?: return false
        val pathAndQuery = ((uri.path ?: "") + (uri.query?.let { "?$it" } ?: "")).lowercase()

        // 1. Domain & subdomain match against built-in + user blocklists
        if (matchesDomainSet(reqHost, builtInBlockedDomains) || matchesDomainSet(reqHost, customBlocklist)) {
            recordBlock(normalizedPageHost)
            return true
        }

        // 2. Third-party tracking pixel / fingerprinting path signature match
        val isThirdParty = normalizedPageHost.isNotEmpty() &&
            reqHost != normalizedPageHost &&
            !reqHost.endsWith(".$normalizedPageHost")

        if (isThirdParty && trackerPathPatterns.any { pathAndQuery.contains(it) }) {
            recordBlock(normalizedPageHost)
            return true
        }

        return false
    }

    fun stripTrackingParameters(url: String): String {
        return try {
            val uri = URI(url)
            val query = uri.rawQuery ?: return url
            val cleanedParams = query.split("&")
                .filter { param ->
                    val key = param.substringBefore("=").lowercase()
                    key !in trackingQueryParams
                }
            val newQuery = if (cleanedParams.isEmpty()) null else cleanedParams.joinToString("&")
            URI(uri.scheme, uri.authority, uri.path, newQuery, uri.fragment).toString()
        } catch (_: Exception) {
            url
        }
    }

    private fun matchesDomainSet(host: String, domainSet: Set<String>): Boolean {
        if (host in domainSet) return true
        var dotIndex = host.indexOf('.')
        while (dotIndex != -1) {
            val parent = host.substring(dotIndex + 1)
            if (parent in domainSet) return true
            dotIndex = host.indexOf('.', dotIndex + 1)
        }
        return false
    }

    private fun recordBlock(pageHost: String) {
        totalBlockedCount.incrementAndGet()
        if (pageHost.isNotEmpty()) {
            perSiteBlockedCounts.compute(pageHost) { _, current -> (current ?: 0) + 1 }
        }
    }
}
