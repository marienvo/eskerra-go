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
import com.eskerra.go.core.todayhub.DatedNoteReadStatus
import com.eskerra.go.core.usecase.LoadNoteForReading
import com.eskerra.go.core.usecase.NotePrefetchScheduler
import com.eskerra.go.core.usecase.SetDatedNoteReadStatus
import com.eskerra.go.data.notes.ParsedMarkdownCache
import com.eskerra.go.data.perf.NoteNavTrace
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NoteReaderViewModel(
    private val config: WorkspaceConfig,
    private val filesDir: File,
    private val noteId: NoteId,
    private val loadNoteForReading: LoadNoteForReading,
    private val parsedMarkdownCache: ParsedMarkdownCachePort = ParsedMarkdownCache(),
    private val notePrefetchScheduler: NotePrefetchScheduler? = null,
    private val setDatedNoteReadStatus: SetDatedNoteReadStatus? = null
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

    fun toggleDatedNoteReadStatus(onPersisted: () -> Unit = {}) {
        val setReadStatus = setDatedNoteReadStatus ?: return
        val content = _uiState.value as? NoteReaderUiState.Content ?: return
        if (content.readStatusMutationInProgress ||
            !DatedNoteReadStatus.hasTitle(content.document.content.markdown)
        ) {
            return
        }
        val read = !DatedNoteReadStatus.isRead(content.document.content.markdown)
        _uiState.update {
            it.asContentFor(noteId)?.copy(
                readStatusMutationInProgress = true,
                readStatusMessage = null
            ) ?: it
        }

        viewModelScope.launch {
            setReadStatus(config, filesDir, noteId, read).fold(
                onSuccess = { result ->
                    val current = _uiState.value.asContentFor(noteId) ?: return@fold
                    val title = DatedNoteReadStatus.title(result.markdown) ?: current.title
                    val document = current.document.copy(
                        note = current.document.note.copy(title = title),
                        content = current.document.content.copy(markdown = result.markdown),
                        registry = result.registry
                    )
                    currentDocument = document
                    _uiState.update {
                        it.asContentFor(noteId)?.copy(
                            title = title,
                            document = document,
                            readStatusMutationInProgress = false,
                            readStatusMessage = if (result.registryRefreshFailed) {
                                READ_STATUS_REFRESH_WARNING
                            } else {
                                null
                            }
                        ) ?: it
                    }
                    onPersisted()
                },
                onFailure = {
                    _uiState.update {
                        it.asContentFor(noteId)?.copy(
                            readStatusMutationInProgress = false,
                            readStatusMessage = READ_STATUS_WRITE_ERROR
                        ) ?: it
                    }
                }
            )
        }
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

    private fun NoteReaderUiState.asContentFor(target: NoteId): NoteReaderUiState.Content? =
        (this as? NoteReaderUiState.Content)?.takeIf { it.noteId == target }

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
        const val READ_STATUS_WRITE_ERROR = "Could not update read status."
        const val READ_STATUS_REFRESH_WARNING =
            "Read status saved, but the note list could not be refreshed."

        fun factory(
            config: WorkspaceConfig,
            filesDir: File,
            noteId: NoteId,
            loadNoteForReading: LoadNoteForReading,
            parsedMarkdownCache: ParsedMarkdownCachePort = ParsedMarkdownCache(),
            notePrefetchScheduler: NotePrefetchScheduler? = null,
            setDatedNoteReadStatus: SetDatedNoteReadStatus? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = NoteReaderViewModel(
                config,
                filesDir,
                noteId,
                loadNoteForReading,
                parsedMarkdownCache,
                notePrefetchScheduler,
                setDatedNoteReadStatus
            ) as T
        }
    }
}
