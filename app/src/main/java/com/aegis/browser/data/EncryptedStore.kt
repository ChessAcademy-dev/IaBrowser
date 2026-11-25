package com.aegis.browser.data

import android.content.Context
import com.aegis.browser.crypto.CryptoEngine
import com.aegis.browser.crypto.KeyVault
import com.aegis.browser.model.EncryptedEnvelope
import com.aegis.browser.model.VaultPayload
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.PrivateKey
import java.security.PublicKey

/**
 * Stores all user state (bookmarks, history, tabs, notes, filter rules) strictly as an
 * authenticated AES-256-GCM + X25519 ciphertext envelope on disk.
 * Also supports exporting/importing raw ciphertext JSON for zero-knowledge remote sync.
 */
class EncryptedStore(context: Context) {

    private val vaultFile = File(context.filesDir, "aegis_vault.enc.json")
    private val keyVault = KeyVault(context)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    fun vaultExists(): Boolean = vaultFile.exists()

    fun getPublicKey(): PublicKey = keyVault.getOrCreatePublicKey()

    fun getKeyFingerprint(): String = CryptoEngine.publicKeyFingerprint(getPublicKey())

    fun unlockAndLoad(passphrase: CharArray): VaultPayload {
        val privateKey: PrivateKey = keyVault.loadPrivateKey()
        if (!vaultFile.exists()) {
            return VaultPayload()
        }
        val envelope = json.decodeFromString<EncryptedEnvelope>(vaultFile.readText())
        val decryptedBytes = CryptoEngine.decrypt(envelope, passphrase, privateKey)
        return try {
            val plaintextJson = decryptedBytes.toString(Charsets.UTF_8)
            json.decodeFromString<VaultPayload>(plaintextJson)
        } finally {
            decryptedBytes.fill(0)
        }
    }

    fun saveEncrypted(payload: VaultPayload, passphrase: CharArray) {
        val publicKey = keyVault.getOrCreatePublicKey()
        val plaintextBytes = json.encodeToString(payload).toByteArray(Charsets.UTF_8)
        try {
            val envelope = CryptoEngine.encrypt(plaintextBytes, passphrase, publicKey)
            vaultFile.writeText(json.encodeToString(envelope))
        } finally {
            plaintextBytes.fill(0)
        }
    }

    /**
     * Exports ciphertext-only envelope for external zero-knowledge sync/backup.
     * Plaintext and private keys never leave the device.
     */
    fun exportCiphertextEnvelope(): String? {
        return if (vaultFile.exists()) vaultFile.readText() else null
    }

    fun importCiphertextEnvelope(envelopeJson: String, passphrase: CharArray): VaultPayload {
        val envelope = json.decodeFromString<EncryptedEnvelope>(envelopeJson)
        val privateKey = keyVault.loadPrivateKey()
        val decryptedBytes = CryptoEngine.decrypt(envelope, passphrase, privateKey)
        return try {
            val payload = json.decodeFromString<VaultPayload>(decryptedBytes.toString(Charsets.UTF_8))
            vaultFile.writeText(envelopeJson)
            payload
        } finally {
            decryptedBytes.fill(0)
        }
    }
}
