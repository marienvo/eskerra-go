package com.eskerra.go.data.todayhub

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.todayhub.TodayHubFrontmatter
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FileNoteflowRepositoryTest {
    private val repository = FileNoteflowRepository()

    @Test
    fun readsStudioConfigurationAndDefaults() {
        val config = repository.parse(
            NoteId("Work/.flow"),
            """{"version":1,"title":"Work","links":["[[Plan]]"],"settings":{
                "start":"sunday","columns":["Tasks","Notes"]}}"""
        )
        assertEquals("Work", config.title)
        assertEquals(listOf("[[Plan]]"), config.links)
        assertEquals(TodayHubFrontmatter.StartDay.SUNDAY, config.settings.start)
        assertEquals(listOf("Tasks", "Notes"), config.settings.columns)
        assertEquals(
            emptyList<String>(),
            repository.parse(NoteId(".flow"), """{"version":1}""").links
        )
    }

    @Test
    fun rejectsUnsupportedVersion() {
        assertThrows(IllegalArgumentException::class.java) {
            repository.parse(NoteId(".flow"), """{"version":2}""")
        }
    }

    @Test
    fun discoversMarkersAndSkipsHiddenFolders() = runBlocking {
        val files = Files.createTempDirectory("noteflow-test").toFile()
        try {
            val vault = files.resolve("vault").apply { mkdirs() }
            vault.resolve("Work").mkdirs()
            vault.resolve("Work/.flow").writeText("""{"version":1,"title":"Work"}""")
            vault.resolve(".hidden").mkdirs()
            vault.resolve(".hidden/.flow").writeText("not json")
            val workspace = WorkspaceConfig("vault", "vault", null, "main", 0)
            val flows = repository.discover(workspace, files).getOrThrow()
            assertEquals(listOf("Work/.flow"), flows.map { it.id.value })
        } finally {
            files.deleteRecursively()
        }
    }
}
