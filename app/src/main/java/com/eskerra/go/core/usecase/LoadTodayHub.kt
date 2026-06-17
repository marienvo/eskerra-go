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
import com.eskerra.go.core.todayhub.TodayHubData
import com.eskerra.go.core.todayhub.TodayHubDiscovery
import com.eskerra.go.core.todayhub.TodayHubFrontmatter
import com.eskerra.go.core.todayhub.TodayHubRef
import com.eskerra.go.data.notes.NoteRegistryCache
import java.io.File

/**
 * Discovers Today hubs in the vault and loads the active hub's intro + settings (spec §11.1–§11.2).
 * Returns `null` when no `Today.md` exists so the caller can show the empty state. Week rows are
 * fetched separately via [LoadTodayHubRow] (spec §11.4).
 */
class LoadTodayHub(
    private val registryCache: NoteRegistryCache,
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
        val introMarkdown = VaultMarkdownPreprocess.splitYamlFrontmatter(markdown).body
        val availableWeekStems = TodayHubDiscovery.availableWeekStems(activeHubId.value, registry)
        val hubs = hubIds.map { TodayHubRef(it, TodayHubDiscovery.folderLabel(it.value)) }

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
