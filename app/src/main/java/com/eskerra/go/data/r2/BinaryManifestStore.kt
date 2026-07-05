package com.eskerra.go.data.r2

import com.eskerra.go.core.model.BinaryManifestEntry
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class BinaryManifestDocument(val entries: List<BinaryManifestEntry> = emptyList())

/**
 * Persists the binaries download manifest as JSON at [manifestFile] (kept **outside**
 * the vault working tree, in app-private storage). A missing or corrupt file reads as
 * an empty manifest.
 */
class BinaryManifestStore(filesDir: File, fileName: String = DEFAULT_FILE_NAME) {

    private val manifestFile = File(filesDir, fileName)

    fun read(): List<BinaryManifestEntry> {
        if (!manifestFile.isFile) return emptyList()
        return runCatching {
            json.decodeFromString(BinaryManifestDocument.serializer(), manifestFile.readText())
        }.getOrNull()?.entries ?: emptyList()
    }

    fun write(entries: List<BinaryManifestEntry>) {
        manifestFile.parentFile?.mkdirs()
        val text = json.encodeToString(
            BinaryManifestDocument.serializer(),
            BinaryManifestDocument(entries)
        )
        val temp = File.createTempFile("manifest", ".part", manifestFile.parentFile)
        temp.writeText(text)
        if (!temp.renameTo(manifestFile)) {
            manifestFile.writeText(text)
            temp.delete()
        }
    }

    private companion object {
        const val DEFAULT_FILE_NAME = "binaries-manifest.json"
        val json = Json { ignoreUnknownKeys = true }
    }
}
