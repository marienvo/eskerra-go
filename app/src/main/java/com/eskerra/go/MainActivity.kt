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
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.eskerra.go.app.AppRoot
import com.eskerra.go.app.PodcastPlaylistWiring
import com.eskerra.go.app.PodcastShellStateWiring
import com.eskerra.go.core.repository.PodcastPlayerDriver
import com.eskerra.go.core.usecase.BuildSafeSyncDiagnostic
import com.eskerra.go.core.usecase.BuildSyncPreflight
import com.eskerra.go.core.usecase.ClearPlaylist
import com.eskerra.go.core.usecase.ClearPodcastPlaybackSnapshot
import com.eskerra.go.core.usecase.ClearRemoteSyncSettings
import com.eskerra.go.core.usecase.CreateInboxNote
import com.eskerra.go.core.usecase.DeleteInboxNotes
import com.eskerra.go.core.usecase.EnsureDeviceInstanceId
import com.eskerra.go.core.usecase.LoadDownloadedBinaries
import com.eskerra.go.core.usecase.LoadEditableNote
import com.eskerra.go.core.usecase.LoadGitStatusSummary
import com.eskerra.go.core.usecase.LoadInboxSummaries
import com.eskerra.go.core.usecase.LoadInboxSummariesCached
import com.eskerra.go.core.usecase.LoadLocalSettings
import com.eskerra.go.core.usecase.LoadNoteForReading
import com.eskerra.go.core.usecase.LoadPodcastArtwork
import com.eskerra.go.core.usecase.LoadPodcastCatalog
import com.eskerra.go.core.usecase.LoadRemoteSyncSettings
import com.eskerra.go.core.usecase.LoadSyncStatus
import com.eskerra.go.core.usecase.LoadTodayHub
import com.eskerra.go.core.usecase.LoadTodayHubRow
import com.eskerra.go.core.usecase.LoadVaultSettings
import com.eskerra.go.core.usecase.MaintainVaultSearchIndex
import com.eskerra.go.core.usecase.ManualSyncNow
import com.eskerra.go.core.usecase.MarkPodcastEpisodesPlayed
import com.eskerra.go.core.usecase.PersistAppShellMode
import com.eskerra.go.core.usecase.PersistPodcastPlaybackSnapshot
import com.eskerra.go.core.usecase.PodcastPlaylistSync
import com.eskerra.go.core.usecase.PrefetchLinkedNotes
import com.eskerra.go.core.usecase.ReadPlaylist
import com.eskerra.go.core.usecase.ReconcileWorkspaceSyncBranch
import com.eskerra.go.core.usecase.RecordLastSyncAttempt
import com.eskerra.go.core.usecase.RefreshRemoteSyncStatus
import com.eskerra.go.core.usecase.RepairVaultSearchIndex
import com.eskerra.go.core.usecase.RestorePodcastPlayback
import com.eskerra.go.core.usecase.SaveLocalSettings
import com.eskerra.go.core.usecase.SaveNote
import com.eskerra.go.core.usecase.SaveRemoteSyncSettings
import com.eskerra.go.core.usecase.SaveVaultSettings
import com.eskerra.go.core.usecase.SearchVault
import com.eskerra.go.core.usecase.SyncBinaries
import com.eskerra.go.core.usecase.SyncPodcastChange
import com.eskerra.go.core.usecase.SyncPodcastChangesViaVaultSync
import com.eskerra.go.core.usecase.SyncPodcastVaultRefresh
import com.eskerra.go.core.usecase.TestRemoteConnection
import com.eskerra.go.core.usecase.TouchVaultSearchPaths
import com.eskerra.go.core.usecase.UpdateSyncToken
import com.eskerra.go.core.usecase.WritePlaylist
import com.eskerra.go.data.credentials.AndroidKeystoreTokenCipher
import com.eskerra.go.data.credentials.EncryptedCredentialStore
import com.eskerra.go.data.git.GitSyncMutex
import com.eskerra.go.data.git.JGitRemoteSyncRepository
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.notes.CoalescingNoteRegistryRepository
import com.eskerra.go.data.notes.FileInboxSnapshotStore
import com.eskerra.go.data.notes.FileNoteContentRepository
import com.eskerra.go.data.notes.FileNoteRegistryRepository
import com.eskerra.go.data.notes.FileNoteRegistrySnapshotStore
import com.eskerra.go.data.notes.FileNoteWriteRepository
import com.eskerra.go.data.notes.NoteContentCache
import com.eskerra.go.data.notes.NoteRegistryCache
import com.eskerra.go.data.notes.ParsedMarkdownCache
import com.eskerra.go.data.player.Media3PodcastPlayerDriver
import com.eskerra.go.data.podcast.FilePodcastCatalogRepository
import com.eskerra.go.data.podcast.FilePodcastCatalogSnapshotStore
import com.eskerra.go.data.podcast.FilePodcastFileRepository
import com.eskerra.go.data.podcast.artwork.FilePodcastArtworkRepository
import com.eskerra.go.data.podcast.rss.FilePodcastRssVaultSync
import com.eskerra.go.data.podcast.rss.OkHttpRssFeedFetcher
import com.eskerra.go.data.r2.BinaryManifestStore
import com.eskerra.go.data.r2.DefaultBinarySyncRepository
import com.eskerra.go.data.r2.PlaylistR2ConditionalFetch
import com.eskerra.go.data.r2.R2BinaryObjectClient
import com.eskerra.go.data.r2.R2PlaylistConditionalClient
import com.eskerra.go.data.r2.R2PlaylistObjectClient
import com.eskerra.go.data.r2.R2PlaylistSyncRepository
import com.eskerra.go.data.search.SqliteVaultSearchRepository
import com.eskerra.go.data.todayhub.DataStoreActiveTodayHubStore
import com.eskerra.go.data.todayhub.FileTodayHubSnapshotStore
import com.eskerra.go.data.vault.DataStoreLocalSettingsStore
import com.eskerra.go.data.vault.FileVaultSettingsRepository
import com.eskerra.go.data.workspace.DataStoreWorkspaceStore
import com.eskerra.go.data.workspace.DefaultRemoteSyncSettingsRepository
import com.eskerra.go.data.workspace.DefaultWorkspaceSetupCompletion
import com.eskerra.go.data.workspace.DefaultWorkspaceSetupRepository
import com.eskerra.go.data.workspace.GateFingerprintComputer
import java.io.File
import okhttp3.OkHttpClient

/** Single entry point. Hosts the Compose UI and nothing else. */
class MainActivity : ComponentActivity() {
    private var podcastPlayerDriver: PodcastPlayerDriver? = null

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

        val fileNoteRegistryRepository = FileNoteRegistryRepository()
        val noteRegistryRepository = CoalescingNoteRegistryRepository(fileNoteRegistryRepository)
        val noteContentCache = NoteContentCache(FileNoteContentRepository())
        val parsedMarkdownCache = ParsedMarkdownCache()
        val noteWriteRepository = FileNoteWriteRepository(gitRepository)
        val loadGitStatusSummary = LoadGitStatusSummary(gitRepository)

        val noteRegistryCache = NoteRegistryCache(
            repository = noteRegistryRepository,
            snapshotStore = FileNoteRegistrySnapshotStore()
        )

        val loadInboxSummaries = LoadInboxSummariesCached(
            delegate = LoadInboxSummaries(noteRegistryCache),
            snapshotStore = FileInboxSnapshotStore()
        )
        val loadNoteForReading = LoadNoteForReading(
            registryCache = noteRegistryCache,
            contentRepository = noteContentCache
        )
        val prefetchLinkedNotes = PrefetchLinkedNotes(
            contentCache = noteContentCache,
            parsedMarkdownCache = parsedMarkdownCache
        )
        val createInboxNote = CreateInboxNote(
            writeRepository = noteWriteRepository,
            registryCache = noteRegistryCache,
            loadGitStatusSummary = loadGitStatusSummary
        )
        val deleteInboxNotes = DeleteInboxNotes(
            writeRepository = noteWriteRepository,
            registryCache = noteRegistryCache,
            loadGitStatusSummary = loadGitStatusSummary
        )
        val loadEditableNote = LoadEditableNote(
            registryRepository = noteRegistryRepository,
            contentRepository = noteContentCache
        )
        val saveNote = SaveNote(
            writeRepository = noteWriteRepository,
            registryCache = noteRegistryCache,
            loadGitStatusSummary = loadGitStatusSummary,
            contentCache = noteContentCache
        )

        val loadTodayHub = LoadTodayHub(
            registryCache = noteRegistryCache,
            contentRepository = noteContentCache
        )
        val loadTodayHubRow = LoadTodayHubRow(contentRepository = noteContentCache)
        val activeTodayHubStore = DataStoreActiveTodayHubStore(applicationContext)
        val todayHubSnapshotStore = FileTodayHubSnapshotStore()

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
        val gitSyncMutex = GitSyncMutex()
        val manualSyncNow = ManualSyncNow(
            remoteSyncRepository = remoteSyncRepository,
            credentialStore = credentialStore,
            registryCache = noteRegistryCache,
            contentCache = noteContentCache,
            loadSyncStatus = loadSyncStatus,
            reconcileWorkspaceSyncBranch = reconcileWorkspaceSyncBranch,
            gitSyncMutex = gitSyncMutex
        )

        val localSettingsStore = DataStoreLocalSettingsStore(applicationContext)
        val vaultSettingsRepository = FileVaultSettingsRepository(
            applicationContext,
            localSettingsStore
        )
        val loadVaultSettings = LoadVaultSettings(vaultSettingsRepository)
        val saveVaultSettings = SaveVaultSettings(vaultSettingsRepository)
        val loadLocalSettings = LoadLocalSettings(localSettingsStore)
        val saveLocalSettings = SaveLocalSettings(localSettingsStore)
        val ensureDeviceInstanceId = EnsureDeviceInstanceId(localSettingsStore)

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

        val vaultSearchRepository = SqliteVaultSearchRepository(applicationContext)
        val searchVault = SearchVault(vaultSearchRepository)
        val maintainVaultSearchIndex = MaintainVaultSearchIndex(vaultSearchRepository)
        val repairVaultSearchIndex = RepairVaultSearchIndex(vaultSearchRepository)
        val touchVaultSearchPaths = TouchVaultSearchPaths(vaultSearchRepository)

        val loadPodcastCatalog = LoadPodcastCatalog(FilePodcastCatalogRepository())
        val catalogSnapshotStore = FilePodcastCatalogSnapshotStore()
        val syncMarkPlayedChange = SyncPodcastChange(
            remoteSyncRepository = remoteSyncRepository,
            credentialStore = credentialStore,
            gitSyncMutex = gitSyncMutex,
            commitMessage = "Mark podcast episodes played"
        )
        val markPodcastEpisodesPlayed = MarkPodcastEpisodesPlayed(
            podcastFileRepository = FilePodcastFileRepository(),
            syncPodcastChange = syncMarkPlayedChange::invoke
        )
        // Podcast refresh commits + merges + pushes through the unconditional sync engine.
        val syncRefreshChange = SyncPodcastChangesViaVaultSync(
            runVaultSync = { cfg, files -> manualSyncNow(cfg, files) }
        )
        val okHttpClient = OkHttpClient()
        installImageLoader(okHttpClient)
        val rssFeedFetcher = OkHttpRssFeedFetcher(okHttpClient)
        val syncPodcastVaultRefresh = SyncPodcastVaultRefresh(
            vaultSync = FilePodcastRssVaultSync(fetcher = rssFeedFetcher),
            syncPodcastChange = syncRefreshChange::invoke
        )
        val loadPodcastArtwork = LoadPodcastArtwork(
            repository = FilePodcastArtworkRepository(filesDir, okHttpClient),
            fetchRssXml = { url ->
                rssFeedFetcher.fetch(url, FilePodcastArtworkRepository.DOWNLOAD_TIMEOUT_MS)
            },
            workspaceKeyFor = { config, dir ->
                GateFingerprintComputer.compute(config, dir).value
            }
        )
        val podcastPlayerDriver = Media3PodcastPlayerDriver(applicationContext)
            .also { this.podcastPlayerDriver = it }

        val r2HttpClient = okHttpClient
        val r2PlaylistObjectClient = R2PlaylistObjectClient(r2HttpClient)
        val binarySyncRepository = DefaultBinarySyncRepository(
            objectClient = R2BinaryObjectClient(r2HttpClient),
            manifestStore = BinaryManifestStore(filesDir)
        )
        val syncBinaries = SyncBinaries(binarySyncRepository, loadVaultSettings)
        val loadDownloadedBinaries = LoadDownloadedBinaries(binarySyncRepository)
        val playlistSyncRepository = R2PlaylistSyncRepository(
            settingsRepository = vaultSettingsRepository,
            localSettingsStore = localSettingsStore,
            r2Client = r2PlaylistObjectClient
        )
        val readPlaylist = ReadPlaylist(playlistSyncRepository)
        val writePlaylist = WritePlaylist(playlistSyncRepository)
        val clearPlaylist = ClearPlaylist(playlistSyncRepository)
        val podcastPlaylistSync = PodcastPlaylistSync(
            readPlaylist = readPlaylist,
            writePlaylist = writePlaylist,
            clearPlaylist = clearPlaylist,
            loadVaultSettings = loadVaultSettings,
            ensureDeviceInstanceId = ensureDeviceInstanceId
        )
        val playlistR2ConditionalFetch = PlaylistR2ConditionalFetch(
            loadVaultSettings = loadVaultSettings,
            conditionalClient = R2PlaylistConditionalClient(r2HttpClient)
        )

        val podcastPlaylistWiring = PodcastPlaylistWiring(
            sync = podcastPlaylistSync,
            repository = playlistSyncRepository,
            conditionalFetch = playlistR2ConditionalFetch
        )
        val restorePodcastPlayback = RestorePodcastPlayback(
            loadPodcastCatalog = loadPodcastCatalog,
            podcastPlaylistSync = podcastPlaylistSync,
            localSettingsStore = localSettingsStore,
            podcastPlayerDriver = podcastPlayerDriver
        )
        val persistAppShellMode = PersistAppShellMode(localSettingsStore)
        val persistPodcastPlaybackSnapshot = PersistPodcastPlaybackSnapshot(localSettingsStore)
        val clearPodcastPlaybackSnapshot = ClearPodcastPlaybackSnapshot(localSettingsStore)

        val podcastShellStateWiring = PodcastShellStateWiring(
            restorePodcastPlayback = restorePodcastPlayback,
            persistAppShellMode = persistAppShellMode,
            persistPodcastPlaybackSnapshot = persistPodcastPlaybackSnapshot,
            clearPodcastPlaybackSnapshot = clearPodcastPlaybackSnapshot
        )

        setContent {
            AppRoot(
                workspaceStore = workspaceStore,
                bootCacheStore = bootCacheStore,
                setupCompletion = setupCompletion,
                filesDir = filesDir,
                parsedMarkdownCache = parsedMarkdownCache,
                loadInboxSummaries = loadInboxSummaries,
                loadNoteForReading = loadNoteForReading,
                prefetchLinkedNotes = prefetchLinkedNotes,
                createInboxNote = createInboxNote,
                deleteInboxNotes = deleteInboxNotes,
                loadEditableNote = loadEditableNote,
                saveNote = saveNote,
                loadGitStatusSummary = loadGitStatusSummary,
                loadTodayHub = loadTodayHub,
                loadTodayHubRow = loadTodayHubRow,
                activeTodayHubStore = activeTodayHubStore,
                todayHubSnapshotStore = todayHubSnapshotStore,
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
                loadVaultSettings = loadVaultSettings,
                saveVaultSettings = saveVaultSettings,
                loadLocalSettings = loadLocalSettings,
                saveLocalSettings = saveLocalSettings,
                ensureDeviceInstanceId = ensureDeviceInstanceId,
                syncBinaries = syncBinaries,
                loadDownloadedBinaries = loadDownloadedBinaries,
                searchVault = searchVault,
                maintainVaultSearchIndex = maintainVaultSearchIndex,
                repairVaultSearchIndex = repairVaultSearchIndex,
                touchVaultSearchPaths = touchVaultSearchPaths,
                loadPodcastCatalog = loadPodcastCatalog,
                markPodcastEpisodesPlayed = markPodcastEpisodesPlayed,
                podcastPlaylistWiring = podcastPlaylistWiring,
                loadPodcastArtwork = loadPodcastArtwork,
                podcastPlayerDriver = podcastPlayerDriver,
                syncPodcastVaultRefresh = syncPodcastVaultRefresh,
                catalogSnapshotStore = catalogSnapshotStore,
                podcastShellStateWiring = podcastShellStateWiring,
                onLaunchSettled = {
                    if (keepSplashOnScreen) {
                        keepSplashOnScreen = false
                    }
                }
            )
        }
    }

    override fun onDestroy() {
        podcastPlayerDriver?.release()
        podcastPlayerDriver = null
        super.onDestroy()
    }

    private fun installImageLoader(okHttpClient: OkHttpClient) {
        Coil.setImageLoader(
            ImageLoader.Builder(applicationContext)
                .okHttpClient(okHttpClient)
                .memoryCache {
                    MemoryCache.Builder(applicationContext)
                        .maxSizePercent(0.20)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(File(cacheDir, COIL_DISK_CACHE_DIR))
                        .maxSizeBytes(COIL_DISK_CACHE_MAX_SIZE_BYTES)
                        .build()
                }
                .respectCacheHeaders(false)
                .build()
        )
    }

    companion object {
        private const val COIL_DISK_CACHE_DIR = "coil-image-cache"
        private const val COIL_DISK_CACHE_MAX_SIZE_BYTES = 100L * 1024L * 1024L
    }
}
