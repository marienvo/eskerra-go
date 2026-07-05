package com.eskerra.go.core.usecase

import com.eskerra.go.core.markdown.VaultMarkdownPreprocess
import com.eskerra.go.core.model.NoteContentError
import com.eskerra.go.core.model.NoteContentException
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteIndexError
import com.eskerra.go.core.model.NoteIndexException
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.NoteContentRepository
import com.eskerra.go.core.repository.NoteRegistryCachePort
import com.eskerra.go.core.todayhub.TodayHubData
import com.eskerra.go.core.todayhub.TodayHubDiscovery
import com.eskerra.go.core.todayhub.TodayHubFrontmatter
import com.eskerra.go.core.todayhub.TodayHubRef
import java.io.File

/**
 * Discovers Today hubs in the vault and loads the active hub's intro + settings (spec §11.1–§11.2).
 * Returns `null` when no `Today.md` exists so the caller can show the empty state. Week rows are
 * fetched separately via [LoadTodayHubRow] (spec §11.4).
 */
class LoadTodayHub(
    private val registryCache: NoteRegistryCachePort,
    private val contentRepository: NoteContentRepository
) {

    suspend fun currentRegistry(config: WorkspaceConfig, filesDir: File): NoteRegistry? =
        registryCache.current(config, filesDir)

    suspend operator fun invoke(
        config: WorkspaceConfig,
        filesDir: File,
        preferredHubId: NoteId?
    ): Result<TodayHubData?> {
        val registryResult = registryCache.refresh(config, filesDir)
        if (registryResult.isFailure) {
            return Result.failure(registryFailure(registryResult.exceptionOrNull()))
        }
        val registry = registryResult.getOrThrow()

        val hubIds = TodayHubDiscovery.sortedHubNoteIds(registry)
        if (hubIds.isEmpty()) {
            return Result.success(null)
        }

        val activeHubId = preferredHubId?.takeIf { it in hubIds } ?: hubIds.first()

        val contentResult = contentRepository.load(config, filesDir, activeHubId)
        if (contentResult.isFailure) {
            return Result.failure(contentResult.exceptionOrNull()!!)
        }
        val markdown = contentResult.getOrThrow().markdown

        val settings = TodayHubFrontmatter.parse(markdown)
        // The hub note's H1 already shows as the "Switch hub" title, so drop it from the intro body
        // to keep a single H1 on the today-hub page (spec §11). Other notes render their H1 as usual.
        val introMarkdown = stripLeadingH1(
            VaultMarkdownPreprocess.splitYamlFrontmatter(markdown).body
        )
        val availableWeekStems = TodayHubDiscovery.availableWeekStems(activeHubId.value, registry)
        // Label each hub by its note's H1 (the registry title), falling back to the folder name when
        // the hub has no heading, so the "Switch hub" title/picker read the hub's own title.
        val titleById = registry.notes.associate { it.id to it.title }
        val hubs = hubIds.map { id ->
            val h1 = titleById[id]?.takeIf { it.isNotBlank() }
            TodayHubRef(id, h1 ?: TodayHubDiscovery.folderLabel(id.value))
        }

        return Result.success(
            TodayHubData(
                hubs = hubs,
                activeHubId = activeHubId,
                settings = settings,
                introMarkdown = introMarkdown,
                availableWeekStems = availableWeekStems,
                registry = registry
            )
        )
    }

    /**
     * Removes the leading level-1 heading (the note title) from [markdown], along with a single
     * blank line left in its place. Only strips when nothing but blank lines precede the heading,
     * so a body that opens with prose is left untouched.
     */
    internal fun stripLeadingH1(markdown: String): String {
        val lines = markdown.lines()
        var inFence = false
        for (i in lines.indices) {
            val trimmed = lines[i].trimStart()
            if (trimmed.startsWith("```")) {
                inFence = !inFence
                continue
            }
            if (inFence) {
                continue
            }
            if (trimmed.startsWith("# ")) {
                if (lines.take(i).any { it.isNotBlank() }) {
                    return markdown
                }
                val remaining = lines.toMutableList().apply { removeAt(i) }
                if (i < remaining.size && remaining[i].isBlank()) {
                    remaining.removeAt(i)
                }
                return remaining.joinToString("\n").trimStart('\n')
            }
            if (trimmed.isNotEmpty()) {
                return markdown
            }
        }
        return markdown
    }

    private fun registryFailure(cause: Throwable?): NoteContentException {
        if (cause is NoteIndexException) {
            val error = when (cause.error) {
                is NoteIndexError.InvalidWorkspacePath -> NoteContentError.InvalidWorkspacePath
                is NoteIndexError.WorkspaceMissing -> NoteContentError.WorkspaceMissing
                is NoteIndexError.ScanFailed -> NoteContentError.ReadFailed(cause.error.detail)
            }
            return NoteContentException(error)
        }
        return NoteContentException(NoteContentError.ReadFailed(cause?.message))
    }
}
