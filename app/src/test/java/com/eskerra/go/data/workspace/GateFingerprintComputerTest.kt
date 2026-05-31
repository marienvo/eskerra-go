package com.eskerra.go.data.workspace

import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.git.JGitWorkspaceRepository
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GateFingerprintComputerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val config = WorkspaceConfig(
        name = "My Notes",
        relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
        remoteUri = null,
        branch = "main",
        setupCompletedAtEpochMs = 1_700_000_000_000L
    )

    @Test
    fun compute_isStableForSameWorkspace() {
        val filesDir = temp.newFolder("files")
        prepareWorkspace(filesDir)

        val first = GateFingerprintComputer.compute(config, filesDir)
        val second = GateFingerprintComputer.compute(config, filesDir)

        assertEquals(first, second)
    }

    @Test
    fun compute_changesWhenHeadChanges() {
        val filesDir = temp.newFolder("files")
        prepareWorkspace(filesDir)

        val before = GateFingerprintComputer.compute(config, filesDir)
        File(filesDir, "${WorkspacePaths.DEFAULT_RELATIVE_PATH}/.git/HEAD")
            .writeText("ref: refs/heads/feature\n")
        val after = GateFingerprintComputer.compute(config, filesDir)

        assertNotEquals(before, after)
    }

    private fun prepareWorkspace(filesDir: File) {
        val workspaceDir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        workspaceDir.mkdirs()
        JGitWorkspaceRepository().initOrOpen(workspaceDir).getOrThrow()
    }
}
