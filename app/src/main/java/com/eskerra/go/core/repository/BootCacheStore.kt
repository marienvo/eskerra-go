package com.eskerra.go.core.repository

import com.eskerra.go.core.model.GateFingerprint

/** Persists the last known-good app gate fingerprint for optimistic cold start. */
interface BootCacheStore {
    suspend fun readFingerprint(): GateFingerprint?

    suspend fun saveFingerprint(fingerprint: GateFingerprint)

    suspend fun clearFingerprint()
}
