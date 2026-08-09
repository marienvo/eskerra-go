package com.eskerra.go

import com.eskerra.go.app.PodcastPlaylistWiring
import com.eskerra.go.app.PodcastShellStateWiring
import com.eskerra.go.core.repository.LocalSettingsStore
import com.eskerra.go.core.repository.PodcastPlayerDriver
import com.eskerra.go.core.repository.VaultSettingsRepository
import com.eskerra.go.core.usecase.ClearPlaylist
import com.eskerra.go.core.usecase.ClearPodcastPlaybackSnapshot
import com.eskerra.go.core.usecase.EnsureDeviceInstanceId
import com.eskerra.go.core.usecase.LoadDownloadedBinaries
import com.eskerra.go.core.usecase.LoadPodcastCatalog
import com.eskerra.go.core.usecase.LoadVaultSettings
import com.eskerra.go.core.usecase.PersistAppShellMode
import com.eskerra.go.core.usecase.PersistPodcastPlaybackSnapshot
import com.eskerra.go.core.usecase.PodcastPlaylistSync
import com.eskerra.go.core.usecase.ReadPlaylist
import com.eskerra.go.core.usecase.RestorePodcastPlayback
import com.eskerra.go.core.usecase.SyncBinaries
import com.eskerra.go.core.usecase.WritePlaylist
import com.eskerra.go.data.r2.BinaryManifestStore
import com.eskerra.go.data.r2.DefaultBinarySyncRepository
import com.eskerra.go.data.r2.PlaylistR2ConditionalFetch
import com.eskerra.go.data.r2.R2BinaryObjectClient
import com.eskerra.go.data.r2.R2PlaylistConditionalClient
import com.eskerra.go.data.r2.R2PlaylistObjectClient
import com.eskerra.go.data.r2.R2PlaylistSyncRepository
import java.io.File
import okhttp3.OkHttpClient

/**
 * The R2-backed half of the composition root: binary sync, the podcast playlist, and the shell
 * state that restores playback. Lifted out of [MainActivity] verbatim to keep that file inside
 * its size budget; construction order and arguments are unchanged.
 */
data class PodcastCompositionRoot(
    val syncBinaries: SyncBinaries,
    val loadDownloadedBinaries: LoadDownloadedBinaries,
    val podcastPlaylistWiring: PodcastPlaylistWiring,
    val podcastShellStateWiring: PodcastShellStateWiring
)

@Suppress("LongParameterList")
fun buildPodcastCompositionRoot(
    okHttpClient: OkHttpClient,
    filesDir: File,
    vaultSettingsRepository: VaultSettingsRepository,
    localSettingsStore: LocalSettingsStore,
    loadVaultSettings: LoadVaultSettings,
    ensureDeviceInstanceId: EnsureDeviceInstanceId,
    loadPodcastCatalog: LoadPodcastCatalog,
    podcastPlayerDriver: PodcastPlayerDriver
): PodcastCompositionRoot {
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

    return PodcastCompositionRoot(
        syncBinaries = syncBinaries,
        loadDownloadedBinaries = loadDownloadedBinaries,
        podcastPlaylistWiring = podcastPlaylistWiring,
        podcastShellStateWiring = podcastShellStateWiring
    )
}
