package com.eskerra.go.data.notes

import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import java.io.File

class FakeNoteRegistryRepository(
    private var result: Result<NoteRegistry> = Result.success(NoteRegistry.fromNotes(emptyList()))
) : NoteRegistryRepository {

    var lastConfig: WorkspaceConfig? = null
        private set
    var lastFilesDir: File? = null
        private set
    var refreshCount: Int = 0
        private set

    fun setResult(result: Result<NoteRegistry>) {
        this.result = result
    }

    override suspend fun refresh(config: WorkspaceConfig, filesDir: File): Result<NoteRegistry> {
        refreshCount += 1
        lastConfig = config
        lastFilesDir = filesDir
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
