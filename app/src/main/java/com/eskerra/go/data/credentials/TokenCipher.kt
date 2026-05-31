package com.eskerra.go.data.credentials

/** Encrypts and decrypts workspace access tokens at rest. */
interface TokenCipher {
    fun encrypt(plaintext: String): ByteArray

    fun decrypt(ciphertext: ByteArray): String
}
