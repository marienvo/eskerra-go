package com.eskerra.go.data.sync

import android.content.Context
import com.eskerra.go.core.repository.SyncStateRepository
import com.eskerra.go.core.repository.VaultSyncScheduler
import com.eskerra.go.core.usecase.BuildSafeSyncDiagnostic
import com.eskerra.go.core.usecase.BuildSyncPreflight
import com.eskerra.go.core.usecase.LoadSyncStatus
import com.eskerra.go.core.usecase.ManualSyncNow
import com.eskerra.go.core.usecase.ReconcileWorkspaceSyncBranch
import com.eskerra.go.core.usecase.RecordLastSyncAttempt
import com.eskerra.go.core.usecase.RefreshRemoteSyncStatus
import com.eskerra.go.data.credentials.AndroidKeystoreTokenCipher
import com.eskerra.go.data.credentials.CredentialStore
import com.eskerra.go.data.credentials.EncryptedCredentialStore
import com.eskerra.go.data.git.GitSyncMutex
import com.eskerra.go.data.git.JGitRemoteSyncRepository
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.notes.CoalescingNoteRegistryRepository
import com.eskerra.go.data.notes.FileNoteContentRepository
import com.eskerra.go.data.notes.FileNoteRegistryRepository
import com.eskerra.go.data.notes.FileNoteRegistrySnapshotStore
import com.eskerra.go.data.notes.NoteContentCache
import com.eskerra.go.data.notes.NoteRegistryCache
import com.eskerra.go.data.workspace.DataStoreWorkspaceStore
import java.io.File

interface SyncRuntimeProvider {
    val syncRuntime: SyncRuntime
}

class SyncRuntime(
    val context: Context,
    val workspaceStore: DataStoreWorkspaceStore,
    val credentialStore: CredentialStore,
    val gitSyncMutex: GitSyncMutex,
    val syncStateRepository: SyncStateRepository,
    val remoteSyncRepository: JGitRemoteSyncRepository,
    val noteRegistryRepository: CoalescingNoteRegistryRepository,
    val noteRegistryCache: NoteRegistryCache,
    val noteContentCache: NoteContentCache,
    val loadSyncStatus: LoadSyncStatus,
    val refreshRemoteSyncStatus: RefreshRemoteSyncStatus,
    val buildSyncPreflight: BuildSyncPreflight,
    val buildSafeSyncDiagnostic: BuildSafeSyncDiagnostic,
    val recordLastSyncAttempt: RecordLastSyncAttempt,
    val reconcileWorkspaceSyncBranch: ReconcileWorkspaceSyncBranch,
    val manualSyncNow: ManualSyncNow,
    val vaultSyncScheduler: VaultSyncScheduler
) {
    companion object {
        fun create(context: Context, filesDir: File = context.filesDir): SyncRuntime {
            val workspaceStore = DataStoreWorkspaceStore(context)
            val credentialStore = EncryptedCredentialStore(
                filesDir = filesDir,
                tokenCipher = AndroidKeystoreTokenCipher()
            )
            val gitSyncMutex = GitSyncMutex()
            val syncStateRepository = DataStoreSyncStateRepository(context)

            val gitRepository = JGitWorkspaceRepository()
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

            val fileNoteRegistryRepository = FileNoteRegistryRepository()
            val noteRegistryRepository =
                CoalescingNoteRegistryRepository(fileNoteRegistryRepository)
            val noteRegistryCache = NoteRegistryCache(
                repository = noteRegistryRepository,
                snapshotStore = FileNoteRegistrySnapshotStore()
            )
            val noteContentCache = NoteContentCache(FileNoteContentRepository())

            val reconcileWorkspaceSyncBranch = ReconcileWorkspaceSyncBranch(
                workspaceStore = workspaceStore,
                credentialStore = credentialStore,
                remoteSyncRepository = remoteSyncRepository,
                gitSyncMutex = gitSyncMutex
            )

            val manualSyncNow = ManualSyncNow(
                remoteSyncRepository = remoteSyncRepository,
                credentialStore = credentialStore,
                registryCache = noteRegistryCache,
                contentCache = noteContentCache,
                loadSyncStatus = loadSyncStatus,
                reconcileWorkspaceSyncBranch = reconcileWorkspaceSyncBranch,
                gitSyncMutex = gitSyncMutex
            )

            val vaultSyncScheduler = WorkManagerVaultSyncScheduler(context)

            return SyncRuntime(
                context = context,
                workspaceStore = workspaceStore,
                credentialStore = credentialStore,
                gitSyncMutex = gitSyncMutex,
                syncStateRepository = syncStateRepository,
                remoteSyncRepository = remoteSyncRepository,
                noteRegistryRepository = noteRegistryRepository,
                noteRegistryCache = noteRegistryCache,
                noteContentCache = noteContentCache,
                loadSyncStatus = loadSyncStatus,
                refreshRemoteSyncStatus = refreshRemoteSyncStatus,
                buildSyncPreflight = buildSyncPreflight,
                buildSafeSyncDiagnostic = buildSafeSyncDiagnostic,
                recordLastSyncAttempt = recordLastSyncAttempt,
                reconcileWorkspaceSyncBranch = reconcileWorkspaceSyncBranch,
                manualSyncNow = manualSyncNow,
                vaultSyncScheduler = vaultSyncScheduler
            )
        }
    }
}
