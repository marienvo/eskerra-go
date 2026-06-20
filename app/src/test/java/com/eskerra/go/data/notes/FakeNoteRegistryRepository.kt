package com.eskerra.go.data.notes

import com.eskerra.go.core.model.NoteIndexError
import com.eskerra.go.core.model.NoteIndexException
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.NoteRegistryRepository
import java.io.File
import kotlinx.coroutines.delay

class FakeNoteRegistryRepository(
    private var result: Result<NoteRegistry> = Result.success(NoteRegistry.fromNotes(emptyList())),
    private var refreshDelayMs: Long = 0L
) : NoteRegistryRepository {

    var lastConfig: WorkspaceConfig? = null
        private set
    var lastFilesDir: File? = null
        private set
    var refreshCount: Int = 0
        private set
    var lastPreviousRegistry: NoteRegistry? = null
        private set

    fun setResult(result: Result<NoteRegistry>) {
        this.result = result
    }

    fun setRefreshDelayMs(delayMs: Long) {
        refreshDelayMs = delayMs
    }

    override suspend fun refresh(
        config: WorkspaceConfig,
        filesDir: File,
        previousRegistry: NoteRegistry?
    ): Result<NoteRegistry> {
        refreshCount += 1
        lastConfig = config
        lastFilesDir = filesDir
        lastPreviousRegistry = previousRegistry
        if (refreshDelayMs > 0L) {
            delay(refreshDelayMs)
        }
        return result
    }

    companion object {
        fun withInboxNotes(vararg notes: NoteSummary): FakeNoteRegistryRepository =
            FakeNoteRegistryRepository(Result.success(NoteRegistry.fromNotes(notes.toList())))

        fun failing(
            error: NoteIndexError = NoteIndexError.ScanFailed(null)
        ): FakeNoteRegistryRepository =
            FakeNoteRegistryRepository(Result.failure(NoteIndexException(error)))
    }
}
