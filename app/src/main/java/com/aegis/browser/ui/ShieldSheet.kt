package com.aegis.browser.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aegis.browser.browser.UrlValidator
import com.aegis.browser.ui.theme.*
import com.aegis.browser.viewmodel.BrowserUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShieldSheet(
    state: BrowserUiState,
    onDismiss: () -> Unit,
    onToggleGlobal: () -> Unit,
    onToggleSite: () -> Unit,
    onAddBlockDomain: (String) -> Unit,
    onRemoveBlockDomain: (String) -> Unit
) {
    var domainInput by remember { mutableStateOf("") }
    val currentHost = UrlValidator.extractHost(state.activeTab.url)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = if (state.globalShieldEnabled) Icons.Default.VerifiedUser else Icons.Default.GppBad,
                        contentDescription = null,
                        tint = if (state.globalShieldEnabled) ShieldActiveEmerald else ShieldWarningAmber
                    )
                    Text(
                        text = "TRACKER & AD SHIELD",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 1.sp,
                        color = TextPrimary
                    )
                }
                Switch(
                    checked = state.globalShieldEnabled,
                    onCheckedChange = { onToggleGlobal() }
                )
            }

            // Metrics bar
            Surface(
                color = SurfaceElevated,
                border = BorderStroke(1.dp, BorderHairline),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("SESSION BLOCKED", fontSize = 10.sp, color = TextSecondary)
                        Text(
                            text = state.totalBlockedRequests.toString(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = ShieldActiveEmerald
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("CURRENT SITE", fontSize = 10.sp, color = TextSecondary)
                        Text(
                            text = state.currentSiteBlockedRequests.toString(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary
                        )
                    }
                }
            }

            // Per-site toggle
            if (currentHost.isNotEmpty()) {
                Surface(
                    color = SurfaceElevated,
                    border = BorderStroke(1.dp, BorderHairline),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(currentHost, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
                            Text(
                                text = if (state.isCurrentSiteProtected) "Blocking active on this domain" else "Allowlisted (shield paused)",
                                fontSize = 11.sp,
                                color = if (state.isCurrentSiteProtected) ShieldActiveEmerald else ShieldWarningAmber
                            )
                        }
                        Switch(
                            checked = state.isCurrentSiteProtected,
                            onCheckedChange = { onToggleSite() }
                        )
                    }
                }
            }

            // Custom domain blocklist
            Text("CUSTOM DOMAIN BLOCKLIST", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = domainInput,
                    onValueChange = { domainInput = it },
                    placeholder = { Text("tracker.example.com", fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalButton(
                    onClick = {
                        if (domainInput.isNotBlank()) {
                            onAddBlockDomain(domainInput)
                            domainInput = ""
                        }
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add domain")
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(state.customBlocklist.toList()) { domain ->
                    Surface(
                        color = SurfaceElevated,
                        border = BorderStroke(1.dp, BorderHairline),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(domain, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextPrimary)
                            IconButton(onClick = { onRemoveBlockDomain(domain) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = TextSecondary)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
