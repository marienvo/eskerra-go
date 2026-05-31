package com.eskerra.go.data.workspace

import com.eskerra.go.core.model.GateFingerprint
import com.eskerra.go.core.repository.BootCacheStore

/** In-memory [BootCacheStore] for JVM tests. */
class FakeBootCacheStore : BootCacheStore {
    private var fingerprint: GateFingerprint? = null

    override suspend fun readFingerprint(): GateFingerprint? = fingerprint

    override suspend fun saveFingerprint(fingerprint: GateFingerprint) {
        this.fingerprint = fingerprint
    }

    override suspend fun clearFingerprint() {
        fingerprint = null
    }
}
