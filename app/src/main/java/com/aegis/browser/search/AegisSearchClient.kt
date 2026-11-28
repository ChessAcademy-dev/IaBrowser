package com.aegis.browser.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

enum class SearchEngineOption(val label: String) {
    SEARXNG_PUBLIC("SEARXNG"),
    DUCKDUCKGO("DDG"),
    BRAVE("BRAVE")
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
    val category: SearchCategory
)

class AegisSearchClient(
    private val publicInstances: List<String> = listOf(
        "https://searx.be",
        "https://search.sapti.me",
        "https://priv.au"
    )
) {
    private val trackingParams = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "utm_id",
        "fbclid", "gclid", "gclsrc", "dclid", "msclkid", "twclid", "igshid"
    )

    fun buildDirectBrowserUrl(
        query: String,
        option: SearchEngineOption,
        category: SearchCategory
    ): String {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        return when (option) {
            SearchEngineOption.SEARXNG_PUBLIC ->
                "${publicInstances.first()}/search?q=$encoded&categories=${category.searxCategory}"
            SearchEngineOption.DUCKDUCKGO ->
                "https://html.duckduckgo.com/html/?q=$encoded"
            SearchEngineOption.BRAVE ->
                "https://search.brave.com/search?q=$encoded"
        }
    }

    suspend fun searchJson(
        query: String,
        category: SearchCategory = SearchCategory.WEB
    ): List<SearchResultItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        val encoded = URLEncoder.encode(trimmed, "UTF-8")

        for (baseUrl in publicInstances) {
            val endpoint = "$baseUrl/search?q=$encoded&categories=${category.searxCategory}&format=json"
            val rawJson = fetchJson(endpoint) ?: continue
            val parsed = parseResults(rawJson, category)
            if (parsed.isNotEmpty()) return@withContext parsed
        }
        emptyList()
    }

    private fun fetchJson(urlStr: String): String? {
        if (!urlStr.startsWith("https://")) return null
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6_000
                readTimeout = 8_000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile) Aegis/1.0")
            }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun parseResults(jsonText: String, category: SearchCategory): List<SearchResultItem> {
        val root = try {
            JSONObject(jsonText)
        } catch (_: Exception) {
            return emptyList()
        }
        val arr: JSONArray = root.optJSONArray("results") ?: return emptyList()
        val seen = HashSet<String>()
        val out = ArrayList<SearchResultItem>()

        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val cleanedUrl = cleanAndEnforceHttps(obj.optString("url")) ?: continue
            val dedupKey = dedupKeyFor(cleanedUrl)
            if (!seen.add(dedupKey)) continue

            out.add(
                SearchResultItem(
                    title = obj.optString("title").trim().ifEmpty { cleanedUrl },
                    url = cleanedUrl,
                    snippet = obj.optString("content").trim(),
                    sourceEngine = obj.optString("engine", "searxng").trim(),
                    category = category
                )
