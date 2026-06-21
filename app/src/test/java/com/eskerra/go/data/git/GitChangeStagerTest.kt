package com.eskerra.go.data.git

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GitChangeStagerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val repo = JGitWorkspaceRepository()

    @Test
    fun literalPattern_marksPathAsLiteralPathspec() {
        assertEquals(
            ":(literal)General/2026 News - podcasts.md",
            GitChangeStager.literalPattern("General/2026 News - podcasts.md")
        )
    }

    @Test
    fun stageAll_stagesPathsWithSpacesAndBrackets() {
        val dir = temp.newFolder("workspace")
        repo.initOrOpen(dir).getOrThrow()

        val generalDir = File(dir, "General").apply { mkdirs() }
        File(generalDir, "2026 News - podcasts.md").writeText("- [ ] episode\n")
        File(generalDir, "📻 Show [Daily].md").writeText("# Show\n")

        repo.stageAll(dir).getOrThrow()
        repo.commit(dir, "Stage podcast files").getOrThrow()

        assertFalse(repo.status(dir).getOrThrow().hasUncommittedChanges)
    }

    @Test
    fun needsLiteralPathspec_detectsNonAsciiAndBracketCharacters() {
        assertTrue(GitChangeStager.needsLiteralPathspec("General/📻 De Jortcast ●.md"))
        assertTrue(GitChangeStager.needsLiteralPathspec("General/📻 Show [Daily].md"))
        assertFalse(GitChangeStager.needsLiteralPathspec("General/2026 News - podcasts.md"))
    }

    @Test
    fun filepatternFor_usesLiteralForPodcastRssNames() {
        assertEquals(
            ":(literal)General/📻 De Jortcast ●.md",
            GitChangeStager.filepatternFor("General/📻 De Jortcast ●.md")
        )
    }

    @Test
    fun stageAll_stagesManyUnicodePodcastFilesInOneBatch() {
        val dir = temp.newFolder("workspace")
        repo.initOrOpen(dir).getOrThrow()

        val generalDir = File(dir, "General").apply { mkdirs() }
        File(generalDir, "2026 Gentle Hours - podcasts.md").writeText("- [ ] episode\n")
        listOf(
            "📻 Amerika Podcast •.md",
            "📻 Argos Actueel ●.md",
            "📻 De Jortcast ●.md",
            "📻 NRC Vandaag ·.md"
        ).forEach { name ->
            File(generalDir, name).writeText("# $name\n")
        }

        repo.stageAll(dir).getOrThrow()
        repo.commit(dir, "Stage podcast files").getOrThrow()

        assertFalse(repo.status(dir).getOrThrow().hasUncommittedChanges)
    }

    @Test
    fun needsLiteralPathspec_detectsBracketCharacters() {
        assertTrue(GitChangeStager.needsLiteralPathspec("General/📻 Show [Daily].md"))
        assertFalse(GitChangeStager.needsLiteralPathspec("General/2026 News - podcasts.md"))
    }

    @Test
    fun stageAll_stagesEmojiNamedPodcastFiles() {
        val dir = temp.newFolder("workspace")
        repo.initOrOpen(dir).getOrThrow()

        val generalDir = File(dir, "General").apply { mkdirs() }
        val rssName = String(Character.toChars(0x1F4FB)) + " Daily News.md"
        File(generalDir, rssName).writeText("# RSS cache\n")
        File(generalDir, "2026 News - podcasts.md").writeText("- [ ] episode\n")

        repo.stageAll(dir).getOrThrow()
        repo.commit(dir, "Stage podcast files").getOrThrow()

        assertFalse(repo.status(dir).getOrThrow().hasUncommittedChanges)
    }

    @Test
    fun resolveExistingFile_matchesNfcWhenDirectPathMissing() {
        val dir = temp.newFolder("workspace")
        val generalDir = File(dir, "General").apply { mkdirs() }
        val onDiskName = String(Character.toChars(0x1F4FB)) + " Daily News.md"
        File(generalDir, onDiskName).writeText("cached")

        val resolved = GitChangeStager.resolveExistingFile(dir, "General/$onDiskName")
        assertNotNull(resolved)
        assertTrue(resolved!!.exists())
        assertEquals(onDiskName, resolved.name)
    }
}
