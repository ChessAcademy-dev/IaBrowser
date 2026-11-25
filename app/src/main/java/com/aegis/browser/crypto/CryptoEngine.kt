package com.aegis.browser.crypto

import com.aegis.browser.model.EncryptedEnvelope
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.NamedParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Real client-side E2EE engine:
 * 1. Derives a 256-bit passphrase key via PBKDF2WithHmacSHA256 (310,000 iterations, OWASP recommended).
 * 2. Generates an ephemeral X25519 keypair per encryption envelope and computes ECDH shared secret
 *    with the user's static X25519 public key (providing forward-secure envelope encryption for sync).
 * 3. Combines ECDH shared secret + PBKDF2 key via HKDF-SHA256 into a 256-bit AES-GCM content key.
 * 4. Encrypts plaintext with AES-256-GCM (96-bit random IV, 128-bit auth tag, AAD header binding).
 */
object CryptoEngine {

    private const val KDF_ITERATIONS = 310_000
    private const val SALT_BYTES = 16
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val AES_KEY_BITS = 256
    private val AAD_CONTEXT = "AEGIS-E2EE-V1".toByteArray(StandardCharsets.UTF_8)

    private val secureRandom = SecureRandom()

    fun generateX25519KeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("X25519")
        kpg.initialize(NamedParameterSpec.X25519, secureRandom)
        return kpg.generateKeyPair()
    }

    fun encodePublicKey(publicKey: PublicKey): String =
        Base64.getEncoder().encodeToString(publicKey.encoded)

    fun encodePrivateKey(privateKey: PrivateKey): String =
        Base64.getEncoder().encodeToString(privateKey.encoded)

    fun decodePublicKey(base64: String): PublicKey {
        val bytes = Base64.getDecoder().decode(base64)
        return KeyFactory.getInstance("X25519").generatePublic(X509EncodedKeySpec(bytes))
    }

    fun decodePrivateKey(base64: String): PrivateKey {
        val bytes = Base64.getDecoder().decode(base64)
        return KeyFactory.getInstance("X25519").generatePrivate(PKCS8EncodedKeySpec(bytes))
    }

    fun publicKeyFingerprint(publicKey: PublicKey): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(publicKey.encoded)
        return digest.take(8).joinToString(":") { "%02X".format(it) }
    }

    fun encrypt(
        plaintext: ByteArray,
        passphrase: CharArray,
        recipientPublicKey: PublicKey
    ): EncryptedEnvelope {
        val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(GCM_IV_BYTES).also { secureRandom.nextBytes(it) }

        // 1. Ephemeral X25519 ECDH key agreement
        val ephemeralKeyPair = generateX25519KeyPair()
        val ecdhSecret = computeX25519SharedSecret(ephemeralKeyPair.private, recipientPublicKey)

        // 2. Passphrase stretching via PBKDF2-HMAC-SHA256
        val passphraseKey = derivePbkdf2Key(passphrase, salt, KDF_ITERATIONS)

        // 3. Combine both secrets via HKDF-SHA256
        val hybridIkm = ByteBuffer.allocate(ecdhSecret.size + passphraseKey.size)
            .put(ecdhSecret)
            .put(passphraseKey)
            .array()
        val aesKeyBytes = hkdfSha256(ikm = hybridIkm, salt = salt, info = AAD_CONTEXT, length = 32)
        val aesKey = SecretKeySpec(aesKeyBytes, "AES")

        // 4. Encrypt with AES-256-GCM
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(AAD_CONTEXT)
        val ciphertext = cipher.doFinal(plaintext)

        // Zeroize sensitive key buffers
        ecdhSecret.fill(0)
        passphraseKey.fill(0)
        hybridIkm.fill(0)
        aesKeyBytes.fill(0)

        val encoder = Base64.getEncoder()
        return EncryptedEnvelope(
            version = 1,
            kdfSaltBase64 = encoder.encodeToString(salt),
            kdfIterations = KDF_ITERATIONS,
            ephemeralPublicKeyBase64 = encoder.encodeToString(ephemeralKeyPair.public.encoded),
            ivBase64 = encoder.encodeToString(iv),
            ciphertextBase64 = encoder.encodeToString(ciphertext)
        )
    }

    fun decrypt(
        envelope: EncryptedEnvelope,
        passphrase: CharArray,
        recipientPrivateKey: PrivateKey
    ): ByteArray {
        val decoder = Base64.getDecoder()
        val salt = decoder.decode(envelope.kdfSaltBase64)
        val iv = decoder.decode(envelope.ivBase64)
        val ephemeralPubBytes = decoder.decode(envelope.ephemeralPublicKeyBase64)
        val ciphertext = decoder.decode(envelope.ciphertextBase64)

        val ephemeralPublicKey = KeyFactory.getInstance("X25519")
            .generatePublic(X509EncodedKeySpec(ephemeralPubBytes))

        val ecdhSecret = computeX25519SharedSecret(recipientPrivateKey, ephemeralPublicKey)
        val passphraseKey = derivePbkdf2Key(passphrase, salt, envelope.kdfIterations)

        val hybridIkm = ByteBuffer.allocate(ecdhSecret.size + passphraseKey.size)
            .put(ecdhSecret)
            .put(passphraseKey)
            .array()
        val aesKeyBytes = hkdfSha256(ikm = hybridIkm, salt = salt, info = AAD_CONTEXT, length = 32)
        val aesKey = SecretKeySpec(aesKeyBytes, "AES")

        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(AAD_CONTEXT)
            cipher.doFinal(ciphertext)
        } finally {
            ecdhSecret.fill(0)
            passphraseKey.fill(0)
            hybridIkm.fill(0)
            aesKeyBytes.fill(0)
        }
    }

    private fun computeX25519SharedSecret(privateKey: PrivateKey, publicKey: PublicKey): ByteArray {
        val agreement = KeyAgreement.getInstance("X25519")
        agreement.init(privateKey)
        agreement.doPhase(publicKey, true)
        return agreement.generateSecret()
    }

    private fun derivePbkdf2Key(passphrase: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, iterations, AES_KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        // HKDF-Extract
        val extractMac = Mac.getInstance("HmacSHA256")
        extractMac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = extractMac.doFinal(ikm)

        // HKDF-Expand
        val expandMac = Mac.getInstance("HmacSHA256")
        expandMac.init(SecretKeySpec(prk, "HmacSHA256"))
        val result = ByteArray(length)
        var t = ByteArray(0)
        var offset = 0
        var counter: Byte = 1

        while (offset < length) {
            expandMac.reset()
            expandMac.update(t)
            expandMac.update(info)
            expandMac.update(counter)
            t = expandMac.doFinal()
            val toCopy = minOf(t.size, length - offset)
            System.arraycopy(t, 0, result, offset, toCopy)
            offset += toCopy
            counter++
        }
        prk.fill(0)
        return result
    }
}
