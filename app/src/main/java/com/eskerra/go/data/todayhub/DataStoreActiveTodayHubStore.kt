package com.eskerra.go.data.todayhub

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.eskerra.go.core.repository.ActiveTodayHubStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.todayHubDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "today_hub"
)

/** Preferences DataStore-backed [ActiveTodayHubStore]. Stores only the active hub note id (non-secret). */
class DataStoreActiveTodayHubStore(private val dataStore: DataStore<Preferences>) :
    ActiveTodayHubStore {

    constructor(context: Context) : this(context.applicationContext.todayHubDataStore)

    override suspend fun read(): String? = dataStore.data.map { it[ACTIVE_HUB_NOTE_ID] }.first()

    override suspend fun save(noteId: String) {
        dataStore.edit { it[ACTIVE_HUB_NOTE_ID] = noteId }
    }

    private companion object {
        val ACTIVE_HUB_NOTE_ID = stringPreferencesKey("active_today_hub_note_id")
    }
}
