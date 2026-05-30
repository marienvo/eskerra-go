package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.AmbiguousWikiLink
import com.eskerra.go.core.model.MissingWikiLink
import com.eskerra.go.core.model.NoteContentError
import com.eskerra.go.core.model.NoteContentException
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteIndexError
import com.eskerra.go.core.model.NoteIndexException
import com.eskerra.go.core.model.NotePath
import com.eskerra.go.core.model.NoteReaderDocument
import com.eskerra.go.core.model.NoteReaderSegment
import com.eskerra.go.core.model.ResolvedWikiLink
import com.eskerra.go.core.model.WikiLink
import com.eskerra.go.core.model.WikiLinkResolution
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.NoteContentRepository
import com.eskerra.go.core.repository.NoteRegistryRepository
import com.eskerra.go.core.wikilink.WikiLinkParser
import com.eskerra.go.core.wikilink.WikiLinkResolver
import java.io.File

/**
 * Refreshes the note registry, loads markdown for [noteId], parses and resolves wiki
 * links, and returns a reader document with ordered presentation segments.
 */
class LoadNoteForReading(
    private val registryRepository: NoteRegistryRepository,
    private val contentRepository: NoteContentRepository
) {

    suspend operator fun invoke(
        config: WorkspaceConfig,
        filesDir: File,
        noteId: NoteId
    ): Result<NoteReaderDocument> {
        if (NotePath.fromRelativePath(noteId.value).isFailure) {
            return Result.failure(NoteContentException(NoteContentError.InvalidNoteId))
        }

        val registryResult = registryRepository.refresh(config, filesDir)
        if (registryResult.isFailure) {
            return Result.failure(registryFailure(registryResult.exceptionOrNull()))
        }

        val registry = registryResult.getOrThrow()
        val summary = registry.notes.find { it.id == noteId }
            ?: return Result.failure(NoteContentException(NoteContentError.NotFound))

        val contentResult = contentRepository.load(config, filesDir, noteId)
        if (contentResult.isFailure) {
            return Result.failure(contentResult.exceptionOrNull()!!)
        }

        val content = contentResult.getOrThrow()
        val links = WikiLinkParser.parse(content.markdown)
        val resolutions = WikiLinkResolver.resolveAll(links, registry)
        val segments = buildSegments(content.markdown, links, resolutions)

        return Result.success(
            NoteReaderDocument(
                note = summary,
                content = content,
                segments = segments
            )
        )
    }

    internal companion object {
        fun buildSegments(
            markdown: String,
            links: List<WikiLink>,
            resolutions: List<WikiLinkResolution>
        ): List<NoteReaderSegment> {
            if (links.isEmpty()) {
                return listOf(NoteReaderSegment.Text(markdown))
            }

            val segments = mutableListOf<NoteReaderSegment>()
            var cursor = 0

            links.zip(resolutions).forEach { (link, resolution) ->
                val range = link.sourceRange
                if (cursor < range.first) {
                    segments += NoteReaderSegment.Text(markdown.substring(cursor, range.first))
                }
                segments += resolution.toSegment(link)
                cursor = range.last + 1
            }

            if (cursor < markdown.length) {
                segments += NoteReaderSegment.Text(markdown.substring(cursor))
            }

            return segments
        }

        private fun WikiLinkResolution.toSegment(link: WikiLink): NoteReaderSegment = when (this) {
            is ResolvedWikiLink ->
                NoteReaderSegment.ResolvedLink(label = link.displayText, target = note.id)
            is MissingWikiLink ->
                NoteReaderSegment.MissingLink(label = link.displayText, reason = reason)
            is AmbiguousWikiLink ->
                NoteReaderSegment.AmbiguousLink(
                    label = link.displayText,
                    candidateCount = candidates.size
                )
        }

        private fun registryFailure(cause: Throwable?): NoteContentException {
            if (cause is NoteIndexException) {
                val error = when (cause.error) {
                    is NoteIndexError.InvalidWorkspacePath ->
                        NoteContentError.InvalidWorkspacePath
                    is NoteIndexError.WorkspaceMissing ->
                        NoteContentError.WorkspaceMissing
                    is NoteIndexError.ScanFailed ->
                        NoteContentError.ReadFailed(cause.error.detail)
                }
                return NoteContentException(error)
            }
            return NoteContentException(NoteContentError.ReadFailed(cause?.message))
        }
    }
}
