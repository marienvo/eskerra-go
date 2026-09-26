package com.eskerra.go

import com.eskerra.go.core.usecase.LoadDownloadedBinaries
import com.eskerra.go.core.usecase.LoadVaultSettings
import com.eskerra.go.core.usecase.SyncBinaries
import com.eskerra.go.data.r2.BinaryManifestStore
import com.eskerra.go.data.r2.DefaultBinarySyncRepository
import com.eskerra.go.data.r2.R2BinaryObjectClient
import java.io.File
import okhttp3.OkHttpClient

data class BinariesCompositionRoot(
    val syncBinaries: SyncBinaries,
    val loadDownloadedBinaries: LoadDownloadedBinaries
)

fun buildBinariesCompositionRoot(
    okHttpClient: OkHttpClient,
    filesDir: File,
    loadVaultSettings: LoadVaultSettings
): BinariesCompositionRoot {
    val repository = DefaultBinarySyncRepository(
        objectClient = R2BinaryObjectClient(okHttpClient),
        manifestStore = BinaryManifestStore(filesDir)
    )
    return BinariesCompositionRoot(
        syncBinaries = SyncBinaries(repository, loadVaultSettings),
        loadDownloadedBinaries = LoadDownloadedBinaries(repository)
    )
}
