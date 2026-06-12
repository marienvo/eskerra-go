package com.eskerra.go.core.search

object ReconcileDiffer {
    fun diff(
        inDb: Map<String, FileSnapshot>,
        onDisk: Map<String, FileSnapshot>
    ): ReconcileDiffResult {
        val removed = inDb.keys.filter { it !in onDisk }
        val added = onDisk.keys.filter { it !in inDb }
        val updated = onDisk.keys.filter { key ->
            val existing = inDb[key] ?: return@filter false
            val current = onDisk.getValue(key)
            existing.size != current.size || existing.lastModified != current.lastModified
        }
        return ReconcileDiffResult(added = added, updated = updated, removed = removed)
    }
}
