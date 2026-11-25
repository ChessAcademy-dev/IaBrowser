package com.aegis.browser.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aegis.browser.ui.theme.*
import com.aegis.browser.viewmodel.BrowserUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultSheet(
    state: BrowserUiState,
    onDismiss: () -> Unit,
    onUnlock: (String, (Boolean, String?) -> Unit) -> Unit,
    onLock: () -> Unit,
    onNavigateUrl: (String) -> Unit,
    onAddNote: (String, String) -> Unit,
    onDeleteNote: (String) -> Unit,
    onExportCiphertext: () -> String?,
    onImportCiphertext: (String, (Boolean, String?) -> Unit) -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var selectedSection by remember { mutableIntStateOf(0) }
    var noteTitle by remember { mutableStateOf("") }
    var noteBody by remember { mutableStateOf("") }
    var syncPayloadInput by remember { mutableStateOf("") }
    var syncStatus by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current

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
                        imageVector = if (state.isVaultUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (state.isVaultUnlocked) ShieldActiveEmerald else ShieldWarningAmber
                    )
                    Column {
                        Text(
                            text = if (state.isVaultUnlocked) "E2EE VAULT UNLOCKED" else "E2EE VAULT LOCKED",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            letterSpacing = 0.8.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "X25519 • ${state.keyFingerprint}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }
                if (state.isVaultUnlocked) {
                    OutlinedButton(
                        onClick = onLock,
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("LOCK", fontSize = 11.sp)
                    }
                }
            }

            if (!state.isVaultUnlocked) {
                Text(
                    text = if (state.vaultExistsOnDisk) {
                        "Enter passphrase to derive PBKDF2-SHA256 + X25519 key and decrypt bookmarks, history, and notes."
                    } else {
                        "Create a vault passphrase (min 8 chars). All bookmarks, tabs, history, and notes are encrypted locally with AES-256-GCM."
                    },
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it; errorMsg = null },
                    label = { Text("Vault Passphrase") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                errorMsg?.let {
                    Text(it, color = DangerCrimson, fontSize = 12.sp)
                }
                Button(
                    onClick = {
                        onUnlock(passphrase) { ok, err ->
                            if (ok) passphrase = "" else errorMsg = err
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (state.vaultExistsOnDisk) "DECRYPT & UNLOCK" else "INITIALIZE ENCRYPTED VAULT")
                }
            } else {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf("Bookmarks", "History", "Notes", "Sync").forEachIndexed { idx, label ->
                        SegmentedButton(
                            selected = selectedSection == idx,
                            onClick = { selectedSection = idx },
                            shape = SegmentedButtonDefaults.itemShape(idx, 4)
                        ) {
                            Text(label, fontSize = 11.sp)
                        }
                    }
                }

                when (selectedSection) {
                    0 -> {
                        LazyColumn(modifier = Modifier.heightIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(state.bookmarks) { bm ->
                                Surface(
                                    color = SurfaceElevated,
                                    border = BorderStroke(1.dp, BorderHairline),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onNavigateUrl(bm.url); onDismiss() }
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(bm.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary, maxLines = 1)
                                        Text(bm.url, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        LazyColumn(modifier = Modifier.heightIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(state.history) { item ->
                                Surface(
                                    color = SurfaceElevated,
                                    border = BorderStroke(1.dp, BorderHairline),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onNavigateUrl(item.url); onDismiss() }
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(item.title, fontSize = 13.sp, color = TextPrimary, maxLines = 1)
                                        Text(item.url, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        OutlinedTextField(
                            value = noteTitle,
                            onValueChange = { noteTitle = it },
                            placeholder = { Text("Encrypted note title", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = noteBody,
                                onValueChange = { noteBody = it },
                                placeholder = { Text("Secret note content", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    onAddNote(noteTitle, noteBody)
                                    noteTitle = ""
                                    noteBody = ""
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Save")
                            }
                        }
                        LazyColumn(modifier = Modifier.heightIn(max = 180.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(state.notes) { note ->
                                Surface(
                                    color = SurfaceElevated,
                                    border = BorderStroke(1.dp, BorderHairline),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(note.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                                            Text(note.body, fontSize = 12.sp, color = TextSecondary)
                                        }
                                        IconButton(onClick = { onDeleteNote(note.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete note", tint = TextSecondary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    3 -> {
                        Text(
                            "Zero-Knowledge Ciphertext Sync: Export/Import raw AES-256-GCM + X25519 JSON envelope. Keys never leave this device.",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                        FilledTonalButton(
                            onClick = {
                                val cipher = onExportCiphertext()
                                if (cipher != null) {
                                    clipboard.setText(AnnotatedString(cipher))
                                    syncStatus = "Ciphertext envelope copied to clipboard (${cipher.length} bytes)"
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("COPY ENCRYPTED SYNC PAYLOAD")
                        }
                        OutlinedTextField(
                            value = syncPayloadInput,
                            onValueChange = { syncPayloadInput = it },
                            placeholder = { Text("Paste ciphertext JSON envelope to import", fontSize = 11.sp) },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                onImportCiphertext(syncPayloadInput) { ok, err ->
                                    syncStatus = if (ok) "Ciphertext verified & decrypted" else err
                                    if (ok) syncPayloadInput = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("IMPORT & VERIFY CIPHERTEXT")
                        }
                        syncStatus?.let {
                            Text(it, fontSize = 11.sp, color = ShieldActiveEmerald)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
