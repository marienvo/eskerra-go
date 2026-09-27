package com.eskerra.go.feature.note

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eskerra.go.core.markdown.PrefetchLinkTargets
import com.eskerra.go.core.markdown.PreparedMarkdown
import com.eskerra.go.core.markdown.PreparedMarkdownLinks
import com.eskerra.go.core.markdown.VaultMarkdownPreprocess
import com.eskerra.go.core.model.NoteContentError
import com.eskerra.go.core.model.NoteContentException
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteReaderDocument
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.ParsedMarkdownCachePort
import com.eskerra.go.core.usecase.LoadNoteForReading
import com.eskerra.go.core.usecase.NotePrefetchScheduler
import com.eskerra.go.data.notes.ParsedMarkdownCache
import com.eskerra.go.data.perf.NoteNavTrace
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NoteReaderViewModel(
    private val config: WorkspaceConfig,
    private val filesDir: File,
    private val noteId: NoteId,
    private val loadNoteForReading: LoadNoteForReading,
    private val parsedMarkdownCache: ParsedMarkdownCachePort = ParsedMarkdownCache(),
    private val notePrefetchScheduler: NotePrefetchScheduler? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<NoteReaderUiState>(NoteReaderUiState.Loading)
    val uiState: StateFlow<NoteReaderUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var currentDocument: NoteReaderDocument? = null
    private var currentPreparedBody: PreparedMarkdown? = null

    init {
        load()
    }

    fun retry() {
        _uiState.value = NoteReaderUiState.Loading
        load()
    }

    private fun load() {
        loadJob?.cancel()
        currentDocument = null
        currentPreparedBody = null
        NoteNavTrace.log("reader.load.start", "noteId=${noteId.value}")
        loadJob = viewModelScope.launch {
            val loadStartMs = System.currentTimeMillis()
            loadNoteForReading(config, filesDir, noteId, viewModelScope).fold(
                onSuccess = { document ->
                    NoteNavTrace.log(
                        "reader.content",
                        "noteId=${noteId.value} tookMs=${System.currentTimeMillis() - loadStartMs}"
                    )
                    val bodyMarkdown = VaultMarkdownPreprocess.stripTitleHeading(
                        document.content.markdown
                    )
                    // Prepare the body before publishing Content: on a warm cache hit this does not
                    // suspend, so title and body always reach the screen together (never title-first,
                    // never an empty flash on the frame the reader appears).
                    val prepareStartMs = System.currentTimeMillis()
                    val preparedBody = parsedMarkdownCache.get(bodyMarkdown)
                    NoteNavTrace.log(
                        "reader.prepared",
                        "noteId=${noteId.value} tookMs=${System.currentTimeMillis() - prepareStartMs}"
                    )
                    _uiState.value = NoteReaderUiState.Content(
                        title = document.note.title,
                        noteId = document.note.id,
                        path = document.content.path.value,
                        canEdit = document.note.isInbox,
                        document = document,
                        bodyMarkdown = bodyMarkdown,
                        preparedBody = preparedBody
                    )
                    NoteNavTrace.log(
                        "reader.published",
                        "noteId=${noteId.value} totalMs=${System.currentTimeMillis() - loadStartMs}"
                    )
                    currentDocument = document
                    currentPreparedBody = preparedBody
                    schedulePrefetch(
                        document,
                        preparedBody,
                        visibleStartOffset = 0,
                        visibleEndOffset = 0
                    )
                },
                onFailure = { error ->
                    _uiState.value = mapFailure(error)
                }
            )
        }
    }

    /** Reports the original markdown range that is visible, so prefetch follows visible links. */
    fun onViewportChanged(visibleStartOffset: Int, visibleEndOffset: Int) {
        val document = currentDocument ?: return
        val preparedBody = currentPreparedBody ?: return
        schedulePrefetch(document, preparedBody, visibleStartOffset, visibleEndOffset)
    }

    /**
     * (re)submits this note's linked notes to the shared, app-scoped [NotePrefetchScheduler] —
     * ordered by distance to the visible window, closest first — replacing whatever batch was
     * previously submitted (by this note or any other). The scheduler owns its own lifecycle, so
     * this is a fire-and-forget call, not tied to [viewModelScope].
     *
     * Targets come from [PreparedMarkdownLinks], which walks the same parsed AST the reader
     * renders — not a separate regex scan — so a link that is tappable is always prefetched, and
     * vice versa.
     */
    private fun schedulePrefetch(
        document: NoteReaderDocument,
        preparedBody: PreparedMarkdown,
        visibleStartOffset: Int,
        visibleEndOffset: Int
    ) {
        val scheduler = notePrefetchScheduler ?: return
        val resolved = PreparedMarkdownLinks.resolve(
            prepared = preparedBody,
            sourceNoteId = document.note.id,
            registry = document.registry
        )
        if (resolved.targets.isEmpty()) return
        val ordered = PrefetchLinkTargets.orderByViewport(
            targets = resolved.targets,
            visibleStartOffset = visibleStartOffset,
            visibleEndOffset = visibleEndOffset
        )
        NoteNavTrace.log(
            "prefetch.submit",
            "source=note:${document.note.id.value} count=${ordered.size} " +
                "targets=${ordered.map { it.value }}"
        )
        scheduler.submit(config, filesDir, ordered)
    }

    private fun mapFailure(error: Throwable): NoteReaderUiState {
        val contentError = (error as? NoteContentException)?.error
        return when (contentError) {
            NoteContentError.InvalidNoteId -> NoteReaderUiState.InvalidNoteId
            NoteContentError.NotFound -> NoteReaderUiState.NotFound
            NoteContentError.InvalidWorkspacePath ->
                NoteReaderUiState.Error(WORKSPACE_UNAVAILABLE_MESSAGE)
            NoteContentError.WorkspaceMissing ->
                NoteReaderUiState.Error(WORKSPACE_MISSING_MESSAGE)
            is NoteContentError.ReadFailed,
            null -> NoteReaderUiState.Error(READ_ERROR_MESSAGE)
        }
    }

    companion object {
        const val READ_ERROR_MESSAGE = "Could not open this note."
        const val WORKSPACE_UNAVAILABLE_MESSAGE = "Workspace is not available."
        const val WORKSPACE_MISSING_MESSAGE = "Workspace files are missing."

        fun factory(
            config: WorkspaceConfig,
            filesDir: File,
            noteId: NoteId,
            loadNoteForReading: LoadNoteForReading,
            parsedMarkdownCache: ParsedMarkdownCachePort = ParsedMarkdownCache(),
            notePrefetchScheduler: NotePrefetchScheduler? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = NoteReaderViewModel(
                config,
                filesDir,
                noteId,
                loadNoteForReading,
                parsedMarkdownCache,
                notePrefetchScheduler
            ) as T
        }
    }
}
