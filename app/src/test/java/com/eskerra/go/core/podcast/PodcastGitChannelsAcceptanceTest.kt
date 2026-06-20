package com.eskerra.go.core.podcast

import com.eskerra.go.core.model.SyncError
import com.eskerra.go.core.model.SyncException
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.LoadSyncStatus
import com.eskerra.go.core.usecase.ManualSyncNow
import com.eskerra.go.data.credentials.FakeCredentialStore
import com.eskerra.go.data.git.GitSyncMutex
import com.eskerra.go.data.git.JGitRemoteSyncRepository
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.notes.FileNoteRegistryRepository
import com.eskerra.go.data.notes.NoteRegistryCache
import com.eskerra.go.data.workspace.WorkspacePaths
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PodcastGitChannelsAcceptanceTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val gitRepo = JGitWorkspaceRepository()
    private val remoteSync = JGitRemoteSyncRepository(gitRepo)

    @Test
    fun manualSyncRejectedWhilePodcastChannelHoldsMutex() = runTest {
        val mutex = GitSyncMutex()
        val hold = CompletableDeferred<Unit>()
        val podcastHold = async {
            mutex.mutex.withLock {
                hold.await()
            }
        }
        runCurrent()

        val manualSync = ManualSyncNow(
            remoteSyncRepository = remoteSync,
            credentialStore = FakeCredentialStore(),
            registryCache = NoteRegistryCache(FileNoteRegistryRepository()),
            loadSyncStatus = LoadSyncStatus(remoteSync),
            gitSyncMutex = mutex,
            dispatcher = UnconfinedTestDispatcher(testScheduler)
        )
        val config = WorkspaceConfig(
            name = "Vault",
            relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
            remoteUri = null,
            branch = "main",
            setupCompletedAtEpochMs = 0L
        )
        val filesDir = temp.newFolder("files")

        val result = manualSync(config, filesDir)

        assertTrue(result.isFailure)
        assertEquals(
            SyncError.SyncAlreadyRunning,
            (result.exceptionOrNull() as SyncException).error
        )

        hold.complete(Unit)
        podcastHold.await()
    }
}
