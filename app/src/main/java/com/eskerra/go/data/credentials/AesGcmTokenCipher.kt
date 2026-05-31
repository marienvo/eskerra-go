package com.eskerra.go.data.credentials

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-GCM token cipher for JVM unit tests and non-Keystore environments.
 *
 * Production Android wiring should prefer [AndroidKeystoreTokenCipher].
 */
class AesGcmTokenCipher(secretKey: SecretKey) : TokenCipher {

    constructor(keyBytes: ByteArray) : this(
        SecretKeySpec(keyBytes.copyOf(REQUIRED_KEY_BYTES), ALGORITHM)
    )

    private val key: SecretKey = secretKey

    override fun encrypt(plaintext: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = ByteArray(GCM_IV_BYTES).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return iv + ciphertext
    }

    override fun decrypt(ciphertext: ByteArray): String {
        require(ciphertext.size > GCM_IV_BYTES) { "ciphertext is too short" }
        val iv = ciphertext.copyOfRange(0, GCM_IV_BYTES)
        val payload = ciphertext.copyOfRange(GCM_IV_BYTES, ciphertext.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(payload).toString(Charsets.UTF_8)
    }

    companion object {
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val REQUIRED_KEY_BYTES = 32

        fun randomKeyBytes(): ByteArray =
            ByteArray(REQUIRED_KEY_BYTES).also { SecureRandom().nextBytes(it) }
    }
}
