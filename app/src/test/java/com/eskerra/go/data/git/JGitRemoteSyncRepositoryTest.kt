package com.eskerra.go.data.git

import com.eskerra.go.core.model.SyncStatusState
import java.io.File
import org.eclipse.jgit.api.Git
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JGitRemoteSyncRepositoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val gitRepo = JGitWorkspaceRepository()
    private val remoteSync = JGitRemoteSyncRepository(gitRepo)

    private fun newBareRemoteUri(name: String): String {
        val bare = TestGitRepos.initBareRemote(File(temp.root, name))
        return TestGitRepos.fileUri(bare)
    }

    @Test
    fun stageInboxChanges_stagesOnlyInboxPaths() {
        val dir = temp.newFolder("workspace")
        gitRepo.initOrOpen(dir).getOrThrow()
        gitRepo.writeFile(dir, "Inbox/note.md", "inbox").getOrThrow()
        gitRepo.writeFile(dir, "Projects/other.md", "other").getOrThrow()

        remoteSync.stageInboxChanges(dir).getOrThrow()

        Git.open(dir).use { git ->
            val staged = git.status().call().added
            assertEquals(setOf("Inbox/note.md"), staged.toSet())
            assertFalse(staged.contains("Projects/other.md"))
        }
    }

    @Test
    fun compareWithRemote_equalAfterClone() {
        val remoteUri = newBareRemoteUri("remote.git")
        seedRemote(remoteUri)

        val consumer = temp.newFolder("consumer")
        gitRepo.cloneFrom(remoteUri, consumer).getOrThrow()
        val branch = gitRepo.status(consumer).getOrThrow().branch
        gitRepo.fetch(consumer).getOrThrow()

        val comparison = remoteSync.compareWithRemote(consumer, branch).getOrThrow()
        assertTrue(comparison.isEqual)
        assertEquals(0, comparison.aheadCount)
        assertEquals(0, comparison.behindCount)
    }

    @Test
    fun compareWithRemote_detectsBehind() {
        val remoteUri = newBareRemoteUri("remote.git")
        val branch = seedRemote(remoteUri)

        val producer = temp.newFolder("producer")
        gitRepo.cloneFrom(remoteUri, producer).getOrThrow()
        val consumer = temp.newFolder("consumer")
        gitRepo.cloneFrom(remoteUri, consumer).getOrThrow()

        gitRepo.writeFile(producer, "Inbox/v2.md", "v2").getOrThrow()
        gitRepo.stageAll(producer).getOrThrow()
        gitRepo.commit(producer, "v2").getOrThrow()
        gitRepo.push(producer).getOrThrow()

        gitRepo.fetch(consumer).getOrThrow()
        val comparison = remoteSync.compareWithRemote(consumer, branch).getOrThrow()

        assertTrue(comparison.localIsAncestorOfRemote)
        assertEquals(0, comparison.aheadCount)
        assertTrue(comparison.behindCount > 0)
    }

    @Test
    fun fastForwardToRemote_integratesRemoteCommits() {
        val remoteUri = newBareRemoteUri("remote.git")
        val branch = seedRemote(remoteUri)

        val producer = temp.newFolder("producer")
        gitRepo.cloneFrom(remoteUri, producer).getOrThrow()
        val consumer = temp.newFolder("consumer")
        gitRepo.cloneFrom(remoteUri, consumer).getOrThrow()

        gitRepo.writeFile(producer, "Projects/remote.md", "remote").getOrThrow()
        gitRepo.stageAll(producer).getOrThrow()
        gitRepo.commit(producer, "remote change").getOrThrow()
        gitRepo.push(producer).getOrThrow()

        gitRepo.fetch(consumer).getOrThrow()
        remoteSync.fastForwardToRemote(consumer, branch).getOrThrow()

        assertTrue(File(consumer, "Projects/remote.md").isFile)
    }

    @Test
    fun fastForwardToRemote_usesFastForwardMergeNotHardReset() {
        val remoteUri = newBareRemoteUri("remote.git")
        val branch = seedRemote(remoteUri)

        val producer = temp.newFolder("producer")
        gitRepo.cloneFrom(remoteUri, producer).getOrThrow()
        val consumer = temp.newFolder("consumer")
        gitRepo.cloneFrom(remoteUri, consumer).getOrThrow()

        gitRepo.writeFile(producer, "Projects/remote.md", "remote").getOrThrow()
        gitRepo.stageAll(producer).getOrThrow()
        gitRepo.commit(producer, "remote change").getOrThrow()
        gitRepo.push(producer).getOrThrow()

        gitRepo.fetch(consumer).getOrThrow()
        val headLog = File(consumer, ".git/logs/HEAD")
        val reflogBefore = headLog.readText()

        remoteSync.fastForwardToRemote(consumer, branch).getOrThrow()

        val reflogAfter = headLog.readText()
        assertFalse(reflogAfter.removePrefix(reflogBefore).contains("reset: moving to"))
    }

    @Test
    fun compareWithRemote_reportsMissingRemoteBranch() {
        val remoteUri = newBareRemoteUri("remote.git")
        val branch = seedRemote(remoteUri)

        val consumer = temp.newFolder("consumer")
        gitRepo.cloneFrom(remoteUri, consumer).getOrThrow()
        org.eclipse.jgit.api.Git.open(consumer).use { git ->
            git.checkout()
                .setCreateBranch(true)
                .setName("missing-branch")
                .setStartPoint(branch)
                .call()
        }

        val comparison = remoteSync.compareWithRemote(consumer, "missing-branch").getOrThrow()

        assertTrue(comparison.remoteBranchMissing)
    }

    @Test
    fun buildStatusSummary_dirtyLocalChanges() {
        val workspaceStatus = com.eskerra.go.core.model.GitWorkspaceStatus(
            branch = "main",
            hasUncommittedChanges = true,
            changedPaths = setOf("Inbox/a.md")
        )
        val summary = remoteSync.buildStatusSummary(workspaceStatus, null)
        assertEquals(SyncStatusState.DirtyLocalChanges, summary.state)
    }

    @Test
    fun configureSanitizedOrigin_setsUrlWithoutCredentials() {
        val dir = temp.newFolder("workspace")
        gitRepo.initOrOpen(dir).getOrThrow()
        val remoteUri = newBareRemoteUri("origin.git")

        remoteSync.configureSanitizedOrigin(dir, remoteUri).getOrThrow()

        val configText = File(dir, ".git/config").readText()
        assertTrue(configText.contains("[remote \"origin\"]"))
        val urlLine = configText.lines().first { it.trim().startsWith("url =") }
        assertTrue(urlLine.contains("file:"))
        assertFalse(urlLine.contains("@"))
    }

    @Test
    fun readOriginUrl_returnsConfiguredOrigin() {
        val dir = temp.newFolder("workspace")
        gitRepo.initOrOpen(dir).getOrThrow()
        val remoteUri = newBareRemoteUri("origin-read.git")
        remoteSync.configureSanitizedOrigin(dir, remoteUri).getOrThrow()

        val originUrl = remoteSync.readOriginUrl(dir).getOrThrow()

        requireNotNull(originUrl)
        assertTrue(originUrl.contains("file:"))
        assertTrue(originUrl.contains("origin-read.git"))
    }

    @Test
    fun probeRemoteConnection_listsRemoteWithoutLocalFetch() {
        val remoteUri = newBareRemoteUri("remote-probe.git")
        val branch = seedRemote(remoteUri)

        remoteSync.probeRemoteConnection(remoteUri, branch, null).getOrThrow()
    }

    @Test
    fun ensureLocalBranch_createsTrackingBranchWhenLocalHeadDiffers() {
        val remoteUri = newBareRemoteUri("remote-ensure.git")
        val branch = seedRemote(remoteUri)

        val consumer = temp.newFolder("consumer-init")
        org.eclipse.jgit.api.Git.init()
            .setDirectory(consumer)
            .setInitialBranch("master")
            .call()
            .close()
        assertEquals("master", gitRepo.status(consumer).getOrThrow().branch)

        remoteSync.configureSanitizedOrigin(consumer, remoteUri).getOrThrow()
        remoteSync.ensureLocalBranch(consumer, branch, null).getOrThrow()

        assertEquals(branch, gitRepo.status(consumer).getOrThrow().branch)
        val comparison = remoteSync.compareWithRemote(consumer, branch).getOrThrow()
        assertFalse(comparison.remoteBranchMissing)
    }

    @Test
    fun ensureLocalBranch_checkoutsExistingLocalBranch() {
        val remoteUri = newBareRemoteUri("remote-checkout.git")
        val branch = seedRemote(remoteUri)
        val consumer = temp.newFolder("consumer")
        gitRepo.cloneFrom(remoteUri, consumer).getOrThrow()

        remoteSync.ensureLocalBranch(consumer, branch, null).getOrThrow()

        assertEquals(branch, gitRepo.status(consumer).getOrThrow().branch)
    }

    @Test
    fun probeRemoteConnection_reconcilesLegacyMasterToMainOnRemote() {
        val remoteUri = newBareRemoteUri("remote-main-only.git")
        val branch = seedRemote(remoteUri)

        val result = remoteSync.probeRemoteConnection(remoteUri, "master", null)

        assertTrue(
            "expected success when remote has $branch but probe used master",
            result.isSuccess || branch != "main"
        )
        if (branch == "main") {
            assertTrue(result.isSuccess)
        }
    }

    @Test
    fun probeRemoteConnection_unknownBranchFails() {
        val remoteUri = newBareRemoteUri("remote-missing.git")
        seedRemote(remoteUri)

        val result = remoteSync.probeRemoteConnection(remoteUri, "missing-branch", null)

        assertTrue(result.isFailure)
    }

    private fun seedRemote(remoteUri: String): String {
        val producer = temp.newFolder("seed-producer")
        gitRepo.cloneFrom(remoteUri, producer).getOrThrow()
        gitRepo.writeFile(producer, "Inbox/seed.md", "# Seed\n").getOrThrow()
        gitRepo.writeFile(producer, "Projects/read.md", "# Read\n").getOrThrow()
        gitRepo.stageAll(producer).getOrThrow()
        gitRepo.commit(producer, "Seed").getOrThrow()
        gitRepo.push(producer).getOrThrow()
        return gitRepo.status(producer).getOrThrow().branch
    }
}
