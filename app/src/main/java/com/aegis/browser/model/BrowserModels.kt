package com.aegis.browser.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New Tab",
    val url: String = "about:blank",
    val blockedCount: Int = 0
)

@Serializable
data class BookmarkItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class HistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    val visitedAt: Long = System.currentTimeMillis()
)

@Serializable
data class EncryptedNote(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val body: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class VaultPayload(
    val tabs: List<BrowserTab> = emptyList(),
    val bookmarks: List<BookmarkItem> = emptyList(),
    val history: List<HistoryItem> = emptyList(),
    val notes: List<EncryptedNote> = emptyList(),
    val customBlocklist: Set<String> = emptySet(),
    val allowlist: Set<String> = emptySet()
)

@Serializable
data class EncryptedEnvelope(
    val version: Int = 1,
    val kdfSaltBase64: String,
    val kdfIterations: Int,
    val ephemeralPublicKeyBase64: String,
    val ivBase64: String,
    val ciphertextBase64: String
)
