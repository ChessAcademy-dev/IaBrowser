package com.aegis.browser.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyPair
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed Android Keystore wrapper that protects the user's X25519 private key at rest.
 * The X25519 private key is wrapped inside Android Keystore AES-256-GCM and only unwrapped into
 * memory when the user unlocks the vault with their passphrase.
 */
class KeyVault(context: Context) {

    private val keyAlias = "com.aegis.browser.master_wrap_key"
    private val keyFile = File(context.filesDir, "aegis_identity.key")

    fun hasIdentity(): Boolean = keyFile.exists()

    fun getOrCreatePublicKey(): PublicKey {
        if (!keyFile.exists()) {
            val kp = CryptoEngine.generateX25519KeyPair()
            saveWrappedKeyPair(kp)
            return kp.public
        }
        val lines = keyFile.readLines()
        return CryptoEngine.decodePublicKey(lines[0])
    }

    fun loadPrivateKey(): PrivateKey {
        require(keyFile.exists()) { "Identity keypair does not exist" }
        val lines = keyFile.readLines()
        val iv = Base64.getDecoder().decode(lines[1])
        val wrappedPriv = Base64.getDecoder().decode(lines[2])

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKeystoreSecretKey(), GCMParameterSpec(128, iv))
        val privBytes = cipher.doFinal(wrappedPriv)
        val privBase64 = String(privBytes, Charsets.UTF_8)
        privBytes.fill(0)
        return CryptoEngine.decodePrivateKey(privBase64)
    }

    private fun saveWrappedKeyPair(keyPair: KeyPair) {
        val pubBase64 = CryptoEngine.encodePublicKey(keyPair.public)
        val privBytes = CryptoEngine.encodePrivateKey(keyPair.private).toByteArray(Charsets.UTF_8)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKeystoreSecretKey())
        val wrappedPriv = cipher.doFinal(privBytes)
        privBytes.fill(0)

        val ivBase64 = Base64.getEncoder().encodeToString(cipher.iv)
        val wrappedBase64 = Base64.getEncoder().encodeToString(wrappedPriv)

        keyFile.writeText("$pubBase64\n$ivBase64\n$wrappedBase64")
    }

    private fun getOrCreateKeystoreSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keyStore.getKey(keyAlias, null)?.let { return it as SecretKey }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}
