package com.aegis.browser.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.aegis.browser.browser.SafeWebViewClient
import com.aegis.browser.browser.UrlValidator
import com.aegis.browser.ui.theme.*
import com.aegis.browser.viewmodel.BrowserViewModel

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(viewModel: BrowserViewModel) {
    val state by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    var showShieldSheet by remember { mutableStateOf(false) }
    var showVaultSheet by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val activeUrl = state.activeTab.url
    val isBookmarked = state.bookmarks.any { it.url == activeUrl }

    BackHandler(enabled = state.canGoBack) {
        webViewRef?.goBack()
    }

    Scaffold(
        containerColor = ObsidianBg,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark)
                    .statusBarsPadding()
            ) {
                // Compact Tab Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(state.tabs) { index, tab ->
                            val selected = index == state.activeTabIndex
                            Surface(
                                color = if (selected) SurfaceElevated else ObsidianBg,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) ShieldActiveEmerald else BorderHairline
                                ),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.clickable { viewModel.selectTab(index) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = tab.title.ifBlank { "Tab ${index + 1}" },
                                        fontSize = 11.sp,
                                        color = if (selected) TextPrimary else TextSecondary,
                                        maxLines = 1,
                                        modifier = Modifier.widthIn(max = 110.dp)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close tab",
                                        tint = TextSecondary,
                                        modifier = Modifier
                                            .size(13.dp)
                                            .clickable { viewModel.closeTab(index) }
                                    )
                                }
                            }
                        }
                    }
                    IconButton(
                        onClick = { viewModel.addTab() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New tab", tint = TextPrimary, modifier = Modifier.size(18.dp))
                    }
                }

                // High-Contrast Address Bar + Shield & E2EE Status Indicators
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Shield Status Badge (Opens Ad/Tracker Blocker Sheet)
                    Surface(
                        color = SurfaceElevated,
                        border = BorderStroke(
                            1.dp,
                            if (state.globalShieldEnabled && state.isCurrentSiteProtected) ShieldActiveEmerald else ShieldWarningAmber
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { showShieldSheet = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Ad & Tracker Shield",
                                tint = if (state.globalShieldEnabled && state.isCurrentSiteProtected) ShieldActiveEmerald else ShieldWarningAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = state.totalBlockedRequests.toString(),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = TextPrimary
                            )
                        }
                    }

                    // Omnibox Input
                    Surface(
                        color = ObsidianBg,
                        border = BorderStroke(1.dp, BorderHairline),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicTextField(
                                value = state.addressBarInput,
                                onValueChange = viewModel::onAddressInputChange,
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                cursorBrush = SolidColor(ShieldActiveEmerald),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(
                                    onGo = {
                                        focusManager.clearFocus()
                                        viewModel.navigateToInput(state.addressBarInput)
                                    }
                                ),
                                decorationBox = { innerTextField ->
                                    if (state.addressBarInput.isEmpty()) {
                                        Text(
                                            text = "Search or enter URL...",
                                            color = TextSecondary,
                                            fontSize = 13.sp
                                        )
                                    }
                                    innerTextField()
                                },
                                modifier = Modifier.weight(1f)
                            )
                            if (state.addressBarInput.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear URL",
                                    tint = TextSecondary,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { viewModel.onAddressInputChange("") }
                                )
                            }
                        }
                    }

                    // E2EE Lock Status Badge (Opens Vault Sheet)
                    Surface(
                        color = SurfaceElevated,
                        border = BorderStroke(
                            1.dp,
                            if (state.isVaultUnlocked) ShieldActiveEmerald else BorderHairline
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { showVaultSheet = true }
                    ) {
                        Box(modifier = Modifier.padding(8.dp)) {
                            Icon(
                                imageVector = if (state.isVaultUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                contentDescription = "E2EE Vault",
                                tint = if (state.isVaultUnlocked) ShieldActiveEmerald else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                if (state.isLoading) {
                    LinearProgressIndicator(
                        progress = { state.pageProgress },
                        color = ShieldActiveEmerald,
                        trackColor = ObsidianBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                    )
                } else {
                    HorizontalDivider(thickness = 1.dp, color = BorderHairline)
                }

                state.statusBanner?.let { banner ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceElevated)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(banner, color = ShieldWarningAmber, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = TextSecondary,
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { viewModel.clearStatusBanner() }
                        )
                    }
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark)
                    .navigationBarsPadding()
            ) {
                HorizontalDivider(thickness = 1.dp, color = BorderHairline)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { webViewRef?.goBack() }, enabled = state.canGoBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (state.canGoBack) TextPrimary else BorderHairline
                        )
                    }
                    IconButton(onClick = { webViewRef?.goForward() }, enabled = state.canGoForward) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Forward",
                            tint = if (state.canGoForward) TextPrimary else BorderHairline
                        )
                    }
                    IconButton(onClick = { webViewRef?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = TextPrimary)
                    }
                    IconButton(onClick = { viewModel.toggleBookmarkCurrentPage() }) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint = if (isBookmarked) ShieldActiveEmerald else TextPrimary
                        )
                    }
                    IconButton(onClick = { showVaultSheet = true }) {
                        Icon(Icons.Default.VpnKey, contentDescription = "Encrypted Vault", tint = TextPrimary)
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(ObsidianBg)
        ) {
            if (activeUrl == "about:blank") {
                // Utilitarian, zero-fluff new-tab overview
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "AEGIS // PRIVACY RUNTIME",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        letterSpacing = 1.2.sp,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        color = SurfaceDark,
                        border = BorderStroke(1.dp, BorderHairline),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatusLine(
                                label = "TRACKER SHIELD",
                                value = if (state.globalShieldEnabled) "ACTIVE (${state.totalBlockedRequests} BLOCKED)" else "DISABLED",
                                active = state.globalShieldEnabled
                            )
                            StatusLine(
                                label = "E2EE STORAGE",
                                value = if (state.isVaultUnlocked) "UNLOCKED (AES-256-GCM)" else "LOCKED (X25519)",
                                active = state.isVaultUnlocked
                            )
                            StatusLine(
                                label = "3RD-PARTY COOKIES",
                                value = "BLOCKED",
                                active = true
                            )
                            StatusLine(
                                label = "SAFE BROWSING",
                                value = "ENABLED",
                                active = true
                            )
                        }
                    }
                }
            } else {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = false
                                allowFileAccess = false
                                allowContentAccess = false
                                setGeolocationEnabled(false)
                                setSupportMultipleWindows(false) // Blocks popup windows
                                javaScriptCanOpenWindowsAutomatically = false
                                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                safeBrowsingEnabled = true
                            }
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

                            webViewClient = SafeWebViewClient(
                                blocker = viewModel.adBlocker,
                                getCurrentPageHost = { UrlValidator.extractHost(viewModel.uiState.value.activeTab.url) },
                                onUrlChanged = { viewModel.onPageUrlChanged(it) },
                                onTitleChanged = { viewModel.onPageTitleChanged(it) },
                                onBlockedEvent = { viewModel.onRequestBlocked() },
                                onSecurityBlock = { viewModel.onSecurityBlock(it) }
                            )
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    viewModel.onNavigationStateChanged(
                                        canBack = view?.canGoBack() == true,
                                        canForward = view?.canGoForward() == true,
                                        progress = newProgress
                                    )
                                }
                            }
                            webViewRef = this
                        }
                    },
                    update = { webView ->
                        if (webView.url != activeUrl && activeUrl != "about:blank") {
                            webView.loadUrl(activeUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    if (showShieldSheet) {
        ShieldSheet(
            state = state,
            onDismiss = { showShieldSheet = false },
            onToggleGlobal = viewModel::toggleGlobalShield,
            onToggleSite = viewModel::toggleCurrentSiteShield,
            onAddBlockDomain = viewModel::addCustomBlockDomain,
            onRemoveBlockDomain = viewModel::removeCustomBlockDomain
        )
    }

    if (showVaultSheet) {
        VaultSheet(
            state = state,
            onDismiss = { showVaultSheet = false },
            onUnlock = viewModel::unlockOrInitializeVault,
            onLock = viewModel::lockVault,
            onNavigateUrl = viewModel::navigateToInput,
            onAddNote = viewModel::addEncryptedNote,
            onDeleteNote = viewModel::deleteEncryptedNote,
            onExportCiphertext = viewModel::exportCiphertextJson,
            onImportCiphertext = viewModel::importCiphertextJson
        )
    }
}

@Composable
private fun StatusLine(label: String, value: String, active: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextSecondary)
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = if (active) ShieldActiveEmerald else ShieldWarningAmber
        )
    }
}
