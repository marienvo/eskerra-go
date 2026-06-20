package com.eskerra.go.data.podcast.rss

import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.podcast.parsePodcastFileDetails
import com.eskerra.go.core.podcast.rss.PodcastRssFileSync
import com.eskerra.go.core.podcast.rss.PodcastsMdMerge
import com.eskerra.go.core.podcast.rss.RssFileSyncResult
import com.eskerra.go.core.repository.PodcastRefreshProgress
import com.eskerra.go.core.repository.PodcastRssVaultSync
import com.eskerra.go.core.repository.PodcastRssVaultSyncSummary
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.time.Year
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Filesystem-backed [PodcastRssVaultSync]. Refreshes `📻` feed files referenced by
 * each year's companion hub, then merges fresh episodes into the `*- podcasts.md`
 * stubs (spec §7.3). All markdown writes land in the working tree; the caller
 * commits them as one refresh commit.
 */
class FilePodcastRssVaultSync(
    private val fetcher: RssFeedFetcher,
    private val currentYear: () -> Int = { Year.now().value },
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : PodcastRssVaultSync {

    private val fileSync = PodcastRssFileSync(fetcher::fetch, nowMs, zoneId)

    override suspend fun refresh(
        config: WorkspaceConfig,
        filesDir: File,
        onProgress: (PodcastRefreshProgress) -> Unit
    ): Result<PodcastRssVaultSyncSummary> = withContext(Dispatchers.IO) {
        val workspaceDir = WorkspacePaths.resolve(filesDir, config.relativePath).getOrElse {
            return@withContext Result.failure(it)
        }
        val generalDir = File(workspaceDir, GENERAL_DIRECTORY)
        if (!generalDir.isDirectory) {
            onProgress(PodcastRefreshProgress(100, PodcastRefreshProgress.PHASE_COMPLETE))
            return@withContext Result.success(PodcastRssVaultSyncSummary.EMPTY)
        }

        runCatching { run(generalDir, onProgress) }
    }

    private fun run(
        generalDir: File,
        onProgress: (PodcastRefreshProgress) -> Unit
    ): PodcastRssVaultSyncSummary {
        val year = currentYear()
        val stubFiles = generalDir.listFiles().orEmpty()
            .filter { isRegularFile(it) && parsePodcastFileDetails(it.name, year) != null }
            .sortedBy { it.name }

        val stubToRssFiles = LinkedHashMap<File, List<File>>()
        val uniqueRssFiles = LinkedHashMap<String, File>()
        for (stub in stubFiles) {
            val details = parsePodcastFileDetails(stub.name, year) ?: continue
            val hubFile = File(generalDir, "${details.year} ${details.sectionTitle}.md")
            val rssNames = if (isRegularFile(hubFile)) {
                parseUncheckedRssLinks(hubFile.readText(Charsets.UTF_8))
            } else {
                emptyList()
            }
            val rssFiles = rssNames
                .map { File(generalDir, it) }
                .filter { isRegularFile(it) }
            stubToRssFiles[stub] = rssFiles
            rssFiles.forEach { uniqueRssFiles.putIfAbsent(it.name, it) }
        }

        val syncResults = HashMap<String, RssFileSyncResult>()
        var refreshedFileCount = 0
        var failedFeedCount = 0
        val rssFiles = uniqueRssFiles.values.toList()
        rssFiles.forEachIndexed { index, file ->
            onProgress(
                PodcastRefreshProgress(
                    percent = percentForIndex(index, rssFiles.size),
                    phase = PodcastRefreshProgress.PHASE_RSS,
                    detail = file.name
                )
            )
            val content = file.readText(Charsets.UTF_8)
            val result = fileSync.sync(file.name, content)
            syncResults[file.name] = result
            if (result.changed) {
                file.writeText(result.content, Charsets.UTF_8)
                refreshedFileCount++
            } else {
                failedFeedCount++
            }
        }

        onProgress(PodcastRefreshProgress(99, PodcastRefreshProgress.PHASE_MERGE))
        val now = nowMs()
        var mergedStubCount = 0
        for ((stub, rssFilesForStub) in stubToRssFiles) {
            val candidates = rssFilesForStub.flatMap { syncResults[it.name]?.episodes.orEmpty() }
            val existing = stub.readText(Charsets.UTF_8)
            val merged = PodcastsMdMerge.merge(existing, candidates, now, zoneId)
            if (merged != existing) {
                stub.writeText(merged, Charsets.UTF_8)
                mergedStubCount++
            }
        }

        onProgress(PodcastRefreshProgress(100, PodcastRefreshProgress.PHASE_COMPLETE))
        return PodcastRssVaultSyncSummary(
            refreshedFileCount = refreshedFileCount,
            mergedStubCount = mergedStubCount,
            failedFeedCount = failedFeedCount
        )
    }

    private fun percentForIndex(index: Int, total: Int): Int {
        if (total <= 0) return 99
        return ((index * 99) / total).coerceIn(0, 99)
    }

    private fun isRegularFile(file: File): Boolean =
        Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)

    private fun parseUncheckedRssLinks(hubContent: String): List<String> = hubContent.lineSequence()
        .mapNotNull { line -> UNCHECKED_RSS_LINK.find(line)?.groupValues?.get(1)?.trim() }
        .filter { it.startsWith(RSS_PREFIX) }
        .map { if (it.endsWith(".md", ignoreCase = true)) it else "$it.md" }
        .toList()

    companion object {
        const val GENERAL_DIRECTORY = "General"
        private val RSS_PREFIX = String(Character.toChars(0x1F4FB))
        private val UNCHECKED_RSS_LINK =
            Regex("""^\s*-\s*\[ ]\s*\[\[([^\]]+)]]""")
    }
}
