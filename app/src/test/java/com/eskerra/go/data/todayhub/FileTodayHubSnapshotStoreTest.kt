package com.eskerra.go.data.todayhub

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.todayhub.TodayHubFrontmatter
import com.eskerra.go.core.todayhub.TodayHubRef
import com.eskerra.go.core.todayhub.TodayHubRow
import com.eskerra.go.core.todayhub.TodayHubSnapshot
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileTodayHubSnapshotStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val config = WorkspaceConfig(
        name = "My Notes",
        relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
        remoteUri = null,
        branch = "main",
        setupCompletedAtEpochMs = 1_700_000_000_000L
    )

    private val snapshot = TodayHubSnapshot(
        hubs = listOf(TodayHubRef(NoteId("Daily/Today.md"), "Daily")),
        activeHubId = NoteId("Daily/Today.md"),
        settings = TodayHubFrontmatter.Settings(columns = listOf("Tasks")),
        introMarkdown = "Intro",
        availableWeekStems = listOf("2026-04-06"),
        selectedWeekStem = "2026-04-06",
        row = TodayHubRow(
            rowNoteId = NoteId("Daily/2026-04-06.md"),
            weekStartStem = "2026-04-06",
            columns = listOf("default", "tasks")
        )
    )

    @Test
    fun saveAndRead_roundTripsSnapshot() = runTest {
        val filesDir = temp.newFolder("files")
        val store = FileTodayHubSnapshotStore()

        store.save(config, filesDir, snapshot)

        assertEquals(snapshot, store.read(config, filesDir))
    }

    @Test
    fun read_returnsNullWhenFileMissing() = runTest {
        val filesDir = temp.newFolder("files")
        val store = FileTodayHubSnapshotStore()

        assertNull(store.read(config, filesDir))
    }

    @Test
    fun read_returnsNullForCorruptSnapshot() = runTest {
        val filesDir = temp.newFolder("files")
        val snapshotFile = File(filesDir, "cache/today_hub_snapshot.json")
        snapshotFile.parentFile?.mkdirs()
        snapshotFile.writeText("{not-json")

        val store = FileTodayHubSnapshotStore()

        assertNull(store.read(config, filesDir))
    }

    @Test
    fun read_returnsNullForFingerprintMismatch() = runTest {
        val filesDir = temp.newFolder("files")
        val store = FileTodayHubSnapshotStore()
        store.save(config, filesDir, snapshot)

        val changedConfig = config.copy(branch = "other")

        assertNull(store.read(changedConfig, filesDir))
    }
}
