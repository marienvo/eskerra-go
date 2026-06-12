package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.NoteContentError
import com.eskerra.go.core.model.NoteContentException
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.NoteContentRepository
import com.eskerra.go.core.todayhub.TodayHubDiscovery
import com.eskerra.go.core.todayhub.TodayHubRow
import com.eskerra.go.core.todayhub.TodayHubRowColumns
import java.io.File

/**
 * Loads one Today hub week row beside [todayNoteId] and splits it into [columnCount] columns
 * (spec §11.3). A missing row file yields empty columns; other read failures propagate.
 */
class LoadTodayHubRow(private val contentRepository: NoteContentRepository) {

    suspend operator fun invoke(
        config: WorkspaceConfig,
        filesDir: File,
        todayNoteId: NoteId,
        weekStartStem: String,
        columnCount: Int
    ): Result<TodayHubRow> {
        val rowNoteId = TodayHubDiscovery.rowNoteId(todayNoteId.value, weekStartStem)

        val body = contentRepository.load(config, filesDir, rowNoteId).fold(
            onSuccess = { it.markdown },
            onFailure = { error ->
                val contentError = (error as? NoteContentException)?.error
                if (contentError is NoteContentError.NotFound) {
                    ""
                } else {
                    return Result.failure(error)
                }
            }
        )

        return Result.success(
            TodayHubRow(
                rowNoteId = rowNoteId,
                weekStartStem = weekStartStem,
                columns = TodayHubRowColumns.split(body, columnCount)
            )
        )
    }
}
