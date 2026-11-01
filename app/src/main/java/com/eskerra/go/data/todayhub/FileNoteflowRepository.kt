package com.eskerra.go.data.todayhub

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.NoteflowRepository
import com.eskerra.go.core.todayhub.NoteflowConfig
import com.eskerra.go.core.todayhub.TodayHubFrontmatter
import com.eskerra.go.core.vault.VaultVisibility
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

class FileNoteflowRepository : NoteflowRepository {
    override suspend fun discover(
        config: WorkspaceConfig,
        filesDir: File
    ): Result<List<NoteflowConfig>> = withContext(Dispatchers.IO) {
        runCatching {
            val root = WorkspacePaths.resolve(filesDir, config.relativePath).getOrThrow()
            require(root.isDirectory) { "Workspace missing" }
            val found = mutableListOf<NoteflowConfig>()
            Files.walkFileTree(
                root.toPath(),
                object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(
                        dir: Path,
                        attrs: BasicFileAttributes
                    ): FileVisitResult = if (dir != root.toPath() &&
                        VaultVisibility.isExcludedDirectorySegment(dir.fileName.toString())
                    ) {
                        FileVisitResult.SKIP_SUBTREE
                    } else {
                        FileVisitResult.CONTINUE
                    }

                    override fun visitFile(
                        path: Path,
                        attrs: BasicFileAttributes
                    ): FileVisitResult {
                        if (path.fileName.toString() == ".flow" &&
                            Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        ) {
                            val id =
                                NoteId(root.toPath().relativize(path).toString().replace('\\', '/'))
                            found += parse(id, path.toFile().readText())
                        }
                        return FileVisitResult.CONTINUE
                    }
                }
            )
            found.sortedBy { it.id.value }
        }
    }

    internal fun parse(id: NoteId, raw: String): NoteflowConfig {
        val obj = Json.parseToJsonElement(raw).jsonObject
        val version = obj["version"] as? JsonPrimitive
        require(version != null && !version.isString && version.contentOrNull == "1") {
            "Invalid .flow version"
        }
        val settings = obj["settings"] as? JsonObject ?: JsonObject(emptyMap())
        val start = (settings["start"] as? JsonPrimitive)?.contentOrNull
            ?.let(TodayHubFrontmatter.StartDay::fromName) ?: TodayHubFrontmatter.StartDay.MONDAY
        val columns = strings(settings["columns"])
        return NoteflowConfig(
            id = id,
            title = (obj["title"] as? JsonPrimitive)?.takeIf {
                it.isString
            }?.contentOrNull.orEmpty(),
            links = (obj["links"] as? JsonArray)?.let(::strings).orEmpty(),
            settings = TodayHubFrontmatter.Settings(columns = columns, start = start)
        )
    }

    private fun strings(value: kotlinx.serialization.json.JsonElement?): List<String> =
        when (value) {
            is JsonArray -> value.mapNotNull {
                (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.contentOrNull
            }
            is JsonPrimitive -> listOfNotNull(
                value.takeIf {
                    it.isString
                }?.contentOrNull?.takeIf { it.isNotBlank() }
            )
            else -> emptyList()
        }
}
