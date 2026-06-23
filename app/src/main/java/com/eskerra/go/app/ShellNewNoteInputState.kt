package com.eskerra.go.app

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eskerra.go.core.model.AppShellMode
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.ActiveTodayHubStore
import com.eskerra.go.core.usecase.CreateInboxNote
import com.eskerra.go.core.usecase.TouchVaultSearchPaths
import com.eskerra.go.feature.editor.CreateInboxUiState
import java.io.File
import kotlinx.coroutines.CoroutineScope

internal data class ShellNewNoteInputState(
    val visible: Boolean,
    val draft: String,
    val canSave: Boolean,
    val isSaving: Boolean,
    val errorMessage: String?,
    val onDraftChange: (String) -> Unit,
    val onSave: () -> Unit
)

@Composable
internal fun rememberShellNewNoteInputState(
    currentConfig: WorkspaceConfig,
    filesDir: File,
    createInboxNote: CreateInboxNote,
    activeTodayHubStore: ActiveTodayHubStore,
    touchVaultSearchPaths: TouchVaultSearchPaths,
    appSyncViewModel: AppSyncViewModel,
    scope: CoroutineScope,
    currentRoute: String?,
    selectedTopLevelRoute: String?,
    markInboxNotesChanged: () -> Unit
): ShellNewNoteInputState {
    val context = LocalContext.current
    val createInboxNoteViewModel: CreateInboxNoteViewModel = viewModel(
        key = currentConfig.createInboxNoteViewModelKey(),
        factory = CreateInboxNoteViewModel.factory(
            config = currentConfig,
            filesDir = filesDir,
            createInboxNote = createInboxNote,
            activeTodayHubStore = activeTodayHubStore
        )
    )
    val createInboxState by createInboxNoteViewModel.uiState.collectAsState()
    val createInboxContent = createInboxState as? CreateInboxUiState.Content
    val selectedShellMode = when (selectedTopLevelRoute) {
        AppRoute.PODCASTS_GRAPH -> AppShellMode.PODCASTS
        else -> AppShellMode.HOME
    }

    LaunchedEffect(createInboxNoteViewModel, currentConfig, filesDir, appSyncViewModel) {
        createInboxNoteViewModel.savedNoteEvents.collect { noteId ->
            markInboxNotesChanged()
            appSyncViewModel.refreshLocalStatusQuietly()
            scope.touchVaultSearchPathsAsync(
                touchVaultSearchPaths,
                currentConfig,
                filesDir,
                listOf(noteId.value)
            )
            Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
        }
    }

    return ShellNewNoteInputState(
        visible = shouldShowNewNoteInput(currentRoute, selectedShellMode),
        draft = createInboxContent?.draft.orEmpty(),
        canSave = createInboxContent?.canSave == true,
        isSaving = createInboxContent?.isSaving == true,
        errorMessage = createInboxContent?.errorMessage,
        onDraftChange = createInboxNoteViewModel::updateDraft,
        onSave = createInboxNoteViewModel::save
    )
}

private fun WorkspaceConfig.createInboxNoteViewModelKey(): String =
    "create-inbox:$relativePath:${remoteUri.orEmpty()}:$branch"
