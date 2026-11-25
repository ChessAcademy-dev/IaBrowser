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
import com.aegis.browser.search.AegisSearchClient
import com.aegis.browser.search.SearchCategory
import com.aegis.browser.search.SearchEngineOption
import com.aegis.browser.ui.RuntimeUiState
import com.aegis.browser.ui.stripTrackingQueryParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    val adBlocker = AdBlockerEngine()
    private val searchClient = AegisSearchClient()
    private val encryptedStore: EncryptedStore? = runCatching {
        EncryptedStore(application)
    }.getOrNull()

    private var activePassphrase: CharArray? = null
    private var searchJob: Job? = null
    private val blockedHostsPerUrl = mutableMapOf<String, MutableSet<String>>()

    private val initialVaultExists: Boolean = runCatching {
        encryptedStore?.vaultExists() ?: false
    }.getOrDefault(false)

    private val initialFingerprint: String = runCatching {
        encryptedStore?.getKeyFingerprint()?.take(12) ?: ""
    }.getOrDefault("")

    private val _uiState = MutableStateFlow(
        RuntimeUiState(
            vaultExistsOnDisk = false,
            vaultKeyLabel = if (initialFingerprint.isNotBlank()) {
                "KEY ${initialFingerprint.uppercase()}"
            } else if (initialVaultExists) {
                "VAULT LOCKED"
            } else {
                "X25519 READY"
            },
            statusMessage = if (encryptedStore == null) {
                "Vault storage fallback active"
            } else {
                null
            }
        )
    )
    val uiState: StateFlow<RuntimeUiState> = _uiState.asStateFlow()

    fun onAddressInputChange(value: String) {
        _uiState.update { it.copy(addressInput = value) }
    }

    fun setEditingAddress(editing: Boolean) {
        _uiState.update { state ->
            val currentUrl = state.tabs.getOrElse(state.activeTabIndex) { BrowserTab() }.url
            state.copy(
                isEditingAddress = editing,
                addressInput = if (editing && currentUrl != "about:blank") {
                    currentUrl
                } else {
                    state.addressInput
                }
            )
        }
    }

    fun clearAddressInput() {
        _uiState.update { it.copy(addressInput = "", isEditingAddress = true) }
    }

    fun toggleBarPosition() {
        _uiState.update { it.copy(isBottomBar = !it.isBottomBar) }
    }

    fun selectEngine(option: SearchEngineOption) {
        _uiState.update { it.copy(selectedEngine = option) }
        val currentInput = _uiState.value.addressInput.trim()
        if (currentInput.isNotEmpty() && !currentInput.contains(".") && !currentInput.startsWith("http")) {
            runNativeSearch(currentInput, option, _uiState.value.selectedCategory)
        }
    }

    fun selectCategory(category: SearchCategory) {
        _uiState.update { it.copy(selectedCategory = category) }
        val currentInput = _uiState.value.addressInput.trim()
        if (currentInput.isNotEmpty() && !currentInput.contains(".") && !currentInput.startsWith("http")) {
            runNativeSearch(currentInput, _uiState.value.selectedEngine, category)
        }
    }

    fun submitAddressOrQuery(rawInput: String) {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return

        val cleanedInput = stripTrackingQueryParams(trimmed)
        val looksLikeUrl = cleanedInput.startsWith("http://", ignoreCase = true) ||
            cleanedInput.startsWith("https://", ignoreCase = true) ||
            (!cleanedInput.contains(" ") && cleanedInput.contains(".") && !cleanedInput.startsWith("."))

        if (!looksLikeUrl) {
            val state = _uiState.value
            runNativeSearch(trimmed, state.selectedEngine, state.selectedCategory)
            val fallbackUrl = searchClient.buildDirectBrowserUrl(
                query = trimmed,
                option = state.selectedEngine,
                category = state.selectedCategory
            )
            navigateToCleanedUrl(fallbackUrl, keepSearchResults = true)
            return
        }

        val normalized = UrlValidator.normalizeUserInput(cleanedInput)
        val stripped = adBlocker.stripTrackingParameters(normalized)
        navigateToCleanedUrl(stripped, keepSearchResults = false)
    }

    private fun navigateToCleanedUrl(url: String, keepSearchResults: Boolean) {
        when (val result = UrlValidator.validateNavigationUrl(url)) {
            is UrlValidator.ValidationResult.Blocked -> {
                _uiState.update { it.copy(statusMessage = result.reason) }
            }
            is UrlValidator.ValidationResult.Allowed -> {
                val clean = stripTrackingQueryParams(result.normalizedUrl)
                updateActiveTab { it.copy(url = clean) }
                _uiState.update { state ->
                    state.copy(
                        addressInput = if (clean == "about:blank") "" else clean,
                        isEditingAddress = false,
                        nativeSearchResults = if (keepSearchResults) state.nativeSearchResults else emptyList(),
                        statusMessage = null
                    )
                }
            }
        }
    }

    private fun runNativeSearch(
        query: String,
        option: SearchEngineOption,
        category: SearchCategory
    ) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val results = searchClient.searchJson(query = query, category = category)
            _uiState.update { it.copy(nativeSearchResults = results) }
        }
    }

    fun onPageUrlChanged(url: String) {
        val clean = stripTrackingQueryParams(url)
        val host = UrlValidator.extractHost(clean)
        updateActiveTab { it.copy(url = clean) }
        val blockedSet = blockedHostsPerUrl[clean].orEmpty()
        _uiState.update { state ->
            state.copy(
                addressInput = if (clean == "about:blank") "" else clean,
                isEditingAddress = false,
                currentSiteShieldEnabled = adBlocker.isSiteProtected(host),
                currentSiteBlockedCount = blockedSet.size,
                blockedDomainsOnPage = blockedSet.toList()
            )
        }
    }

    fun onPageTitleChanged(title: String) {
        val currentUrl = _uiState.value.activeTab.url
        updateActiveTab { it.copy(title = title) }
        if (currentUrl != "about:blank" && _uiState.value.isVaultUnlocked) {
            val entry = HistoryItem(title = title, url = currentUrl)
            _uiState.update { state ->
                state.copy(history = (listOf(entry) + state.history).take(200))
            }
            persistVaultAsync()
        }
    }

    fun onProgressChanged(progress: Int, canBack: Boolean, canForward: Boolean) {
        _uiState.update {
            it.copy(
                isLoading = progress in 1..99,
                canGoBack = canBack,
                canGoForward = canForward
            )
        }
    }

    fun onRequestBlocked(blockedHost: String) {
        adBlocker.totalBlockedCount.incrementAndGet()
        val activeUrl = _uiState.value.activeTab.url
        val set = blockedHostsPerUrl.getOrPut(activeUrl) { linkedSetOf() }
        set.add(blockedHost)

        _uiState.update { state ->
            state.copy(
                totalBlockedCount = state.totalBlockedCount + 1,
                currentSiteBlockedCount = set.size,
                blockedDomainsOnPage = set.toList()
            )
        }
    }

    fun setGlobalShieldEnabled(enabled: Boolean) {
        adBlocker.globalEnabled.set(enabled)
        _uiState.update { it.copy(globalShieldEnabled = enabled) }
    }

    fun setCurrentSiteShieldEnabled(enabled: Boolean) {
        val host = UrlValidator.extractHost(_uiState.value.activeTab.url)
        if (host.isBlank()) return
        val currentlyProtected = adBlocker.isSiteProtected(host)
        if (currentlyProtected != enabled) {
            adBlocker.toggleSiteAllowlist(host)
        }
        _uiState.update { it.copy(currentSiteShieldEnabled = enabled) }
        persistVaultAsync()
    }

    fun selectTab(index: Int) {
        _uiState.update { state ->
            if (index !in state.tabs.indices) return@update state
            val tab = state.tabs[index]
            val host = UrlValidator.extractHost(tab.url)
            val blocked = blockedHostsPerUrl[tab.url].orEmpty()
            state.copy(
                activeTabIndex = index,
                addressInput = if (tab.url == "about:blank") "" else tab.url,
                isEditingAddress = false,
                currentSiteShieldEnabled = adBlocker.isSiteProtected(host),
                currentSiteBlockedCount = blocked.size,
                blockedDomainsOnPage = blocked.toList()
            )
        }
    }

    fun closeTab(index: Int) {
        _uiState.update { state ->
            if (state.tabs.size <= 1) {
                state.copy(
                    tabs = listOf(BrowserTab(url = "about:blank")),
                    activeTabIndex = 0,
                    addressInput = "",
                    isEditingAddress = false,
                    currentSiteBlockedCount = 0,
                    blockedDomainsOnPage = emptyList()
                )
            } else {
                val updated = state.tabs.filterIndexed { i, _ -> i != index }
                val newIdx = state.activeTabIndex.coerceAtMost(updated.lastIndex)
                val active = updated[newIdx]
                val blocked = blockedHostsPerUrl[active.url].orEmpty()
                state.copy(
                    tabs = updated,
                    activeTabIndex = newIdx,
                    addressInput = if (active.url == "about:blank") "" else active.url,
                    isEditingAddress = false,
                    currentSiteBlockedCount = blocked.size,
                    blockedDomainsOnPage = blocked.toList()
                )
            }
        }
        persistVaultAsync()
    }

    fun newTab(url: String = "about:blank") {
        val clean = stripTrackingQueryParams(url)
        _uiState.update { state ->
            val updated = state.tabs + BrowserTab(url = clean)
            state.copy(
                tabs = updated,
                activeTabIndex = updated.lastIndex,
                addressInput = if (clean == "about:blank") "" else clean,
                isEditingAddress = false,
                currentSiteBlockedCount = 0,
                blockedDomainsOnPage = emptyList()
            )
        }
        persistVaultAsync()
    }

    fun openBookmark(url: String) {
        navigateToCleanedUrl(stripTrackingQueryParams(url), keepSearchResults = false)
    }

    fun deleteLocalBookmark(url: String) {
        _uiState.update { state ->
            state.copy(localBookmarks = state.localBookmarks.filterNot { it.url == url })
        }
        persistVaultAsync()
    }

    fun unlockOrInitVault(passphrase: String, onResult: (Boolean, String?) -> Unit) {
        val store = encryptedStore
        if (store == null) {
            onResult(false, "EncryptedStore auf diesem Gerät nicht verfügbar")
            return
        }
        if (passphrase.length < 8) {
            onResult(false, "Passphrase muss mindestens 8 Zeichen haben")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val chars = passphrase.toCharArray()
                val payload = if (store.vaultExists()) {
                    store.unlockAndLoad(chars)
                } else {
                    val initial = VaultPayload(
                        tabs = _uiState.value.tabs,
                        bookmarks = _uiState.value.localBookmarks,
                        customBlocklist = adBlocker.getCustomBlocklist(),
                        allowlist = adBlocker.getAllowlist()
                    )
                    store.saveEncrypted(initial, chars)
                    initial
                }
                activePassphrase?.fill('\u0000')
                activePassphrase = chars
                adBlocker.setRules(payload.customBlocklist, payload.allowlist)

                val fp = runCatching { store.getKeyFingerprint().take(12).uppercase() }.getOrDefault("X25519")
                _uiState.update { state ->
                    state.copy(
                        isVaultUnlocked = true,
                        vaultKeyLabel = "KEY $fp",
                        localBookmarks = payload.bookmarks,
                        statusMessage = null
                    )
                }
                onResult(true, null)
            } catch (_: Exception) {
                onResult(false, "Entschlüsselung fehlgeschlagen (Passphrase prüfen)")
            }
        }
    }

    fun lockVault() {
        activePassphrase?.fill('\u0000')
        activePassphrase = null
        _uiState.update { it.copy(isVaultUnlocked = false) }
    }

    private fun persistVaultAsync() {
        val store = encryptedStore ?: return
        val pass = activePassphrase ?: return
        val snapshot = _uiState.value
        val payload = VaultPayload(
            tabs = snapshot.tabs,
            bookmarks = snapshot.localBookmarks,
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
            val mutable = state.tabs.toMutableList()
            mutable[idx] = transform(mutable[idx])
            state.copy(tabs = mutable)
        }
    }

    override fun onCleared() {
        activePassphrase?.fill('\u0000')
        activePassphrase = null
        super.onCleared()
    }
}
