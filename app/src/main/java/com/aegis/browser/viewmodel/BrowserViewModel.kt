package com.aegis.browser.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aegis.browser.browser.AdBlockerEngine
import com.aegis.browser.browser.UrlValidator
import com.aegis.browser.data.EncryptedStore
import com.aegis.browser.model.BookmarkItem
import com.aegis.browser.model.BrowserTab
import com.aegis.browser.model.EncryptedNote
import com.aegis.browser.model.HistoryItem
import com.aegis.browser.model.VaultPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BrowserUiState(
    val tabs: List<BrowserTab> = listOf(BrowserTab()),
    val activeTabIndex: Int = 0,
    val addressBarInput: String = "",
    val isLoading: Boolean = false,
    val pageProgress: Float = 0f,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val globalShieldEnabled: Boolean = true,
    val totalBlockedRequests: Int = 0,
    val currentSiteBlockedRequests: Int = 0,
    val isCurrentSiteProtected: Boolean = true,
    val customBlocklist: Set<String> = emptySet(),
    val allowlist: Set<String> = emptySet(),
    val isVaultUnlocked: Boolean = false,
    val vaultExistsOnDisk: Boolean = false,
    val keyFingerprint: String = "",
    val bookmarks: List<BookmarkItem> = emptyList(),
    val history: List<HistoryItem> = emptyList(),
    val notes: List<EncryptedNote> = emptyList(),
    val statusBanner: String? = null
) {
    val activeTab: BrowserTab
        get() = tabs.getOrElse(activeTabIndex) { BrowserTab() }
}

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    val adBlocker = AdBlockerEngine()
    private val encryptedStore: EncryptedStore? = runCatching {
        EncryptedStore(application)
    }.getOrNull()
    private var activePassphrase: CharArray? = null

    private val initialVaultExists: Boolean = runCatching {
        encryptedStore?.vaultExists() ?: false
    }.getOrDefault(false)

    private val initialFingerprint: String = runCatching {
        encryptedStore?.getKeyFingerprint() ?: ""
    }.getOrDefault("")

    private val initialBanner: String? = if (encryptedStore == null) {
        "Vault storage unavailable on startup"
    } else {
        null
    }

    private val _uiState = MutableStateFlow(
        BrowserUiState(
            vaultExistsOnDisk = initialVaultExists,
            keyFingerprint = initialFingerprint,
            statusBanner = initialBanner
        )
    )
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    fun onAddressInputChange(value: String) {
        _uiState.update { it.copy(addressBarInput = value) }
    }

    fun navigateToInput(rawInput: String) {
        val normalized = UrlValidator.normalizeUserInput(rawInput)
        val cleaned = adBlocker.stripTrackingParameters(normalized)
        when (val result = UrlValidator.validateNavigationUrl(cleaned)) {
            is UrlValidator.ValidationResult.Blocked -> {
                _uiState.update { it.copy(statusBanner = result.reason) }
            }
            is UrlValidator.ValidationResult.Allowed -> {
                updateActiveTab { it.copy(url = result.normalizedUrl) }
                _uiState.update {
                    it.copy(
                        addressBarInput = if (result.normalizedUrl == "about:blank") "" else result.normalizedUrl,
                        statusBanner = null
                    )
                }
            }
        }
    }

    fun onPageUrlChanged(url: String) {
        val host = UrlValidator.extractHost(url)
        updateActiveTab { it.copy(url = url) }
        _uiState.update {
            it.copy(
                addressBarInput = if (url == "about:blank") "" else url,
                isCurrentSiteProtected = adBlocker.isSiteProtected(host),
                currentSiteBlockedRequests = adBlocker.getBlockedCountForSite(host)
            )
        }
    }

    fun onPageTitleChanged(title: String) {
        val currentUrl = _uiState.value.activeTab.url
        updateActiveTab { it.copy(title = title) }
        if (currentUrl != "about:blank" && _uiState.value.isVaultUnlocked) {
            val newEntry = HistoryItem(title = title, url = currentUrl)
            _uiState.update { state ->
                val updatedHistory = (listOf(newEntry) + state.history).take(200)
                state.copy(history = updatedHistory)
            }
            persistVaultAsync()
        }
    }

    fun onNavigationStateChanged(canBack: Boolean, canForward: Boolean, progress: Int) {
        _uiState.update {
            it.copy(
                canGoBack = canBack,
                canGoForward = canForward,
                isLoading = progress in 1..99,
                pageProgress = progress / 100f
            )
        }
    }

    fun onRequestBlocked() {
        val host = UrlValidator.extractHost(_uiState.value.activeTab.url)
        _uiState.update {
            it.copy(
                totalBlockedRequests = adBlocker.totalBlockedCount.get(),
                currentSiteBlockedRequests = adBlocker.getBlockedCountForSite(host)
            )
        }
    }

    fun onSecurityBlock(reason: String) {
        _uiState.update { it.copy(statusBanner = reason) }
    }

    fun clearStatusBanner() {
        _uiState.update { it.copy(statusBanner = null) }
    }

    fun toggleGlobalShield() {
        val next = !adBlocker.globalEnabled.get()
        adBlocker.globalEnabled.set(next)
        val host = UrlValidator.extractHost(_uiState.value.activeTab.url)
        _uiState.update {
            it.copy(
                globalShieldEnabled = next,
                isCurrentSiteProtected = adBlocker.isSiteProtected(host)
            )
        }
    }

    fun toggleCurrentSiteShield() {
        val host = UrlValidator.extractHost(_uiState.value.activeTab.url)
        if (host.isEmpty()) return
        val protectedNow = adBlocker.toggleSiteAllowlist(host)
        _uiState.update {
            it.copy(
                isCurrentSiteProtected = protectedNow,
                allowlist = adBlocker.getAllowlist()
            )
        }
        persistVaultAsync()
    }

    fun addCustomBlockDomain(domain: String) {
        adBlocker.addCustomBlockDomain(domain)
        _uiState.update { it.copy(customBlocklist = adBlocker.getCustomBlocklist()) }
        persistVaultAsync()
    }

    fun removeCustomBlockDomain(domain: String) {
        adBlocker.removeCustomBlockDomain(domain)
        _uiState.update { it.copy(customBlocklist = adBlocker.getCustomBlocklist()) }
        persistVaultAsync()
    }

    fun addTab(url: String = "about:blank") {
        val newTab = BrowserTab(url = url)
        _uiState.update { state ->
            val updated = state.tabs + newTab
            state.copy(
                tabs = updated,
                activeTabIndex = updated.lastIndex,
                addressBarInput = if (url == "about:blank") "" else url
            )
        }
        persistVaultAsync()
    }

    fun selectTab(index: Int) {
        _uiState.update { state ->
            if (index !in state.tabs.indices) return@update state
            val tab = state.tabs[index]
            val host = UrlValidator.extractHost(tab.url)
            state.copy(
                activeTabIndex = index,
                addressBarInput = if (tab.url == "about:blank") "" else tab.url,
                isCurrentSiteProtected = adBlocker.isSiteProtected(host),
                currentSiteBlockedRequests = adBlocker.getBlockedCountForSite(host)
            )
        }
    }

    fun closeTab(index: Int) {
        _uiState.update { state ->
            if (state.tabs.size <= 1) {
                val reset = listOf(BrowserTab())
                state.copy(tabs = reset, activeTabIndex = 0, addressBarInput = "")
            } else {
                val updated = state.tabs.filterIndexed { i, _ -> i != index }
                val newIndex = state.activeTabIndex.coerceAtMost(updated.lastIndex)
                val active = updated[newIndex]
                state.copy(
                    tabs = updated,
                    activeTabIndex = newIndex,
                    addressBarInput = if (active.url == "about:blank") "" else active.url
                )
            }
        }
        persistVaultAsync()
    }

    fun toggleBookmarkCurrentPage() {
        val tab = _uiState.value.activeTab
        if (tab.url == "about:blank") return
        if (!_uiState.value.isVaultUnlocked) {
            _uiState.update { it.copy(statusBanner = "Unlock E2EE vault to save encrypted bookmarks") }
            return
        }
        _uiState.update { state ->
            val exists = state.bookmarks.any { it.url == tab.url }
            val updated = if (exists) {
                state.bookmarks.filterNot { it.url == tab.url }
            } else {
                listOf(BookmarkItem(title = tab.title.ifBlank { tab.url }, url = tab.url)) + state.bookmarks
            }
            state.copy(bookmarks = updated)
        }
        persistVaultAsync()
    }

    fun addEncryptedNote(title: String, body: String) {
        if (!_uiState.value.isVaultUnlocked || title.isBlank()) return
        val note = EncryptedNote(title = title.trim(), body = body.trim())
        _uiState.update { it.copy(notes = listOf(note) + it.notes) }
        persistVaultAsync()
    }

    fun deleteEncryptedNote(id: String) {
        if (!_uiState.value.isVaultUnlocked) return
        _uiState.update { it.copy(notes = it.notes.filterNot { n -> n.id == id }) }
        persistVaultAsync()
    }

    fun unlockOrInitializeVault(passphraseInput: String, onResult: (Boolean, String?) -> Unit) {
        val store = encryptedStore
        if (store == null) {
            onResult(false, "Encrypted vault is unavailable on this device")
            return
        }
        if (passphraseInput.length < 8) {
            onResult(false, "Passphrase must be at least 8 characters")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val chars = passphraseInput.toCharArray()
                val loaded = if (store.vaultExists()) {
                    store.unlockAndLoad(chars)
                } else {
                    val initial = VaultPayload(
                        tabs = _uiState.value.tabs,
                        customBlocklist = adBlocker.getCustomBlocklist(),
                        allowlist = adBlocker.getAllowlist()
                    )
                    store.saveEncrypted(initial, chars)
                    initial
                }

                activePassphrase?.fill('\u0000')
                activePassphrase = chars

                adBlocker.setRules(loaded.customBlocklist, loaded.allowlist)
                val restoredTabs = loaded.tabs.ifEmpty { _uiState.value.tabs }

                _uiState.update { state ->
                    state.copy(
                        isVaultUnlocked = true,
                        vaultExistsOnDisk = true,
                        tabs = restoredTabs,
                        activeTabIndex = 0,
                        bookmarks = loaded.bookmarks,
                        history = loaded.history,
                        notes = loaded.notes,
                        customBlocklist = loaded.customBlocklist,
                        allowlist = loaded.allowlist,
                        statusBanner = null
                    )
                }
                onResult(true, null)
            } catch (_: Exception) {
                onResult(false, "Decryption failed: invalid passphrase or tampered ciphertext")
            }
        }
    }

    fun lockVault() {
        activePassphrase?.fill('\u0000')
        activePassphrase = null
        _uiState.update {
            it.copy(
                isVaultUnlocked = false,
                bookmarks = emptyList(),
                history = emptyList(),
                notes = emptyList()
            )
        }
    }

    fun exportCiphertextJson(): String? = runCatching {
        encryptedStore?.exportCiphertextEnvelope()
    }.getOrNull()

    fun importCiphertextJson(envelopeJson: String, onResult: (Boolean, String?) -> Unit) {
        val store = encryptedStore
        if (store == null) {
            onResult(false, "Encrypted vault is unavailable on this device")
            return
        }
        val pass = activePassphrase
        if (pass == null) {
            onResult(false, "Unlock vault first before importing ciphertext")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val payload = store.importCiphertextEnvelope(envelopeJson, pass)
                adBlocker.setRules(payload.customBlocklist, payload.allowlist)
                _uiState.update {
                    it.copy(
                        tabs = payload.tabs.ifEmpty { it.tabs },
                        bookmarks = payload.bookmarks,
                        history = payload.history,
                        notes = payload.notes,
                        customBlocklist = payload.customBlocklist,
                        allowlist = payload.allowlist
                    )
                }
                onResult(true, null)
            } catch (_: Exception) {
                onResult(false, "Ciphertext verification failed (GCM tag mismatch)")
            }
        }
    }

    private fun persistVaultAsync() {
        val store = encryptedStore ?: return
        val pass = activePassphrase ?: return
        val snapshot = _uiState.value
        val payload = VaultPayload(
            tabs = snapshot.tabs,
            bookmarks = snapshot.bookmarks,
            history = snapshot.history,
            notes = snapshot.notes,
            customBlocklist = adBlocker.getCustomBlocklist(),
            allowlist = adBlocker.getAllowlist()
        )
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { store.saveEncrypted(payload, pass) }
        }
    }

    private fun updateActiveTab(transform: (BrowserTab) -> BrowserTab) {
        _uiState.update { state ->
            val idx = state.activeTabIndex
            if (idx !in state.tabs.indices) return@update state
            val updated = state.tabs.toMutableList()
            updated[idx] = transform(updated[idx])
            state.copy(tabs = updated)
        }
    }

    override fun onCleared() {
        activePassphrase?.fill('\u0000')
        activePassphrase = null
        super.onCleared()
    }
}
