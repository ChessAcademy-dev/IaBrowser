package com.aegis.browser

import com.aegis.browser.crypto.CryptoEngine
import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.security.AEADBadTagException

class CryptoEngineTest {

    @Test
    fun encryptAndDecryptRoundTripWithX25519AndPbkdf2() {
        val keyPair = CryptoEngine.generateX25519KeyPair()
        val passphrase = "correct-horse-battery-staple".toCharArray()
        val secretData = """{"bookmarks":["https://eff.org"],"notes":["private key backup"]}"""
            .toByteArray(StandardCharsets.UTF_8)

        val envelope = CryptoEngine.encrypt(secretData, passphrase, keyPair.public)
        val decrypted = CryptoEngine.decrypt(
            envelope,
            "correct-horse-battery-staple".toCharArray(),
            keyPair.private
        )

        assertArrayEquals(secretData, decrypted)
    }

    @Test(expected = AEADBadTagException::class)
    fun failsAuthenticationOnWrongPassphrase() {
        val keyPair = CryptoEngine.generateX25519KeyPair()
        val envelope = CryptoEngine.encrypt(
            "sensitive payload".toByteArray(),
            "right-passphrase".toCharArray(),
            keyPair.public
        )
        CryptoEngine.decrypt(envelope, "wrong-passphrase".toCharArray(), keyPair.private)
    }

    @Test(expected = AEADBadTagException::class)
    fun failsAuthenticationOnTamperedCiphertext() {
        val keyPair = CryptoEngine.generateX25519KeyPair()
        val envelope = CryptoEngine.encrypt(
            "sensitive payload".toByteArray(),
            "right-passphrase".toCharArray(),
            keyPair.public
        )
        val tampered = envelope.copy(
            ciphertextBase64 = envelope.ciphertextBase64.dropLast(4) + "AAAA"
        )
        CryptoEngine.decrypt(tampered, "right-passphrase".toCharArray(), keyPair.private)
    }
}
