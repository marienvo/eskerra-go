package com.eskerra.go

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.eskerra.go.app.AppRoot
import com.eskerra.go.core.usecase.CreateInboxNote
import com.eskerra.go.core.usecase.LoadEditableNote
import com.eskerra.go.core.usecase.LoadGitStatusSummary
import com.eskerra.go.core.usecase.LoadInboxSummaries
import com.eskerra.go.core.usecase.LoadNoteForReading
import com.eskerra.go.core.usecase.SaveNote
import com.eskerra.go.data.credentials.AppPrivateCredentialStore
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.notes.FileNoteContentRepository
import com.eskerra.go.data.notes.FileNoteRegistryRepository
import com.eskerra.go.data.notes.FileNoteWriteRepository
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

        val loadInboxSummaries = LoadInboxSummaries(noteRegistryRepository)
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

        setContent {
            AppRoot(
                workspaceStore = workspaceStore,
                setupCompletion = setupCompletion,
                filesDir = filesDir,
                loadInboxSummaries = loadInboxSummaries,
                loadNoteForReading = loadNoteForReading,
                createInboxNote = createInboxNote,
                loadEditableNote = loadEditableNote,
                saveNote = saveNote,
                loadGitStatusSummary = loadGitStatusSummary
            )
        }
    }
}
