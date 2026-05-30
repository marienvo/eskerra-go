package com.eskerra.go.data.git

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * JVM tests for the remote operations of [JGitWorkspaceRepository] against a
 * local `file://` bare repository acting as the remote. No network, no
 * credentials.
 */
class JGitWorkspaceRemoteTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val repo = JGitWorkspaceRepository()

    private fun newBareRemoteUri(name: String): String {
        val bare = TestGitRepos.initBareRemote(File(temp.root, name))
        return TestGitRepos.fileUri(bare)
    }

    @Test
    fun pushedCommit_isVisibleToAFreshClone() {
        val remoteUri = newBareRemoteUri("remote.git")

        val producer = temp.newFolder("producer")
        repo.cloneFrom(remoteUri, producer).getOrThrow()
        repo.writeFile(producer, "notes/shared.md", "# Shared\n").getOrThrow()
        repo.stageAll(producer).getOrThrow()
        repo.commit(producer, "Add shared note").getOrThrow()
        repo.push(producer).getOrThrow()

        val consumer = temp.newFolder("consumer")
        repo.cloneFrom(remoteUri, consumer).getOrThrow()

        val pulledFile = File(consumer, "notes/shared.md")
        assertTrue(pulledFile.isFile)
        assertEquals("# Shared\n", pulledFile.readText())
    }

    @Test
    fun cloneFrom_failsWhenTargetIsNotEmpty() {
        val remoteUri = newBareRemoteUri("remote.git")
        val target = temp.newFolder("occupied")
        File(target, "preexisting.txt").writeText("keep me")

        val result = repo.cloneFrom(remoteUri, target)

        assertTrue(result.isFailure)
        assertTrue("existing content must be untouched", File(target, "preexisting.txt").isFile)
    }

    @Test
    fun fetchAndFastForwardPull_bringInRemoteCommits() {
        val remoteUri = newBareRemoteUri("remote.git")

        val producer = temp.newFolder("producer")
        repo.cloneFrom(remoteUri, producer).getOrThrow()
        repo.writeFile(producer, "notes/v1.md", "v1").getOrThrow()
        repo.stageAll(producer).getOrThrow()
        repo.commit(producer, "v1").getOrThrow()
        repo.push(producer).getOrThrow()

        val consumer = temp.newFolder("consumer")
        repo.cloneFrom(remoteUri, consumer).getOrThrow()

        // Producer adds a second commit and pushes it.
        repo.writeFile(producer, "notes/v2.md", "v2").getOrThrow()
        repo.stageAll(producer).getOrThrow()
        repo.commit(producer, "v2").getOrThrow()
        repo.push(producer).getOrThrow()

        repo.fetch(consumer).getOrThrow()
        repo.pullFastForwardOnly(consumer).getOrThrow()

        assertTrue(File(consumer, "notes/v2.md").isFile)
    }

    @Test
    fun pullFastForwardOnly_failsOnDivergentHistory() {
        val remoteUri = newBareRemoteUri("remote.git")

        val producer = temp.newFolder("producer")
        repo.cloneFrom(remoteUri, producer).getOrThrow()
        repo.writeFile(producer, "notes/base.md", "base").getOrThrow()
        repo.stageAll(producer).getOrThrow()
        repo.commit(producer, "base").getOrThrow()
        repo.push(producer).getOrThrow()

        val consumer = temp.newFolder("consumer")
        repo.cloneFrom(remoteUri, consumer).getOrThrow()

        // Consumer creates a divergent local commit.
        repo.writeFile(consumer, "notes/local.md", "local").getOrThrow()
        repo.stageAll(consumer).getOrThrow()
        repo.commit(consumer, "local divergent").getOrThrow()

        // Producer pushes a different commit on top of base.
        repo.writeFile(producer, "notes/remote.md", "remote").getOrThrow()
        repo.stageAll(producer).getOrThrow()
        repo.commit(producer, "remote divergent").getOrThrow()
        repo.push(producer).getOrThrow()

        val result = repo.pullFastForwardOnly(consumer)

        assertTrue(result.isFailure)
        // The merge must not have happened: the remote-only file is absent.
        assertFalse(File(consumer, "notes/remote.md").exists())
    }
}
