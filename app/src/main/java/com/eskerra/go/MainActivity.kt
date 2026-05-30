package com.eskerra.go

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.eskerra.go.app.AppRoot
import com.eskerra.go.data.credentials.AppPrivateCredentialStore
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.notes.FileNoteRegistryRepository
import com.eskerra.go.data.notes.LoadInboxSummaries
import com.eskerra.go.data.workspace.DataStoreWorkspaceStore
import com.eskerra.go.data.workspace.DefaultWorkspaceSetupCompletion
import com.eskerra.go.data.workspace.DefaultWorkspaceSetupRepository

/** Single entry point. Hosts the Compose UI and nothing else. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val workspaceStore = DataStoreWorkspaceStore(applicationContext)
        val credentialStore = AppPrivateCredentialStore(filesDir)
        val setupCompletion = DefaultWorkspaceSetupCompletion(
            setupRepository = DefaultWorkspaceSetupRepository(JGitWorkspaceRepository()),
            workspaceStore = workspaceStore,
            credentialStore = credentialStore
        )

        val loadInboxSummaries = LoadInboxSummaries(FileNoteRegistryRepository())

        setContent {
            AppRoot(
                workspaceStore = workspaceStore,
                setupCompletion = setupCompletion,
                filesDir = filesDir,
                loadInboxSummaries = loadInboxSummaries
            )
        }
    }
}
