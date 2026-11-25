package com.aegis.browser.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.aegis.browser.model.BookmarkItem
import com.aegis.browser.model.BrowserTab
import com.aegis.browser.search.SearchCategory
import com.aegis.browser.search.SearchEngineOption
import com.aegis.browser.search.SearchResultItem
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.URLEncoder

object AegisColors {
    val OledBlack = Color(0xFF000000)
    val SurfaceCard = Color(0xFF0A0D0B)
    val SurfaceElevated = Color(0xFF101512)
    val BorderSubtle = Color(0xFF1E2923)
    val EmeraldPrimary = Color(0xFF10B981)
    val EmeraldBright = Color(0xFF34D399)
    val EmeraldDim = Color(0xFF064E3B)
    val TextPrimary = Color(0xFFF3F4F6)
    val TextSecondary = Color(0xFF9CA3AF)
    val TextMuted = Color(0xFF6B7280)
    val DangerRed = Color(0xFFEF4444)
}

private val MonoFont = FontFamily.Monospace

private val TrackingParamsToStrip = setOf(
    "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "utm_id",
    "fbclid", "gclid", "gclsrc", "dclid", "msclkid", "twclid", "igshid", "mc_cid", "mc_eid"
)

fun stripTrackingQueryParams(rawUrl: String): String {
    if (rawUrl.isBlank() || rawUrl == "about:blank") return rawUrl
    return try {
        val uri = URI(rawUrl)
        val rawQuery = uri.rawQuery ?: return rawUrl
        val keptParams = rawQuery
            .split("&")
            .filter { pair ->
                val key = pair.substringBefore("=").lowercase()
                key.isNotEmpty() && key !in TrackingParamsToStrip && !key.startsWith("utm_")
            }
        val newQuery = keptParams.joinToString("&").ifEmpty { null }
        URI(
            uri.scheme,
            uri.rawUserInfo,
            uri.host,
            uri.port,
            uri.rawPath,
            newQuery,
            uri.rawFragment
        ).toString()
    } catch (_: Exception) {
        rawUrl
    }
}

fun formatIdleDomainLabel(url: String): String {
    if (url.isBlank() || url == "about:blank") return "AEGIS // READY"
    return try {
        val cleaned = stripTrackingQueryParams(url)
        val uri = URI(cleaned)
        val host = uri.host?.lowercase()?.removePrefix("www.")
        if (!host.isNullOrBlank()) host else cleaned.take(42)
    } catch (_: Exception) {
        url.take(42)
    }
}

fun buildSearchUrl(query: String, option: SearchEngineOption, category: SearchCategory): String {
    val trimmed = query.trim()
    val encoded = URLEncoder.encode(trimmed, "UTF-8")
    return when (option) {
        SearchEngineOption.AEGIS_AGGREGATOR ->
            "https://search.aegis.local/api/search?q=$encoded&cat=${category.id}"
        SearchEngineOption.SEARXNG_SELF_HOSTED ->
            "https://searx.aegis.local/search?q=$encoded&categories=${category.searxCategory}&format=html"
        SearchEngineOption.BRAVE ->
            "https://search.brave.com/search?q=$encoded"
        SearchEngineOption.DUCKDUCKGO ->
            "https://duckduckgo.com/?q=$encoded"
    }
}

data class RuntimeUiState(
    val tabs: List<BrowserTab> = listOf(BrowserTab()),
    val activeTabIndex: Int = 0,
    val addressInput: String = "",
    val isEditingAddress: Boolean = false,
    val isBottomBar: Boolean = true,
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val globalShieldEnabled: Boolean = true,
    val currentSiteShieldEnabled: Boolean = true,
    val totalBlockedCount: Int = 0,
    val currentSiteBlockedCount: Int = 0,
    val blockedDomainsOnPage: List<String> = emptyList(),
    val isVaultUnlocked: Boolean = false,
    val vaultKeyLabel: String = "X25519 LOCKED",
    val thirdPartyCookiesBlocked: Boolean = true,
    val safeBrowseActive: Boolean = true,
    val selectedEngine: SearchEngineOption = SearchEngineOption.AEGIS_AGGREGATOR,
    val selectedCategory: SearchCategory = SearchCategory.WEB,
    val nativeSearchResults: List<SearchResultItem> = emptyList(),
    val localBookmarks: List<BookmarkItem> = emptyList(),
    val statusMessage: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AegisRuntimeScreen(
    state: RuntimeUiState,
    onAddressInputChange: (String) -> Unit,
    onEditingChange: (Boolean) -> Unit,
    onSubmitAddress: (String) -> Unit,
    onClearAddress: () -> Unit,
    onToggleBarPosition: () -> Unit,
    onSelectEngine: (SearchEngineOption) -> Unit,
    onSelectCategory: (SearchCategory) -> Unit,
    onOpenVaultSheet: () -> Unit,
    onOpenShieldOverlay: () -> Unit,
    onToggleGlobalShield: (Boolean) -> Unit,
    onToggleSiteShield: (Boolean) -> Unit,
    onSelectTab: (Int) -> Unit,
    onCloseTab: (Int) -> Unit,
    onNewTab: () -> Unit,
    onPageUrlChanged: (String) -> Unit,
    onPageTitleChanged: (String) -> Unit,
    onProgressChanged: (Int, Boolean, Boolean) -> Unit,
    onRequestBlocked: (String) -> Unit,
    onOpenLocalBookmark: (String) -> Unit,
    onDeleteLocalBookmark: (String) -> Unit,
    vaultSheetContent: @Composable (() -> Unit)? = null,
    shieldSheetContent: @Composable (() -> Unit)? = null
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val activeTab = state.tabs.getOrElse(state.activeTabIndex) { BrowserTab() }
    val showStartScreen = activeTab.url.isBlank() || activeTab.url == "about:blank"

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var horizontalSwipeAccum by remember { mutableFloatStateOf(0f) }

    val addressBarSlot: @Composable () -> Unit = {
        AddressCapsuleBar(
            currentUrl = activeTab.url,
            addressInput = state.addressInput,
            isEditing = state.isEditingAddress,
            shieldEnabled = state.globalShieldEnabled && state.currentSiteShieldEnabled,
            blockedCount = if (showStartScreen) state.totalBlockedCount else state.currentSiteBlockedCount,
            tabCount = state.tabs.size,
            activeTabNumber = state.activeTabIndex + 1,
            isBottomBar = state.isBottomBar,
            onInputChange = onAddressInputChange,
            onFocusChanged = onEditingChange,
            onSubmit = {
                focusManager.clearFocus()
                onEditingChange(false)
                onSubmitAddress(state.addressInput)
            },
            onClear = {
                onClearAddress()
                onEditingChange(true)
            },
            onShieldClick = onOpenShieldOverlay,
            onToggleBarPosition = onToggleBarPosition,
            onSwipeTabStep = { delta ->
                if (state.tabs.size > 1) {
                    val nextIdx = (state.activeTabIndex + delta).coerceIn(0, state.tabs.lastIndex)
                    if (nextIdx != state.activeTabIndex) onSelectTab(nextIdx)
                }
            },
            onCloseCurrentTab = { onCloseTab(state.activeTabIndex) }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AegisColors.OledBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!state.isBottomBar) {
                addressBarSlot()
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (showStartScreen) {
                    PrivacyRuntimeStartScreen(
                        state = state,
                        onOpenVault = onOpenVaultSheet,
                        onOpenShield = onOpenShieldOverlay,
                        onSelectEngine = onSelectEngine,
                        onSelectCategory = onSelectCategory,
                        onSubmitQuery = { query ->
                            onAddressInputChange(query)
                            onSubmitAddress(query)
                        },
                        onOpenBookmark = onOpenLocalBookmark,
                        onDeleteBookmark = onDeleteLocalBookmark
                    )
                } else {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.mixedContentMode =
                                    android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                setBackgroundColor(android.graphics.Color.BLACK)

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        onProgressChanged(
                                            newProgress,
                                            view?.canGoBack() == true,
                                            view?.canGoForward() == true
                                        )
                                    }

                                    override fun onReceivedTitle(view: WebView?, title: String?) {
                                        if (!title.isNullOrBlank()) onPageTitleChanged(title)
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun shouldInterceptRequest(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): WebResourceResponse? {
                                        val reqUrl = request?.url?.toString() ?: return null
                                        val host = formatIdleDomainLabel(reqUrl)
                                        if (state.globalShieldEnabled &&
                                            state.currentSiteShieldEnabled &&
                                            isKnownTrackerHost(host)
                                        ) {
                                            onRequestBlocked(host)
                                            return WebResourceResponse(
                                                "text/plain",
                                                "UTF-8",
                                                ByteArrayInputStream(ByteArray(0))
                                            )
                                        }
                                        return super.shouldInterceptRequest(view, request)
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val raw = request?.url?.toString() ?: return true
                                        val lower = raw.trim().lowercase()
                                        if (lower.startsWith("javascript:") ||
                                            lower.startsWith("intent:") ||
                                            lower.startsWith("file:")
                                        ) {
                                            return true
                                        }
                                        val cleaned = stripTrackingQueryParams(raw)
                                        if (cleaned != raw) {
                                            view?.loadUrl(cleaned)
                                            return true
                                        }
                                        return false
                                    }

                                    override fun onPageStarted(
                                        view: WebView?,
                                        url: String?,
                                        favicon: Bitmap?
                                    ) {
                                        super.onPageStarted(view, url, favicon)
                                        if (!url.isNullOrBlank()) {
                                            onPageUrlChanged(stripTrackingQueryParams(url))
                                        }
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        if (!url.isNullOrBlank()) {
                                            onPageUrlChanged(stripTrackingQueryParams(url))
                                        }
                                    }
                                }
                                webViewRef = this
                            }
                        },
                        update = { view ->
                            val target = stripTrackingQueryParams(activeTab.url)
                            if (target.isNotBlank() && target != "about:blank" && view.url != target) {
                                view.loadUrl(target)
                            }
                        }
                    )
                }

                if (state.tabs.size > 1) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AegisColors.SurfaceElevated.copy(alpha = 0.92f))
                            .border(1.dp, AegisColors.BorderSubtle, RoundedCornerShape(8.dp))
                            .pointerInput(state.activeTabIndex, state.tabs.size) {
                                detectHorizontalDragGestures(
                                    onDragEnd = { horizontalSwipeAccum = 0f },
                                    onHorizontalDrag = { _, dragAmount ->
                                        horizontalSwipeAccum += dragAmount
                                        if (horizontalSwipeAccum > 90f) {
                                            horizontalSwipeAccum = 0f
                                            if (state.activeTabIndex > 0) {
                                                onSelectTab(state.activeTabIndex - 1)
                                            }
                                        } else if (horizontalSwipeAccum < -90f) {
                                            horizontalSwipeAccum = 0f
                                            if (state.activeTabIndex < state.tabs.lastIndex) {
                                                onSelectTab(state.activeTabIndex + 1)
                                            }
                                        }
                                    }
                                )
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "TAB ${state.activeTabIndex + 1}/${state.tabs.size}",
                            fontFamily = MonoFont,
                            fontSize = 11.sp,
                            color = AegisColors.EmeraldBright
                        )
                        Text(
                            text = "SWIPE",
                            fontFamily = MonoFont,
                            fontSize = 10.sp,
                            color = AegisColors.TextMuted
                        )
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Tab schließen",
                            tint = AegisColors.TextSecondary,
                            modifier = Modifier
                                .size(15.dp)
                                .clickable { onCloseTab(state.activeTabIndex) }
                        )
                    }
                }
            }

            if (!showStartScreen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AegisColors.SurfaceCard)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { webViewRef?.goBack() },
                        enabled = state.canGoBack || !showStartScreen
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowBack,
                            contentDescription = "Zurück",
                            tint = if (state.canGoBack) AegisColors.TextPrimary else AegisColors.TextMuted
                        )
                    }
                    IconButton(
                        onClick = { webViewRef?.goForward() },
                        enabled = state.canGoForward
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowForward,
                            contentDescription = "Vor",
                            tint = if (state.canGoForward) AegisColors.TextPrimary else AegisColors.TextMuted
                        )
                    }
                    IconButton(onClick = onNewTab) {
                        Icon(
                            imageVector = Icons.Outlined.Tab,
                            contentDescription = "Neuer Tab",
                            tint = AegisColors.EmeraldBright
                        )
                    }
                }
            }

            if (state.isBottomBar) {
                addressBarSlot()
            }
        }
    }

    if (vaultSheetContent != null) {
        ModalBottomSheet(
            onDismissRequest = onOpenVaultSheet,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = AegisColors.SurfaceCard,
            contentColor = AegisColors.TextPrimary
        ) {
            vaultSheetContent()
        }
    }

    if (shieldSheetContent != null) {
        ModalBottomSheet(
            onDismissRequest = onOpenShieldOverlay,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = AegisColors.SurfaceCard,
            contentColor = AegisColors.TextPrimary
        ) {
            ShieldOverlayContent(
                globalShieldEnabled = state.globalShieldEnabled,
                siteShieldEnabled = state.currentSiteShieldEnabled,
                currentHost = formatIdleDomainLabel(activeTab.url),
                blockedCount = state.currentSiteBlockedCount,
                blockedDomains = state.blockedDomainsOnPage,
                onToggleGlobal = onToggleGlobalShield,
                onToggleSite = onToggleSiteShield
            )
        }
    }
}

@Composable
private fun PrivacyRuntimeStartScreen(
    state: RuntimeUiState,
    onOpenVault: () -> Unit,
    onOpenShield: () -> Unit,
    onSelectEngine: (SearchEngineOption) -> Unit,
    onSelectCategory: (SearchCategory) -> Unit,
    onSubmitQuery: (String) -> Unit,
    onOpenBookmark: (String) -> Unit,
    onDeleteBookmark: (String) -> Unit
) {
    var quickQuery by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AegisColors.OledBlack),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "AEGIS // PRIVACY RUNTIME",
                    fontFamily = MonoFont,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.4.sp,
                    color = AegisColors.EmeraldBright
                )
                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = AegisColors.SurfaceCard,
                    border = BorderStroke(1.dp, AegisColors.BorderSubtle)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RuntimeStatusRow(
                            label = "TRACKER SHIELD",
                            value = if (state.globalShieldEnabled) "ACTIVE (${state.totalBlockedCount})" else "PAUSED",
                            active = state.globalShieldEnabled,
                            onClick = onOpenShield
                        )
                        HorizontalDivider(color = AegisColors.BorderSubtle, thickness = 0.75.dp)
                        RuntimeStatusRow(
                            label = "E2EE STORAGE (X25519)",
                            value = if (state.isVaultUnlocked) "UNLOCKED" else state.vaultKeyLabel,
                            active = state.isVaultUnlocked,
                            onClick = onOpenVault
                        )
                        HorizontalDivider(color = AegisColors.BorderSubtle, thickness = 0.75.dp)
                        RuntimeStatusRow(
                            label = "3RD-PARTY COOKIES",
                            value = if (state.thirdPartyCookiesBlocked) "BLOCKED" else "ALLOWED",
                            active = state.thirdPartyCookiesBlocked,
                            onClick = null
                        )
                        HorizontalDivider(color = AegisColors.BorderSubtle, thickness = 0.75.dp)
                        RuntimeStatusRow(
                            label = "SAFE BROWSE",
                            value = if (state.safeBrowseActive) "STRICT HTTPS" else "STANDARD",
                            active = state.safeBrowseActive,
                            onClick = null
                        )
                    }
                }
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = AegisColors.SurfaceCard,
                border = BorderStroke(1.dp, AegisColors.BorderSubtle)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "DIRECT SEARCH & ENGINE",
                        fontFamily = MonoFont,
                        fontSize = 11.sp,
                        color = AegisColors.TextSecondary,
                        letterSpacing = 1.sp
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(SearchEngineOption.entries) { engine ->
                            val selected = engine == state.selectedEngine
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (selected) AegisColors.EmeraldDim else AegisColors.SurfaceElevated
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) AegisColors.EmeraldPrimary else AegisColors.BorderSubtle,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onSelectEngine(engine) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = engine.label,
                                    fontFamily = MonoFont,
                                    fontSize = 11.sp,
                                    color = if (selected) AegisColors.EmeraldBright else AegisColors.TextSecondary
                                )
                            }
                        }
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(SearchCategory.entries) { cat ->
                            val selected = cat == state.selectedCategory
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selected) AegisColors.SurfaceElevated else Color.Transparent)
                                    .border(
                                        1.dp,
                                        if (selected) AegisColors.EmeraldBright else AegisColors.BorderSubtle,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { onSelectCategory(cat) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = cat.displayName,
                                    fontFamily = MonoFont,
                                    fontSize = 10.sp,
                                    color = if (selected) AegisColors.TextPrimary else AegisColors.TextMuted
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = quickQuery,
                            onValueChange = { quickQuery = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = {
                                Text(
                                    text = "Query oder URL...",
                                    fontFamily = MonoFont,
                                    fontSize = 12.sp,
                                    color = AegisColors.TextMuted
                                )
                            },
                            textStyle = TextStyle(
                                fontFamily = MonoFont,
                                fontSize = 13.sp,
                                color = AegisColors.TextPrimary
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AegisColors.EmeraldPrimary,
                                unfocusedBorderColor = AegisColors.BorderSubtle,
                                cursorColor = AegisColors.EmeraldBright
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    if (quickQuery.isNotBlank()) onSubmitQuery(quickQuery)
                                }
                            )
                        )
                        Button(
                            onClick = { if (quickQuery.isNotBlank()) onSubmitQuery(quickQuery) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AegisColors.EmeraldPrimary,
                                contentColor = AegisColors.OledBlack
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = "Suchen",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        if (state.nativeSearchResults.isNotEmpty()) {
            item {
                Text(
                    text = "RESULTS // ${state.selectedEngine.label}",
                    fontFamily = MonoFont,
                    fontSize = 11.sp,
                    color = AegisColors.EmeraldBright,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            items(state.nativeSearchResults, key = { it.url }) { item ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenBookmark(stripTrackingQueryParams(item.url)) },
                    shape = RoundedCornerShape(12.dp),
                    color = AegisColors.SurfaceCard,
                    border = BorderStroke(1.dp, AegisColors.BorderSubtle)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = formatIdleDomainLabel(item.url),
                            fontFamily = MonoFont,
                            fontSize = 10.sp,
                            color = AegisColors.EmeraldBright,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.title,
                            fontFamily = MonoFont,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AegisColors.TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.snippet.isNotBlank()) {
                            Text(
                                text = item.snippet,
                                fontSize = 12.sp,
                                color = AegisColors.TextSecondary,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = AegisColors.SurfaceCard,
                border = BorderStroke(1.dp, AegisColors.BorderSubtle)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "LOCAL BOOKMARKS (ZERO TELEMETRY)",
                        fontFamily = MonoFont,
                        fontSize = 11.sp,
                        color = AegisColors.TextSecondary,
                        letterSpacing = 1.sp
                    )

                    if (state.localBookmarks.isEmpty()) {
                        Text(
                            text = "Keine lokalen Lesezeichen gespeichert.",
                            fontFamily = MonoFont,
                            fontSize = 12.sp,
                            color = AegisColors.TextMuted
                        )
                    } else {
                        state.localBookmarks.forEach { bm ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AegisColors.SurfaceElevated)
                                    .clickable { onOpenBookmark(bm.url) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = bm.title,
                                        fontFamily = MonoFont,
                                        fontSize = 12.sp,
                                        color = AegisColors.TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = formatIdleDomainLabel(bm.url),
                                        fontFamily = MonoFont,
                                        fontSize = 10.sp,
                                        color = AegisColors.EmeraldBright,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = "Lesezeichen entfernen",
                                    tint = AegisColors.TextMuted,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable { onDeleteBookmark(bm.url) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RuntimeStatusRow(
    label: String,
    value: String,
    active: Boolean,
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable { onClick() } else Modifier
            )
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontFamily = MonoFont,
            fontSize = 11.sp,
            color = AegisColors.TextSecondary,
            letterSpacing = 0.6.sp
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = value,
                fontFamily = MonoFont,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (active) AegisColors.EmeraldBright else AegisColors.TextMuted
            )
            if (onClick != null) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = if (active) AegisColors.EmeraldBright else AegisColors.TextMuted,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
private fun AddressCapsuleBar(
    currentUrl: String,
    addressInput: String,
    isEditing: Boolean,
    shieldEnabled: Boolean,
    blockedCount: Int,
    tabCount: Int,
    activeTabNumber: Int,
    isBottomBar: Boolean,
    onInputChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    onShieldClick: () -> Unit,
    onToggleBarPosition: () -> Unit,
    onSwipeTabStep: (Int) -> Unit,
    onCloseCurrentTab: () -> Unit
) {
    val displayText = if (isEditing) addressInput else formatIdleDomainLabel(currentUrl)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(14.dp),
        color = AegisColors.SurfaceElevated,
        border = BorderStroke(
            1.dp,
            if (shieldEnabled) AegisColors.EmeraldPrimary.copy(alpha = 0.55f) else AegisColors.BorderSubtle
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Grünes Schild-Badge mit Zähler (0 / 1 / N) -> öffnet Shield-Overlay
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (shieldEnabled) AegisColors.EmeraldDim else Color(0xFF1F1617))
                    .border(
                        1.dp,
                        if (shieldEnabled) AegisColors.EmeraldPrimary else AegisColors.DangerRed.copy(alpha = 0.6f),
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onShieldClick() }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = "Tracker Shield",
                    tint = if (shieldEnabled) AegisColors.EmeraldBright else AegisColors.DangerRed,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = blockedCount.toString(),
                    fontFamily = MonoFont,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (shieldEnabled) AegisColors.EmeraldBright else AegisColors.DangerRed
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            BasicTextField(
                value = displayText,
                onValueChange = { next ->
                    if (!isEditing) onFocusChanged(true)
                    onInputChange(next)
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp),
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = MonoFont,
                    fontSize = 13.sp,
                    color = if (isEditing) AegisColors.TextPrimary else AegisColors.EmeraldBright
                ),
                cursorBrush = SolidColor(AegisColors.EmeraldBright),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                decorationBox = { innerTextField ->
                    if (displayText.isEmpty()) {
                        Text(
                            text = "URL oder Suchbegriff...",
                            fontFamily = MonoFont,
                            fontSize = 13.sp,
                            color = AegisColors.TextMuted
                        )
                    }
                    innerTextField()
                }
            )

            // Fester Mindestabstand zum X-Button
            if (isEditing || currentUrl.isNotBlank() && currentUrl != "about:blank") {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Eingabe leeren / Tab schließen",
                    tint = AegisColors.TextSecondary,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable {
                            if (isEditing) onClear() else onCloseCurrentTab()
                        }
                )
                Spacer(modifier = Modifier.width(10.dp))
            }

            // Tab-Counter mit Wischgeste links/rechts zum Wechseln
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(AegisColors.SurfaceCard)
                    .border(1.dp, AegisColors.BorderSubtle, RoundedCornerShape(7.dp))
                    .pointerInput(tabCount) {
                        var acc = 0f
                        detectHorizontalDragGestures(
                            onDragEnd = { acc = 0f },
                            onHorizontalDrag = { _, delta ->
                                acc += delta
                                if (acc > 55f) {
                                    acc = 0f
                                    onSwipeTabStep(-1)
                                } else if (acc < -55f) {
                                    acc = 0f
                                    onSwipeTabStep(1)
                                }
                            }
                        )
                    }
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$activeTabNumber/$tabCount",
                    fontFamily = MonoFont,
                    fontSize = 11.sp,
                    color = AegisColors.TextPrimary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Outlined.SwapVert,
                contentDescription = if (isBottomBar) "Leiste nach oben" else "Leiste nach unten",
                tint = AegisColors.TextMuted,
                modifier = Modifier
                    .size(17.dp)
                    .clickable { onToggleBarPosition() }
            )
        }
    }
}

@Composable
private fun ShieldOverlayContent(
    globalShieldEnabled: Boolean,
    siteShieldEnabled: Boolean,
    currentHost: String,
    blockedCount: Int,
    blockedDomains: List<String>,
    onToggleGlobal: (Boolean) -> Unit,
    onToggleSite: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "AEGIS SHIELD // OVERLAY",
            fontFamily = MonoFont,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = AegisColors.EmeraldBright
        )
        Text(
            text = "Geblockt auf $currentHost: $blockedCount",
            fontFamily = MonoFont,
            fontSize = 12.sp,
            color = AegisColors.TextSecondary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Site-Schutz ($currentHost)",
                fontFamily = MonoFont,
                fontSize = 12.sp,
                color = AegisColors.TextPrimary
            )
            Switch(
                checked = siteShieldEnabled,
                onCheckedChange = onToggleSite,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = AegisColors.OledBlack,
                    checkedTrackColor = AegisColors.EmeraldPrimary
                )
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Globaler Tracker-Blocker",
                fontFamily = MonoFont,
                fontSize = 12.sp,
                color = AegisColors.TextPrimary
            )
            Switch(
                checked = globalShieldEnabled,
                onCheckedChange = onToggleGlobal,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = AegisColors.OledBlack,
                    checkedTrackColor = AegisColors.EmeraldPrimary
                )
            )
        }

        if (blockedDomains.isNotEmpty()) {
            HorizontalDivider(color = AegisColors.BorderSubtle)
            Text(
                text = "GEBLOCKTE DOMAINS",
                fontFamily = MonoFont,
                fontSize = 11.sp,
                color = AegisColors.TextMuted
            )
            blockedDomains.distinct().take(12).forEach { domain ->
                Text(
                    text = "• $domain",
                    fontFamily = MonoFont,
                    fontSize = 11.sp,
                    color = AegisColors.EmeraldBright
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

private val DefaultTrackerHosts = setOf(
    "doubleclick.net", "google-analytics.com", "googletagmanager.com",
    "facebook.net", "fbevents.com", "hotjar.com", "clarity.ms",
    "segment.io", "mixpanel.com", "appsflyer.com", "adjust.com"
)

fun isKnownTrackerHost(host: String): Boolean {
    val clean = host.lowercase().removePrefix("www.")
    if (clean.isBlank()) return false
    return DefaultTrackerHosts.any { clean == it || clean.endsWith(".$it") }
}
