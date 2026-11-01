package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.NoteContentError
import com.eskerra.go.core.model.NoteContentException
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteIndexError
import com.eskerra.go.core.model.NoteIndexException
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.NoteContentRepository
import com.eskerra.go.core.repository.NoteRegistryCachePort
import com.eskerra.go.core.repository.NoteflowRepository
import com.eskerra.go.core.todayhub.NoteflowDatedNotes
import com.eskerra.go.core.todayhub.TodayHubData
import com.eskerra.go.core.todayhub.TodayHubDatedLink
import com.eskerra.go.core.todayhub.TodayHubDiscovery
import com.eskerra.go.core.todayhub.TodayHubRef
import java.io.File
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/** Loads a Studio .flow and resolves its current dated-folder links. */
class LoadTodayHub(
    private val registryCache: NoteRegistryCachePort,
    private val contentRepository: NoteContentRepository,
    private val noteflowRepository: NoteflowRepository,
    private val today: () -> LocalDate = { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
) {
    suspend fun currentRegistry(config: WorkspaceConfig, filesDir: File): NoteRegistry? =
        registryCache.current(config, filesDir)

    suspend operator fun invoke(
        config: WorkspaceConfig,
        filesDir: File,
        preferredHubId: NoteId?
    ): Result<TodayHubData?> {
        val registry = registryCache.refresh(config, filesDir).getOrElse {
            val error = when ((it as? NoteIndexException)?.error) {
                is NoteIndexError.InvalidWorkspacePath -> NoteContentError.InvalidWorkspacePath
                is NoteIndexError.WorkspaceMissing -> NoteContentError.WorkspaceMissing
                else -> NoteContentError.ReadFailed(it.message)
            }
            return Result.failure(NoteContentException(error))
        }
        val flows = noteflowRepository.discover(config, filesDir).getOrElse {
            return Result.failure(NoteContentException(NoteContentError.ReadFailed(it.message)))
        }
        if (flows.isEmpty()) return Result.success(null)
        val preferredDirectory = preferredHubId?.let { TodayHubDiscovery.directoryOf(it.value) }
        val active = flows.firstOrNull { it.id == preferredHubId }
            ?: flows.firstOrNull {
                TodayHubDiscovery.directoryOf(it.id.value) == preferredDirectory
            }
            ?: flows.first()
        val links = mutableListOf<TodayHubDatedLink>()
        for (target in NoteflowDatedNotes.select(registry, active.id, today())) {
            val content = contentRepository.load(config, filesDir, target.noteId).getOrElse {
                return Result.failure(it)
            }
            links += TodayHubDatedLink(
                target.folder,
                target.noteId,
                NoteflowDatedNotes.isIncomplete(content.markdown)
            )
        }
        return Result.success(
            TodayHubData(
                hubs = flows.map { flow ->
                    TodayHubRef(
                        flow.id,
                        flow.title.ifBlank {
                            TodayHubDiscovery.folderLabel(flow.id.value)
                        }
                    )
                },
                activeHubId = active.id,
                settings = active.settings,
                introMarkdown = active.links.joinToString(" "),
                availableWeekStems = TodayHubDiscovery.availableWeekStems(
                    active.id.value,
                    registry
                ),
                registry = registry,
                datedLinks = links
            )
        )
    }
}
