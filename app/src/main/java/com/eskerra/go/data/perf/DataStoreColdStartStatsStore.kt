package com.eskerra.go.data.perf

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.eskerra.go.core.model.ColdStartSample
import com.eskerra.go.core.repository.ColdStartStatsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.coldStartStatsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "perf_cold_start"
)

/**
 * Preferences DataStore-backed [ColdStartStatsStore].
 *
 * Stores only durations, counts and flags — see [NON_SECRET_PREFERENCE_KEY_NAMES]. The window is
 * capped so a device that launches the app often cannot grow this file without bound.
 */
class DataStoreColdStartStatsStore(private val dataStore: DataStore<Preferences>) :
    ColdStartStatsStore {

    constructor(context: Context) : this(context.applicationContext.coldStartStatsDataStore)

    override suspend fun record(sample: ColdStartSample) {
        dataStore.edit { prefs ->
            val existing = prefs[SAMPLES].orEmpty().split(ROW_SEPARATOR).filter { it.isNotBlank() }
            val kept = (existing + ColdStartSampleCodec.encode(sample)).takeLast(MAX_SAMPLES)
            prefs[SAMPLES] = kept.joinToString(ROW_SEPARATOR)
        }
    }

    override suspend fun readWindow(): List<ColdStartSample> = dataStore.data
        .map { prefs -> prefs[SAMPLES].orEmpty() }
        .first()
        .split(ROW_SEPARATOR)
        .filter { it.isNotBlank() }
        // A row written by an older field layout is dropped rather than failing the whole window.
        .mapNotNull(ColdStartSampleCodec::decode)

    override suspend fun readLastReportedAtMs(): Long? =
        dataStore.data.map { it[LAST_REPORTED_AT_MS] }.first()

    override suspend fun resetWindow(reportedAtMs: Long) {
        dataStore.edit { prefs ->
            prefs.remove(SAMPLES)
            prefs[LAST_REPORTED_AT_MS] = reportedAtMs
        }
    }

    companion object {
        internal const val MAX_SAMPLES = 100
        private const val ROW_SEPARATOR = "\n"

        private val SAMPLES = stringPreferencesKey("cold_start_samples")
        private val LAST_REPORTED_AT_MS = longPreferencesKey("cold_start_last_reported_at_ms")

        /** Every key this store writes. Asserted by DataStoreColdStartStatsStoreAndroidTest. */
        val NON_SECRET_PREFERENCE_KEY_NAMES = setOf(
            "cold_start_samples",
            "cold_start_last_reported_at_ms"
        )
    }
}
