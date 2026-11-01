package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.credentials.FakeCredentialStore
import com.eskerra.go.data.git.JGitRemoteSyncRepository
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.git.TestGitRepos
import com.eskerra.go.data.notes.FileNoteRegistryRepository
import com.eskerra.go.data.notes.NoteRegistryCache
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ManualSyncNotificationBoundaryTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun localChangesAreRecordedBeforeCommitWithoutChangingMarkdown() = runTest {
        val git = JGitWorkspaceRepository()
        val remote = TestGitRepos.fileUri(
            TestGitRepos.initBareRemote(File(temp.root, "remote.git"))
        )
        val seed = temp.newFolder("seed")
        git.cloneFrom(remote, seed).getOrThrow()
        git.writeFile(seed, "Inbox/seed.md", "# Seed\n").getOrThrow()
        git.stageAll(seed).getOrThrow()
        git.commit(seed, "seed").getOrThrow()
        git.push(seed).getOrThrow()
        val branch = git.status(seed).getOrThrow().branch
        val filesDir = temp.newFolder("files")
        val workspace = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        git.cloneFrom(remote, workspace).getOrThrow()
        val config =
            WorkspaceConfig("Test", WorkspacePaths.DEFAULT_RELATIVE_PATH, remote, branch, 0L)
        val body = "# Local\n\nKeep this exactly.\n"
        git.writeFile(workspace, "Inbox/new.md", body).getOrThrow()
        val repository = JGitRemoteSyncRepository(git)
        var recorded: Set<String> = emptySet()
        val sync = ManualSyncNow(
            remoteSyncRepository = repository,
            credentialStore = FakeCredentialStore(),
            registryCache = NoteRegistryCache(FileNoteRegistryRepository()),
            loadSyncStatus = LoadSyncStatus(repository),
            recordLocalChanges = { _, paths ->
                recorded = paths
                assertEquals(body, File(workspace, "Inbox/new.md").readText())
            }
        )
        assertTrue(sync(config, filesDir).isSuccess)
        assertTrue("Inbox/new.md" in recorded)
        assertEquals(body, File(workspace, "Inbox/new.md").readText())

        git.writeFile(workspace, "Inbox/fail.md", body).getOrThrow()
        val failingSync = ManualSyncNow(
            remoteSyncRepository = repository,
            credentialStore = FakeCredentialStore(),
            registryCache = NoteRegistryCache(FileNoteRegistryRepository()),
            loadSyncStatus = LoadSyncStatus(repository),
            recordLocalChanges = { _, _ -> error("notification state unavailable") }
        )
        assertTrue(failingSync(config, filesDir).isFailure)
        assertEquals(body, File(workspace, "Inbox/fail.md").readText())
        assertTrue("Inbox/fail.md" in git.status(workspace).getOrThrow().changedPaths)
    }
}
