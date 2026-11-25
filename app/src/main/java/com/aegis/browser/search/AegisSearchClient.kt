package com.aegis.browser.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

enum class SearchEngineOption(val label: String) {
    AEGIS_AGGREGATOR("AEGIS-API"),
    SEARXNG_SELF_HOSTED("SEARXNG"),
    BRAVE("BRAVE"),
    DUCKDUCKGO("DDG")
}

enum class SearchCategory(val id: String, val displayName: String, val searxCategory: String) {
    WEB("web", "WEB", "general"),
    IMAGES("images", "BILDER", "images"),
    VIDEOS("videos", "VIDEOS", "videos"),
    CODE("code", "IT / CODE", "it")
}

data class SearchResultItem(
    val title: String,
    val url: String,
    val snippet: String,
    val sourceEngine: String,
    val category: SearchCategory,
    val proxyThumbnailUrl: String? = null
)

class AegisSearchClient(
    private val aggregatorBaseUrl: String = "https://search.aegis.local",
    private val searxngBaseUrl: String = "https://searx.aegis.local"
) {
    private val throttleMutex = Mutex()
    private var lastRequestTimestampMs: Long = 0L
    private val minIntervalMs: Long = 450L
    private val cache = ConcurrentHashMap<String, List<SearchResultItem>>()

    fun proxiedFaviconUrl(targetPageUrl: String): String {
        val encoded = URLEncoder.encode(targetPageUrl, "UTF-8")
        return "$aggregatorBaseUrl/api/proxy/favicon?url=$encoded"
    }

    fun proxiedImageUrl(imageUrl: String): String {
        val encoded = URLEncoder.encode(imageUrl, "UTF-8")
        return "$aggregatorBaseUrl/api/proxy/image?url=$encoded"
    }

    suspend fun search(
        query: String,
        option: SearchEngineOption,
        category: SearchCategory
    ): List<SearchResultItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        val cacheKey = "${option.name}|${category.id}|${trimmed.lowercase()}"
        cache[cacheKey]?.let { return@withContext it }

        throttleMutex.withLock {
            val now = System.currentTimeMillis()
            val waitMs = (lastRequestTimestampMs + minIntervalMs) - now
            if (waitMs > 0) {
                Thread.sleep(waitMs)
            }
            lastRequestTimestampMs = System.currentTimeMillis()
        }

        val endpoint = when (option) {
            SearchEngineOption.AEGIS_AGGREGATOR -> {
                val q = URLEncoder.encode(trimmed, "UTF-8")
                "$aggregatorBaseUrl/api/search?q=$q&category=${category.id}"
            }
            SearchEngineOption.SEARXNG_SELF_HOSTED -> {
                val q = URLEncoder.encode(trimmed, "UTF-8")
                "$searxngBaseUrl/search?q=$q&categories=${category.searxCategory}&format=json"
            }
            SearchEngineOption.BRAVE, SearchEngineOption.DUCKDUCKGO -> {
                val q = URLEncoder.encode(trimmed, "UTF-8")
                "$aggregatorBaseUrl/api/search?q=$q&category=${category.id}&engine=${option.name.lowercase()}"
            }
        }

        val rawJson = fetchJsonWithCleanHeaders(endpoint) ?: return@withContext emptyList()
        val parsed = parseAndDeduplicate(rawJson, category, option.label)
        cache[cacheKey] = parsed
        parsed
    }

    private fun fetchJsonWithCleanHeaders(urlStr: String): String? {
        if (!urlStr.startsWith("https://")) return null
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 10_000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "AegisSearchClient/1.0 (+privacy-runtime)")
                setRequestProperty("DNT", "1")
            }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun parseAndDeduplicate(
        jsonText: String,
        category: SearchCategory,
        fallbackEngine: String
    ): List<SearchResultItem> {
        val seenUrls = HashSet<String>()
        val results = ArrayList<SearchResultItem>()

        val root = try {
            JSONObject(jsonText)
        } catch (_: Exception) {
            return emptyList()
        }

        val array: JSONArray = root.optJSONArray("results") ?: JSONArray()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val rawUrl = obj.optString("url").trim()
            val httpsUrl = enforceHttps(rawUrl) ?: continue
            val canonicalKey = canonicalizeForDedup(httpsUrl)
            if (!seenUrls.add(canonicalKey)) continue

            val title = obj.optString("title").trim().ifEmpty { httpsUrl }
            val snippet = obj.optString("content", obj.optString("snippet", "")).trim()
            val engine = obj.optString("engine", fallbackEngine).trim()
            val rawThumb = obj.optString("img_src", obj.optString("thumbnail", "")).trim()
            val proxiedThumb = enforceHttps(rawThumb)?.let { proxiedImageUrl(it) }

            results.add(
                SearchResultItem(
                    title = title,
                    url = httpsUrl,
                    snippet = snippet,
                    sourceEngine = engine,
                    category = category,
                    proxyThumbnailUrl = proxiedThumb
                )
            )
        }
        return results
    }

    private fun enforceHttps(rawUrl: String): String? {
        if (rawUrl.isBlank()) return null
        return try {
            val uri = URI(rawUrl)
            when (uri.scheme?.lowercase()) {
                "https" -> rawUrl
                "http" -> URI(
                    "https",
                    uri.rawUserInfo,
                    uri.host,
                    if (uri.port == 80) -1 else uri.port,
                    uri.rawPath,
                    uri.rawQuery,
                    uri.rawFragment
                ).toString()
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun canonicalizeForDedup(url: String): String {
        return try {
            val uri = URI(url)
            val host = uri.host?.lowercase()?.removePrefix("www.") ?: ""
            val path = (uri.rawPath ?: "/").trimEnd('/').ifEmpty { "/" }
            "$host$path"
        } catch (_: Exception) {
            url.lowercase()
        }
    }
}
