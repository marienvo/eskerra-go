package com.eskerra.go.data.todayhub

import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.TodayHubSnapshotStore
import com.eskerra.go.core.todayhub.TodayHubSnapshot
import java.io.File

/** In-memory [TodayHubSnapshotStore] for JVM tests. */
class FakeTodayHubSnapshotStore : TodayHubSnapshotStore {
    var snapshot: TodayHubSnapshot? = null
        private set
    var saveCount: Int = 0
        private set

    override suspend fun read(config: WorkspaceConfig, filesDir: File): TodayHubSnapshot? = snapshot

    override suspend fun save(config: WorkspaceConfig, filesDir: File, snapshot: TodayHubSnapshot) {
        this.snapshot = snapshot
        saveCount += 1
    }

    override suspend fun clear(config: WorkspaceConfig, filesDir: File) {
        snapshot = null
    }
}
