package com.eskerra.go.data.git

import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GitRepoStateInspectorTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val gitRepo = JGitWorkspaceRepository()

    @Test
    fun cleanRepo_doesNotRequireManualIntervention() {
        val workspace = initWorkspace()

        assertFalse(GitRepoStateInspector.requiresManualIntervention(workspace))
    }

    @Test
    fun mergeHeadPresent_requiresManualIntervention() {
        val workspace = initWorkspace()
        File(workspace, ".git/MERGE_HEAD").writeText("deadbeef")

        assertTrue(GitRepoStateInspector.requiresManualIntervention(workspace))
    }

    @Test
    fun cherryPickHeadPresent_requiresManualIntervention() {
        val workspace = initWorkspace()
        File(workspace, ".git/CHERRY_PICK_HEAD").writeText("deadbeef")

        assertTrue(GitRepoStateInspector.requiresManualIntervention(workspace))
    }

    @Test
    fun revertHeadPresent_requiresManualIntervention() {
        val workspace = initWorkspace()
        File(workspace, ".git/REVERT_HEAD").writeText("deadbeef")

        assertTrue(GitRepoStateInspector.requiresManualIntervention(workspace))
    }

    @Test
    fun rebaseMergePresent_requiresManualIntervention() {
        val workspace = initWorkspace()
        File(workspace, ".git/rebase-merge").mkdirs()

        assertTrue(GitRepoStateInspector.requiresManualIntervention(workspace))
    }

    private fun initWorkspace(): File {
        val filesDir = temp.newFolder("files")
        val workspace = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        workspace.mkdirs()
        gitRepo.initOrOpen(workspace).getOrThrow()
        return workspace
    }
}
