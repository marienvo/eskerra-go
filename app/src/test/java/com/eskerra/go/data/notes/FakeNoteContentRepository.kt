package com.eskerra.go.data.notes

import com.eskerra.go.core.model.NoteContent
import com.eskerra.go.core.model.NoteContentError
import com.eskerra.go.core.model.NoteContentException
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NotePath
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.NoteContentRepository
import java.io.File
import kotlinx.coroutines.delay

class FakeNoteContentRepository(
    private var result: Result<NoteContent> = Result.failure(
        NoteContentException(NoteContentError.NotFound)
    ),
    private var loadDelayMs: Long = 0L
) : NoteContentRepository {

    var lastConfig: WorkspaceConfig? = null
        private set
    var lastFilesDir: File? = null
        private set
    var lastNoteId: NoteId? = null
        private set
    var loadCount: Int = 0
        private set

    fun setResult(result: Result<NoteContent>) {
        this.result = result
    }

    fun setLoadDelayMs(delayMs: Long) {
        loadDelayMs = delayMs
    }

    override suspend fun load(
        config: WorkspaceConfig,
        filesDir: File,
        noteId: NoteId
    ): Result<NoteContent> {
        loadCount += 1
        lastConfig = config
        lastFilesDir = filesDir
        lastNoteId = noteId
        if (loadDelayMs > 0L) {
            delay(loadDelayMs)
        }
        return result
    }

    companion object {
        fun withContent(noteId: NoteId, markdown: String): FakeNoteContentRepository {
            val path = NotePath.fromRelativePath(noteId.value).getOrThrow()
            return FakeNoteContentRepository(
                Result.success(NoteContent(noteId, path, markdown))
            )
        }

        fun failing(error: NoteContentError): FakeNoteContentRepository =
            FakeNoteContentRepository(Result.failure(NoteContentException(error)))
    }
}
