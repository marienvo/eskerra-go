package com.eskerra.go.data.notes

import com.eskerra.go.core.model.NotePath
import com.eskerra.go.core.model.NoteWriteError
import com.eskerra.go.core.model.NoteWriteException
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.NoteWriteRepository
import java.io.File
import kotlinx.coroutines.delay

class FakeNoteWriteRepository(
    private var writeResult: Result<Unit> = Result.success(Unit),
    private var existsResult: Result<Boolean> = Result.success(false),
    private var writeDelayMs: Long = 0L
) : NoteWriteRepository {

    val writtenPaths = mutableListOf<Pair<NotePath, String>>()
    val deletedPaths = mutableListOf<NotePath>()
    var writeCount: Int = 0
        private set
    var existsCount: Int = 0
        private set
    var deleteCount: Int = 0
        private set
    private var deleteResult: Result<Unit> = Result.success(Unit)
    var lastConfig: WorkspaceConfig? = null
        private set
    var lastFilesDir: File? = null
        private set

    fun setWriteResult(result: Result<Unit>) {
        writeResult = result
    }

    fun setExistsResult(result: Result<Boolean>) {
        existsResult = result
    }

    fun setWriteDelayMs(delayMs: Long) {
        writeDelayMs = delayMs
    }

    fun setDeleteResult(result: Result<Unit>) {
        deleteResult = result
    }

    override suspend fun write(
        config: WorkspaceConfig,
        filesDir: File,
        notePath: NotePath,
        markdown: String
    ): Result<Unit> {
        writeCount += 1
        lastConfig = config
        lastFilesDir = filesDir
        writtenPaths += notePath to markdown
        if (writeDelayMs > 0L) {
            delay(writeDelayMs)
        }
        return writeResult
    }

    override suspend fun exists(
        config: WorkspaceConfig,
        filesDir: File,
        notePath: NotePath
    ): Result<Boolean> {
        existsCount += 1
        lastConfig = config
        lastFilesDir = filesDir
        return existsResult
    }

    override suspend fun delete(
        config: WorkspaceConfig,
        filesDir: File,
        notePath: NotePath
    ): Result<Unit> {
        deleteCount += 1
        lastConfig = config
        lastFilesDir = filesDir
        deletedPaths += notePath
        return deleteResult
    }

    companion object {
        fun failing(error: NoteWriteError) = FakeNoteWriteRepository(
            writeResult = Result.failure(NoteWriteException(error))
        )
    }
}
