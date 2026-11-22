package com.eskerra.go.app

import android.widget.Toast
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.feature.note.LocalDatedHeader
import com.eskerra.go.feature.note.NoteReaderUiState
import com.eskerra.go.feature.note.NoteReaderViewModel
import com.eskerra.go.feature.note.NoteScreen
import com.eskerra.go.feature.todayhub.TodayHubUiState
import com.eskerra.go.ui.markdown.AmbiguousWikiLinkSheet
import com.eskerra.go.ui.markdown.LocalParsedMarkdownCache

internal fun NavGraphBuilder.noteReaderGraph(ctx: AppNavGraphContext) {
    opaqueComposable(
        route = AppRoute.NOTE_PATTERN,
        arguments = listOf(
            navArgument(AppRoute.NOTE_ARG) { type = NavType.StringType },
            navArgument(AppRoute.NOTE_SOURCE_ARG) {
                type = NavType.StringType
                defaultValue = NoteOpenSource.IN_APP.routeValue
            }
        )
    ) { entry ->
        val noteRouteScope = rememberCoroutineScope()
        val raw = entry.arguments?.getString(AppRoute.NOTE_ARG).orEmpty()
        val noteId = AppRoute.decodeNoteId(raw)
        val parsedMarkdownCache = LocalParsedMarkdownCache.current
        val noteReaderViewModel: NoteReaderViewModel = viewModel(
            factory = NoteReaderViewModel.factory(
                config = ctx.currentConfig,
                filesDir = ctx.filesDir,
                noteId = noteId,
                loadNoteForReading = ctx.loadNoteForReading,
                parsedMarkdownCache = parsedMarkdownCache,
                notePrefetchScheduler = ctx.notePrefetchScheduler,
                setDatedNoteReadStatus = ctx.setDatedNoteReadStatus,
                operationScope = ctx.scope
            )
        )
        val readerState by noteReaderViewModel.uiState.collectAsState()

        LaunchedEffect(ctx.currentRoute) {
            if (consumeNoteReaderChanged(ctx.currentRoute, noteId, entry.savedStateHandle)) {
                noteReaderViewModel.retry()
            }
        }

        val noteReaderContext = LocalContext.current
        var ambiguousCandidates by remember { mutableStateOf<List<NoteId>?>(null) }
        var readStatusSyncGeneration by remember(noteId) { mutableStateOf<Long?>(null) }
        val durableSyncRecord by ctx.appSyncViewModel.durableSyncRecord.collectAsState(
            initial = null
        )
        val readStatusSyncing = isDatedReadStatusSyncing(
            readStatusSyncGeneration,
            durableSyncRecord
        )
        LaunchedEffect(durableSyncRecord, readStatusSyncGeneration) {
            val generation = readStatusSyncGeneration ?: return@LaunchedEffect
            val record = durableSyncRecord ?: return@LaunchedEffect
            if (record.requestedGeneration < generation) return@LaunchedEffect
            if (record.completedGeneration >= generation) {
                readStatusSyncGeneration = null
            } else if (isDatedReadStatusSyncFailed(generation, record)) {
                readStatusSyncGeneration = null
                Toast.makeText(
                    noteReaderContext,
                    "Read status is saved locally, but has not synced yet.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val notificationOpen = AppRoute.isNotificationNoteSource(
            entry.arguments?.getString(AppRoute.NOTE_SOURCE_ARG)
        )
        val datedHeader = datedNoteHeader(
            ctx,
            noteId,
            (readerState as? NoteReaderUiState.Content)?.document,
            ctx.todayHubUiState as? TodayHubUiState.Content,
            notificationOpen,
            ctx.todayHubViewModel::selectHubFromNotification
        )

        CompositionLocalProvider(LocalDatedHeader provides datedHeader) {
            NoteScreen(
                state = readerState,
                onRetry = noteReaderViewModel::retry,
                onEdit = { ctx.navController.navigate(AppRoute.editor(noteId)) },
                onOpenInternalNote = { targetId: NoteId ->
                    ctx.noteOpenGate.openNoteWithWarmBudget(
                        noteRouteScope,
                        ctx.warmNote,
                        ctx.currentConfig,
                        ctx.filesDir,
                        ctx.navController,
                        targetId
                    )
                },
                onOpenExternalUrl = { url: String ->
                    openExternalUrl(noteReaderContext, url)
                },
                onAmbiguousWikiLink = { candidates: List<NoteId>, _: String ->
                    ambiguousCandidates = candidates
                },
                onNoteNotFound = { message: String ->
                    showNoteNotFoundToast(noteReaderContext, message)
                },
                onToggleDatedReadStatus = {
                    noteReaderViewModel.toggleDatedNoteReadStatus {
                        ctx.todayHubViewModel.retry()
                        ctx.scope.touchVaultSearchPathsAsync(
                            ctx.touchVaultSearchPaths,
                            ctx.currentConfig,
                            ctx.filesDir,
                            listOf(noteId.value)
                        )
                        ctx.appSyncViewModel.requestAutoSync(
                            onGenerationRequested = { generation ->
                                readStatusSyncGeneration = generation
                            },
                            onUnavailable = {
                                Toast.makeText(
                                    noteReaderContext,
                                    "Read status is saved locally, but has not synced yet.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                },
                readStatusSyncing = readStatusSyncing,
                workspaceRoot = ctx.workspaceRoot,
                onViewportChanged = noteReaderViewModel::onViewportChanged
            )
        }

        val registry = (readerState as? NoteReaderUiState.Content)?.document?.registry
        if (ambiguousCandidates != null && registry != null) {
            AmbiguousWikiLinkSheet(
                candidates = ambiguousCandidates!!,
                registry = registry,
                onPickNote = { picked ->
                    ambiguousCandidates = null
                    ctx.noteOpenGate.openNoteWithWarmBudget(
                        noteRouteScope,
                        ctx.warmNote,
                        ctx.currentConfig,
                        ctx.filesDir,
                        ctx.navController,
                        picked
                    )
                },
                onDismiss = { ambiguousCandidates = null }
            )
        }
    }
}
