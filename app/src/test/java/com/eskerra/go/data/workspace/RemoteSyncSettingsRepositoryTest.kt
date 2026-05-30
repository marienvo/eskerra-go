package com.eskerra.go.data.workspace

import com.eskerra.go.core.model.RemoteSyncSettingsError
import com.eskerra.go.core.model.RemoteSyncSettingsException
import com.eskerra.go.core.model.SyncError
import com.eskerra.go.core.model.SyncException
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.LoadSyncStatus
import com.eskerra.go.core.usecase.ManualSyncNow
import com.eskerra.go.core.usecase.UpdateSyncToken
import com.eskerra.go.data.credentials.FakeCredentialStore
import com.eskerra.go.data.git.JGitRemoteSyncRepository
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.git.TestGitRepos
import com.eskerra.go.data.notes.FileNoteRegistryRepository
import com.eskerra.go.data.notes.MarkdownNoteScanner
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RemoteSyncSettingsRepositoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val gitRepo = JGitWorkspaceRepository()
    private val remoteSync = JGitRemoteSyncRepository(gitRepo)
    private val credentials = FakeCredentialStore()

    private fun repository(workspaceStore: WorkspaceStore = FakeWorkspaceStore()) =
        DefaultRemoteSyncSettingsRepository(
            workspaceStore = workspaceStore,
            credentialStore = credentials,
            remoteSyncRepository = remoteSync
        )

    private fun localConfig(filesDir: File, remoteUri: String? = null): WorkspaceConfig {
        val workspaceDir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        workspaceDir.mkdirs()
        gitRepo.initOrOpen(workspaceDir).getOrThrow()
        return WorkspaceConfig(
            name = "Notes",
            relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
            remoteUri = remoteUri,
            branch = gitRepo.status(workspaceDir).getOrThrow().branch,
            setupCompletedAtEpochMs = 0L
        )
    }

    @Test
    fun loadSettings_configuredRemote_returnsSanitizedMetadata() = runTest {
        val filesDir = temp.newFolder("files")
        val remote = preparedRemote()
        val config = localConfig(filesDir).copy(
            remoteUri = remote.remoteUri,
            branch = remote.branch
        )
        credentials.saveToken(config.relativePath, "stored-token").getOrThrow()

        val settings = repository().readSettings(config)

        assertEquals(remote.remoteUri, settings.remoteUri)
        assertEquals(remote.branch, settings.branch)
        assertTrue(settings.isConfigured)
        assertTrue(settings.hasStoredCredential)
    }

    @Test
    fun loadSettings_unconfigured_returnsEmptyState() = runTest {
        val filesDir = temp.newFolder("files")
        val config = localConfig(filesDir)

        val settings = repository().readSettings(config)

        assertNull(settings.remoteUri)
        assertFalse(settings.isConfigured)
        assertFalse(settings.hasStoredCredential)
    }

    @Test
    fun loadSettings_neverExposesStoredToken() = runTest {
        val filesDir = temp.newFolder("files")
        val config = localConfig(filesDir)
        credentials.saveToken(config.relativePath, "hidden-token").getOrThrow()

        val settings = repository().readSettings(config)

        assertTrue(settings.hasStoredCredential)
        assertFalse(settings.toString().contains("hidden-token"))
    }

    @Test
    fun saveSettings_alignsLocalCheckoutToConfiguredBranch() = runTest {
        val filesDir = temp.newFolder("files")
        val remote = preparedRemote()
        val workspaceDir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        workspaceDir.mkdirs()
        org.eclipse.jgit.api.Git.init()
            .setDirectory(workspaceDir)
            .setInitialBranch("master")
            .call()
            .close()
        val config = WorkspaceConfig(
            name = "Notes",
            relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
            remoteUri = null,
            branch = "master",
            setupCompletedAtEpochMs = 0L
        )

        val result = repository().saveSettings(
            config = config,
            remoteUri = remote.remoteUri,
            branch = remote.branch,
            replacementToken = null,
            filesDir = filesDir
        )

        assertTrue(result.isSuccess)
        assertEquals(remote.branch, gitRepo.status(workspaceDir).getOrThrow().branch)
    }

    @Test
    fun saveSettings_httpsRemote_persistsSanitizedUrlAndBranch() = runTest {
        val filesDir = temp.newFolder("files")
        val workspaceStore = FakeWorkspaceStore()
        val config = localConfig(filesDir)
        val remote = preparedRemote()

        val result = repository(workspaceStore).saveSettings(
            config = config,
            remoteUri = remote.remoteUri,
            branch = remote.branch,
            replacementToken = "new-token",
            filesDir = filesDir
        )

        assertTrue(result.isSuccess)
        val saved = workspaceStore.read()
        requireNotNull(saved)
        assertEquals(remote.remoteUri, saved.remoteUri)
        assertEquals(remote.branch, saved.branch)
        assertFalse(saved.remoteUri!!.contains("new-token"))
    }

    @Test
    fun saveSettings_rejectsRemoteUrlWithUserinfo() = runTest {
        val filesDir = temp.newFolder("files")
        val config = localConfig(filesDir)

        val result = repository().saveSettings(
            config = config,
            remoteUri = "https://user:pass@example.com/repo.git",
            branch = "main",
            replacementToken = "token",
            filesDir = filesDir
        )

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as RemoteSyncSettingsException
        assertTrue(error.error is RemoteSyncSettingsError.InvalidRemoteUri)
        assertFalse(error.error.message().contains("user:pass"))
        assertFalse(error.error.message().contains("token"))
    }

    @Test
    fun saveSettings_rejectsInvalidBranch() = runTest {
        val filesDir = temp.newFolder("files")
        val remote = preparedRemote()
        val config = localConfig(filesDir)

        val result = repository().saveSettings(
            config = config,
            remoteUri = remote.remoteUri,
            branch = "bad..branch",
            replacementToken = null,
            filesDir = filesDir
        )

        assertTrue(result.isFailure)
        assertTrue(
            (result.exceptionOrNull() as RemoteSyncSettingsException).error is
                RemoteSyncSettingsError.InvalidBranch
        )
    }

    @Test
    fun clearSettings_clearsTokenAndMetadata() = runTest {
        val filesDir = temp.newFolder("files")
        val workspaceStore = FakeWorkspaceStore()
        val remote = preparedRemote()
        val config = localConfig(filesDir).copy(
            remoteUri = remote.remoteUri,
            branch = remote.branch
        )
        credentials.saveToken(config.relativePath, "token").getOrThrow()
        workspaceStore.save(config)
        repository(workspaceStore).saveSettings(
            config = config,
            remoteUri = remote.remoteUri,
            branch = remote.branch,
            replacementToken = "token",
            filesDir = filesDir
        ).getOrThrow()

        val cleared = repository(workspaceStore).clearSettings(config, filesDir).getOrThrow()

        assertNull(cleared.remoteUri)
        assertNull(workspaceStore.read()?.remoteUri)
        assertNull(credentials.readToken(config.relativePath).getOrThrow())
    }

    @Test
    fun clearSettings_removesOriginFromGitConfig() = runTest {
        val filesDir = temp.newFolder("files")
        val remote = preparedRemote()
        val config = localConfig(filesDir).copy(
            remoteUri = remote.remoteUri,
            branch = remote.branch
        )
        val workspaceDir = File(filesDir, config.relativePath)
        repository().saveSettings(
            config = config,
            remoteUri = remote.remoteUri,
            branch = remote.branch,
            replacementToken = null,
            filesDir = filesDir
        ).getOrThrow()
        assertTrue(File(workspaceDir, ".git/config").readText().contains("[remote \"origin\"]"))

        repository().clearSettings(config, filesDir).getOrThrow()

        assertFalse(File(workspaceDir, ".git/config").readText().contains("[remote \"origin\"]"))
    }

    @Test
    fun saveSettings_metadataFailure_restoresOriginAndToken() = runTest {
        val filesDir = temp.newFolder("files")
        val workspaceStore = FakeWorkspaceStore()
        val remote = preparedRemote()
        val config = localConfig(filesDir)
        credentials.saveToken(config.relativePath, "keep-token").getOrThrow()
        val saved = repository(workspaceStore).saveSettings(
            config = config,
            remoteUri = remote.remoteUri,
            branch = remote.branch,
            replacementToken = null,
            filesDir = filesDir
        ).getOrThrow()

        val failingStore = object : WorkspaceStore {
            override suspend fun read(): WorkspaceConfig? = workspaceStore.read()

            override suspend fun save(config: WorkspaceConfig): Unit =
                throw RuntimeException("metadata save failed")

            override suspend fun clear() {
                workspaceStore.clear()
            }
        }
        val result = repository(failingStore).saveSettings(
            config = saved,
            remoteUri = remote.remoteUri,
            branch = "other-branch",
            replacementToken = "new-token",
            filesDir = filesDir
        )

        assertTrue(result.isFailure)
        assertEquals("keep-token", credentials.readToken(config.relativePath).getOrThrow())
        assertEquals(remote.branch, workspaceStore.read()?.branch)
        val configText = File(filesDir, "${config.relativePath}/.git/config").readText()
        assertTrue(configText.contains("file:"))
    }

    @Test
    fun clearSettings_leavesLocalNotesIntact() = runTest {
        val filesDir = temp.newFolder("files")
        val remote = preparedRemote()
        val config = localConfig(filesDir).copy(
            remoteUri = remote.remoteUri,
            branch = remote.branch
        )
        val workspaceDir = File(filesDir, config.relativePath)
        val keepPath = "${MarkdownNoteScanner.INBOX_DIRECTORY}/keep.md"
        gitRepo.writeFile(workspaceDir, keepPath, "# Keep\n").getOrThrow()

        repository().clearSettings(config, filesDir).getOrThrow()

        assertTrue(File(workspaceDir, keepPath).isFile)
        assertTrue(WorkspacePaths.isValidGitWorkspace(workspaceDir))
    }

    @Test
    fun replaceToken_overwritesOldToken() = runTest {
        val config = localConfig(temp.newFolder("files"))
        credentials.saveToken(config.relativePath, "old-token").getOrThrow()

        repository().replaceToken(config.relativePath, "new-token").getOrThrow()

        assertEquals("new-token", credentials.readToken(config.relativePath).getOrThrow())
        assertEquals(1, credentials.tokens.size)
    }

    @Test
    fun saveSettings_tokenNotStoredInDataStore() = runTest {
        val filesDir = temp.newFolder("files")
        val workspaceStore = FakeWorkspaceStore()
        val remote = preparedRemote()
        val config = localConfig(filesDir)
        val token = "secret-pat-value"

        repository(workspaceStore).saveSettings(
            config = config,
            remoteUri = remote.remoteUri,
            branch = remote.branch,
            replacementToken = token,
            filesDir = filesDir
        ).getOrThrow()

        val saved = workspaceStore.read()
        requireNotNull(saved)
        DataStoreWorkspaceStore.NON_SECRET_PREFERENCE_KEY_NAMES.forEach { key ->
            assertFalse(key.contains("token", ignoreCase = true))
        }
        assertFalse(saved.toString().contains(token))
        assertFalse(saved.remoteUri?.contains(token) == true)
    }

    @Test
    fun saveSettings_tokenNotWrittenToGitConfig() = runTest {
        val filesDir = temp.newFolder("files")
        val remote = preparedRemote()
        val config = localConfig(filesDir)
        val token = "x-pat-7f3a9c2e"

        repository().saveSettings(
            config = config,
            remoteUri = remote.remoteUri,
            branch = remote.branch,
            replacementToken = token,
            filesDir = filesDir
        ).getOrThrow()

        val gitConfig = File(filesDir, "${config.relativePath}/.git/config")
        assertTrue(gitConfig.isFile)
        val configText = gitConfig.readText()
        val urlLine = configText.lines().first { it.trim().startsWith("url =") }
        assertFalse(urlLine.contains(token))
        assertTrue(urlLine.contains("file:"))
    }

    @Test
    fun testConnection_succeedsForValidRemote() = runTest {
        val filesDir = temp.newFolder("files")
        val remote = preparedRemote()
        val workspaceDir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        gitRepo.cloneFrom(remote.remoteUri, workspaceDir).getOrThrow()
        val config = WorkspaceConfig(
            name = "Notes",
            relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
            remoteUri = remote.remoteUri,
            branch = remote.branch,
            setupCompletedAtEpochMs = 0L
        )
        remoteSync.configureSanitizedOrigin(workspaceDir, remote.remoteUri).getOrThrow()

        val result = repository().testConnection(config, filesDir)

        assertTrue(result.isSuccess)
    }

    @Test
    fun testConnection_missingToken_returnsMissingCredential() = runTest {
        val filesDir = temp.newFolder("files")
        val config = localConfig(filesDir).copy(
            remoteUri = "https://github.com/example/notes.git",
            branch = "main"
        )

        val result = repository().testConnection(config, filesDir)

        assertTrue(result.isFailure)
        assertTrue(
            (result.exceptionOrNull() as RemoteSyncSettingsException).error is
                RemoteSyncSettingsError.MissingCredential
        )
    }

    @Test
    fun manualSync_reportsSetupRequiredWhenRemoteMissing() = runTest {
        val filesDir = temp.newFolder("files")
        val config = localConfig(filesDir)
        val manualSync = ManualSyncNow(
            remoteSyncRepository = remoteSync,
            credentialStore = credentials,
            registryRepository = FileNoteRegistryRepository(),
            loadSyncStatus = LoadSyncStatus(remoteSync)
        )

        val result = manualSync(config, filesDir)

        assertTrue(result.isFailure)
        assertTrue(
            (result.exceptionOrNull() as SyncException).error is SyncError.MissingRemoteConfig
        )
    }

    @Test
    fun manualSync_reportsMissingCredentialAfterClear() = runTest {
        val filesDir = temp.newFolder("files")
        val remote = preparedRemote()
        val workspaceStore = FakeWorkspaceStore()
        val config = localConfig(filesDir)
        val saved = repository(workspaceStore).saveSettings(
            config = config,
            remoteUri = remote.remoteUri,
            branch = remote.branch,
            replacementToken = "token",
            filesDir = filesDir
        ).getOrThrow()
        repository(workspaceStore).clearSettings(saved, filesDir).getOrThrow()
        val httpsConfig = saved.copy(remoteUri = "https://github.com/example/notes.git")
        workspaceStore.save(httpsConfig)
        credentials.clear(httpsConfig.relativePath).getOrThrow()

        val manualSync = ManualSyncNow(
            remoteSyncRepository = remoteSync,
            credentialStore = credentials,
            registryRepository = FileNoteRegistryRepository(),
            loadSyncStatus = LoadSyncStatus(remoteSync)
        )
        val result = manualSync(httpsConfig, filesDir)

        assertTrue(result.isFailure)
        assertTrue(
            (result.exceptionOrNull() as SyncException).error is SyncError.MissingCredential
        )
    }

    @Test
    fun manualSync_worksAfterTokenReplacement() = runTest {
        val filesDir = temp.newFolder("files")
        val remote = preparedRemote()
        val workspaceDir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        gitRepo.cloneFrom(remote.remoteUri, workspaceDir).getOrThrow()
        val config = WorkspaceConfig(
            name = "Notes",
            relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
            remoteUri = remote.remoteUri,
            branch = remote.branch,
            setupCompletedAtEpochMs = 0L
        )
        repository().saveSettings(
            config = config,
            remoteUri = remote.remoteUri,
            branch = remote.branch,
            replacementToken = "first-token",
            filesDir = filesDir
        ).getOrThrow()
        UpdateSyncToken(repository()).invoke(config.relativePath, "second-token").getOrThrow()

        val manualSync = ManualSyncNow(
            remoteSyncRepository = remoteSync,
            credentialStore = credentials,
            registryRepository = FileNoteRegistryRepository(),
            loadSyncStatus = LoadSyncStatus(remoteSync)
        )
        val result = manualSync(config, filesDir)

        assertTrue(result.isSuccess)
        assertEquals("second-token", credentials.readToken(config.relativePath).getOrThrow())
    }

    private data class PreparedRemote(val remoteUri: String, val branch: String)

    private fun preparedRemote(): PreparedRemote {
        val bare = TestGitRepos.initBareRemote(File(temp.root, "remote-${System.nanoTime()}.git"))
        val remoteUri = TestGitRepos.fileUri(bare)
        val producer = temp.newFolder("producer")
        gitRepo.cloneFrom(remoteUri, producer).getOrThrow()
        gitRepo.writeFile(producer, "${MarkdownNoteScanner.INBOX_DIRECTORY}/seed.md", "# Seed\n")
            .getOrThrow()
        gitRepo.stageAll(producer).getOrThrow()
        gitRepo.commit(producer, "Seed").getOrThrow()
        gitRepo.push(producer).getOrThrow()
        return PreparedRemote(
            remoteUri = remoteUri,
            branch = gitRepo.status(producer).getOrThrow().branch
        )
    }
}
