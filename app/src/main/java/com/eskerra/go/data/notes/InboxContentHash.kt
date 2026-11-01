package com.eskerra.go.data.notes

import java.io.File
import java.security.MessageDigest

/** Hashes content to guard snoozing against a stale registry entry. */
internal object InboxContentHash {
    fun of(file: File): String = file.inputStream().buffered().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(32 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        hex(digest.digest())
    }

    fun of(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun hex(bytes: ByteArray): String {
        val digits = "0123456789abcdef"
        return buildString(bytes.size * 2) {
            bytes.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(digits[value ushr 4])
                append(digits[value and 0x0f])
            }
        }
    }
}
