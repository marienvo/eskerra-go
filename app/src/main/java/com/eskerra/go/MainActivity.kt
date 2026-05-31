package com.eskerra.go

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.eskerra.go.app.AppRoot
import com.eskerra.go.core.usecase.BuildSafeSyncDiagnostic
import com.eskerra.go.core.usecase.BuildSyncPreflight
import com.eskerra.go.core.usecase.ClearRemoteSyncSettings
import com.eskerra.go.core.usecase.CreateInboxNote
import com.eskerra.go.core.usecase.LoadEditableNote
import com.eskerra.go.core.usecase.LoadGitStatusSummary
import com.eskerra.go.core.usecase.LoadInboxSummaries
import com.eskerra.go.core.usecase.LoadInboxSummariesCached
import com.eskerra.go.core.usecase.LoadNoteForReading
import com.eskerra.go.core.usecase.LoadRemoteSyncSettings
import com.eskerra.go.core.usecase.LoadSyncStatus
import com.eskerra.go.core.usecase.ManualSyncNow
import com.eskerra.go.core.usecase.ReconcileWorkspaceSyncBranch
import com.eskerra.go.core.usecase.RecordLastSyncAttempt
import com.eskerra.go.core.usecase.RefreshRemoteSyncStatus
import com.eskerra.go.core.usecase.SaveNote
import com.eskerra.go.core.usecase.SaveRemoteSyncSettings
import com.eskerra.go.core.usecase.TestRemoteConnection
import com.eskerra.go.core.usecase.UpdateSyncToken
import com.eskerra.go.data.credentials.AndroidKeystoreTokenCipher
import com.eskerra.go.data.credentials.EncryptedCredentialStore
import com.eskerra.go.data.git.JGitRemoteSyncRepository
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.notes.FileInboxSnapshotStore
import com.eskerra.go.data.notes.FileNoteContentRepository
import com.eskerra.go.data.notes.FileNoteRegistryRepository
import com.eskerra.go.data.notes.FileNoteWriteRepository
import com.eskerra.go.data.workspace.DataStoreWorkspaceStore
import com.eskerra.go.data.workspace.DefaultRemoteSyncSettingsRepository
import com.eskerra.go.data.workspace.DefaultWorkspaceSetupCompletion
import com.eskerra.go.data.workspace.DefaultWorkspaceSetupRepository

/** Single entry point. Hosts the Compose UI and nothing else. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        var keepSplashOnScreen by mutableStateOf(true)
        splashScreen.setKeepOnScreenCondition { keepSplashOnScreen }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        val workspaceStore = DataStoreWorkspaceStore(applicationContext)
        val bootCacheStore = workspaceStore
        val credentialStore = EncryptedCredentialStore(
            filesDir = filesDir,
            tokenCipher = AndroidKeystoreTokenCipher()
        )
        val gitRepository = JGitWorkspaceRepository()
        val setupCompletion = DefaultWorkspaceSetupCompletion(
            setupRepository = DefaultWorkspaceSetupRepository(gitRepository),
            workspaceStore = workspaceStore,
            credentialStore = credentialStore
        )

        val noteRegistryRepository = FileNoteRegistryRepository()
        val noteContentRepository = FileNoteContentRepository()
        val noteWriteRepository = FileNoteWriteRepository(gitRepository)
        val loadGitStatusSummary = LoadGitStatusSummary(gitRepository)

        val loadInboxSummaries = LoadInboxSummariesCached(
            delegate = LoadInboxSummaries(noteRegistryRepository),
            snapshotStore = FileInboxSnapshotStore()
        )
        val loadNoteForReading = LoadNoteForReading(
            registryRepository = noteRegistryRepository,
            contentRepository = noteContentRepository
        )
        val createInboxNote = CreateInboxNote(
            writeRepository = noteWriteRepository,
            registryRepository = noteRegistryRepository,
            loadGitStatusSummary = loadGitStatusSummary
        )
        val loadEditableNote = LoadEditableNote(
            registryRepository = noteRegistryRepository,
            contentRepository = noteContentRepository
        )
        val saveNote = SaveNote(
            writeRepository = noteWriteRepository,
            registryRepository = noteRegistryRepository,
            loadGitStatusSummary = loadGitStatusSummary
        )

        val remoteSyncRepository = JGitRemoteSyncRepository(gitRepository)
        val loadSyncStatus = LoadSyncStatus(remoteSyncRepository)
        val refreshRemoteSyncStatus = RefreshRemoteSyncStatus(
            remoteSyncRepository = remoteSyncRepository,
            credentialStore = credentialStore,
            loadSyncStatus = loadSyncStatus
        )
        val buildSyncPreflight = BuildSyncPreflight(
            remoteSyncRepository = remoteSyncRepository,
            credentialStore = credentialStore
        )
        val buildSafeSyncDiagnostic = BuildSafeSyncDiagnostic(
            buildSyncPreflight = buildSyncPreflight,
            lastSyncStatusStore = workspaceStore
        )
        val recordLastSyncAttempt = RecordLastSyncAttempt(workspaceStore)
        val reconcileWorkspaceSyncBranch = ReconcileWorkspaceSyncBranch(
            workspaceStore = workspaceStore,
            credentialStore = credentialStore,
            remoteSyncRepository = remoteSyncRepository
        )
        val manualSyncNow = ManualSyncNow(
            remoteSyncRepository = remoteSyncRepository,
            credentialStore = credentialStore,
            registryRepository = noteRegistryRepository,
            loadSyncStatus = loadSyncStatus,
            reconcileWorkspaceSyncBranch = reconcileWorkspaceSyncBranch
        )

        val remoteSyncSettingsRepository = DefaultRemoteSyncSettingsRepository(
            workspaceStore = workspaceStore,
            credentialStore = credentialStore,
            remoteSyncRepository = remoteSyncRepository
        )
        val loadRemoteSyncSettings = LoadRemoteSyncSettings(remoteSyncSettingsRepository)
        val saveRemoteSyncSettings = SaveRemoteSyncSettings(remoteSyncSettingsRepository)
        val updateSyncToken = UpdateSyncToken(remoteSyncSettingsRepository)
        val clearRemoteSyncSettings = ClearRemoteSyncSettings(remoteSyncSettingsRepository)
        val testRemoteConnection = TestRemoteConnection(remoteSyncSettingsRepository)

        setContent {
            AppRoot(
                workspaceStore = workspaceStore,
                bootCacheStore = bootCacheStore,
                setupCompletion = setupCompletion,
                filesDir = filesDir,
                loadInboxSummaries = loadInboxSummaries,
                loadNoteForReading = loadNoteForReading,
                createInboxNote = createInboxNote,
                loadEditableNote = loadEditableNote,
                saveNote = saveNote,
                loadGitStatusSummary = loadGitStatusSummary,
                loadSyncStatus = loadSyncStatus,
                refreshRemoteSyncStatus = refreshRemoteSyncStatus,
                buildSyncPreflight = buildSyncPreflight,
                buildSafeSyncDiagnostic = buildSafeSyncDiagnostic,
                manualSyncNow = manualSyncNow,
                recordLastSyncAttempt = recordLastSyncAttempt,
                loadRemoteSyncSettings = loadRemoteSyncSettings,
                saveRemoteSyncSettings = saveRemoteSyncSettings,
                updateSyncToken = updateSyncToken,
                clearRemoteSyncSettings = clearRemoteSyncSettings,
                testRemoteConnection = testRemoteConnection,
                reconcileWorkspaceSyncBranch = reconcileWorkspaceSyncBranch,
                onLaunchSettled = {
                    if (keepSplashOnScreen) {
                        keepSplashOnScreen = false
                    }
                }
            )
        }
    }
}
