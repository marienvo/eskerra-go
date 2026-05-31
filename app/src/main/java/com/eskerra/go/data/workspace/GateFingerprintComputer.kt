package com.eskerra.go.data.workspace

import com.eskerra.go.core.model.GateFingerprint
import com.eskerra.go.core.model.WorkspaceConfig
import java.io.File
import java.security.MessageDigest

/** Computes a deterministic local workspace fingerprint without network I/O. */
object GateFingerprintComputer {

    fun compute(config: WorkspaceConfig, filesDir: File): GateFingerprint {
        val workspaceDir = WorkspacePaths.resolve(filesDir, config.relativePath).getOrNull()
        val headContent = workspaceDir
            ?.let { File(it, ".git/HEAD") }
            ?.takeIf { it.isFile }
            ?.readText()
            ?.trim()
            .orEmpty()
        val branchRef = workspaceDir
            ?.let { File(it, ".git/refs/heads/${config.branch.trim()}") }
            ?.takeIf { it.isFile }
            ?.readText()
            ?.trim()
            .orEmpty()
        val raw = buildString {
            append(config.relativePath)
            append('\n')
            append(config.setupCompletedAtEpochMs)
            append('\n')
            append(config.branch)
            append('\n')
            append(headContent)
            append('\n')
            append(branchRef)
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
        val encoded = digest.joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
        return GateFingerprint(encoded)
    }
}
