package com.aegis.browser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aegis.browser.ui.AegisColors
import com.aegis.browser.ui.AegisRuntimeScreen
import com.aegis.browser.ui.theme.AegisTheme
import com.aegis.browser.viewmodel.BrowserViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: BrowserViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        intent?.dataString?.let { incoming ->
            if (incoming.isNotBlank()) {
                viewModel.submitAddressOrQuery(incoming)
            }
        }

        setContent {
            AegisTheme {
                val state by viewModel.uiState.collectAsState()
                var showVaultSheet by remember { mutableStateOf(false) }
                var showShieldSheet by remember { mutableStateOf(false) }

                AegisRuntimeScreen(
                    state = state,
                    onAddressInputChange = viewModel::onAddressInputChange,
                    onEditingChange = viewModel::setEditingAddress,
                    onSubmitAddress = viewModel::submitAddressOrQuery,
                    onClearAddress = viewModel::clearAddressInput,
                    onToggleBarPosition = viewModel::toggleBarPosition,
                    onSelectEngine = viewModel::selectEngine,
                    onSelectCategory = viewModel::selectCategory,
                    onOpenVaultSheet = { showVaultSheet = !showVaultSheet },
                    onOpenShieldOverlay = { showShieldSheet = !showShieldSheet },
                    onToggleGlobalShield = viewModel::setGlobalShieldEnabled,
                    onToggleSiteShield = viewModel::setCurrentSiteShieldEnabled,
                    onSelectTab = viewModel::selectTab,
                    onCloseTab = viewModel::closeTab,
                    onNewTab = { viewModel.newTab("about:blank") },
                    onPageUrlChanged = viewModel::onPageUrlChanged,
                    onPageTitleChanged = viewModel::onPageTitleChanged,
                    onProgressChanged = viewModel::onProgressChanged,
                    onRequestBlocked = viewModel::onRequestBlocked,
                    onOpenLocalBookmark = viewModel::openBookmark,
                    onDeleteLocalBookmark = viewModel::deleteLocalBookmark,
                    vaultSheetContent = if (showVaultSheet) {
                        {
                            VaultBottomSheetBody(
                                isUnlocked = state.isVaultUnlocked,
                                keyLabel = state.vaultKeyLabel,
                                onUnlock = { pass, done -> viewModel.unlockOrInitVault(pass, done) },
                                onLock = {
                                    viewModel.lockVault()
                                    showVaultSheet = false
                                }
                            )
                        }
                    } else null,
                    shieldSheetContent = if (showShieldSheet) {
                        { /* ShieldOverlayContent wird in AegisRuntimeScreen gerendert */ }
                    } else null
                )
            }
        }
    }
}

@Composable
private fun VaultBottomSheetBody(
    isUnlocked: Boolean,
    keyLabel: String,
    onUnlock: (String, (Boolean, String?) -> Unit) -> Unit,
    onLock: () -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val mono = FontFamily.Monospace

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "E2EE STORAGE // X25519",
            fontFamily = mono,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = AegisColors.EmeraldBright
        )
        Text(
            text = "Status: ${if (isUnlocked) "UNLOCKED" else "LOCKED"} ($keyLabel)",
            fontFamily = mono,
            fontSize = 11.sp,
            color = AegisColors.TextSecondary
        )

        if (!isUnlocked) {
            OutlinedTextField(
                value = passphrase,
                onValueChange = {
                    passphrase = it
                    errorMsg = null
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                placeholder = {
                    Text(
                        text = "Passphrase (min. 8 Zeichen)...",
                        fontFamily = mono,
                        fontSize = 12.sp,
                        color = AegisColors.TextMuted
                    )
                },
                textStyle = TextStyle(fontFamily = mono, fontSize = 13.sp, color = AegisColors.TextPrimary),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AegisColors.EmeraldPrimary,
                    unfocusedBorderColor = AegisColors.BorderSubtle
                )
            )

            if (errorMsg != null) {
                Text(
                    text = errorMsg.orEmpty(),
                    fontFamily = mono,
                    fontSize = 11.sp,
                    color = AegisColors.DangerRed
                )
            }

            Button(
                onClick = {
                    onUnlock(passphrase) { ok, err ->
                        if (ok) {
                            passphrase = ""
                            errorMsg = null
                        } else {
                            errorMsg = err ?: "Fehler beim Entsperren"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AegisColors.EmeraldPrimary,
                    contentColor = AegisColors.OledBlack
                )
            ) {
                Text("TRESOR ENTSPERREN / INITIALISIEREN", fontFamily = mono, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onLock,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("TRESOR SPERREN", fontFamily = mono, fontSize = 12.sp, color = AegisColors.EmeraldBright)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
