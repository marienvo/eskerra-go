package com.eskerra.go.data.search

import java.io.File
import java.security.MessageDigest

internal object VaultSearchPathHasher {
    fun sha1Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val bytes = digest.digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { byte -> "%02x".format(byte) }
    }

    fun indexDatabaseFile(filesDir: File, workspaceRoot: File): File {
        val hash = sha1Hex(workspaceRoot.canonicalPath)
        return File(filesDir, "vault-search-index/$hash.sqlite")
    }
}
