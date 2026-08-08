package com.eskerra.go.app

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eskerra.go.core.model.AppShellMode
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.ActiveTodayHubStore
import com.eskerra.go.core.usecase.CreateInboxNote
import com.eskerra.go.core.usecase.TouchVaultSearchPaths
import com.eskerra.go.feature.editor.CreateInboxNoteViewModel
import com.eskerra.go.feature.editor.CreateInboxUiState
import com.eskerra.go.feature.share.ShareIntakeViewModel
import com.eskerra.go.feature.sync.AppSyncViewModel
import java.io.File
import kotlinx.coroutines.CoroutineScope

internal data class ShellNewNoteInputState(
    val visible: Boolean,
    val draft: String,
    val canSave: Boolean,
    val isSaving: Boolean,
    val errorMessage: String?,
    val onDraftChange: (String) -> Unit,
    val onSave: () -> Unit,
    val fieldSignal: ShellFieldSignal?
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
    markInboxNotesChanged: () -> Unit,
    shareIntake: ShareIntake,
    onSharePrefillApplied: () -> Unit
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
    var fieldSignal by remember { mutableStateOf<ShellFieldSignal?>(null) }
    var fieldSignalToken by remember { mutableLongStateOf(0L) }
    val selectedShellMode = when (selectedTopLevelRoute) {
        AppRoute.PODCASTS_GRAPH -> AppShellMode.PODCASTS
        else -> AppShellMode.HOME
    }

    LaunchedEffect(createInboxNoteViewModel, currentConfig, filesDir, appSyncViewModel) {
        createInboxNoteViewModel.savedNoteEvents.collect { noteId ->
            // The note is written: let go of the field so the keyboard drops out of the way
            // instead of hanging over the inbox you just added to.
            fieldSignalToken += 1
            fieldSignal = ShellFieldSignal.ReleaseFocus(fieldSignalToken)
            markInboxNotesChanged()
            appSyncViewModel.requestAutoSync()
            scope.touchVaultSearchPathsAsync(
                touchVaultSearchPaths,
                currentConfig,
                filesDir,
                listOf(noteId.value)
            )
            Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
        }
    }

    val shareIntakeViewModel: ShareIntakeViewModel = viewModel(
        key = "share-intake",
        factory = ShareIntakeViewModel.factory(shareIntake.fetchSharedPageTitle)
    )
    val pendingShare = shareIntake.pendingShare
    LaunchedEffect(pendingShare?.id) {
        val share = pendingShare ?: return@LaunchedEffect
        shareIntakeViewModel.offer(share)
        shareIntake.onShareHandled(share.id)
    }
    LaunchedEffect(shareIntakeViewModel, createInboxNoteViewModel) {
        shareIntakeViewModel.prefills.collect(createInboxNoteViewModel::prefillFromShare)
    }
    val navigateForShare by rememberUpdatedState(onSharePrefillApplied)
    LaunchedEffect(createInboxNoteViewModel) {
        // Only a prefill the composer actually accepted moves the caret or the user: a deferred
        // or dropped one must not steal focus or navigate.
        createInboxNoteViewModel.prefillAppliedEvents.collect { caretOffset ->
            fieldSignalToken += 1
            fieldSignal = ShellFieldSignal.PlaceCaret(fieldSignalToken, caretOffset)
            navigateForShare()
        }
    }

    return ShellNewNoteInputState(
        visible = shouldShowNewNoteInput(currentRoute, selectedShellMode),
        draft = createInboxContent?.draft.orEmpty(),
        canSave = createInboxContent?.canSave == true,
        isSaving = createInboxContent?.isSaving == true,
        errorMessage = createInboxContent?.errorMessage,
        onDraftChange = createInboxNoteViewModel::updateDraft,
        onSave = createInboxNoteViewModel::save,
        fieldSignal = fieldSignal
    )
}

private fun WorkspaceConfig.createInboxNoteViewModelKey(): String =
    "create-inbox:$relativePath:${remoteUri.orEmpty()}:$branch"
