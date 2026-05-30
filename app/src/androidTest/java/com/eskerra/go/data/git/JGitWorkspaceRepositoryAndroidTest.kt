package com.eskerra.go.data.git

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.URIish
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Proves the core spike cycle runs on a real Android runtime against app-private
 * internal storage (`context.filesDir`): init/open, write a markdown file,
 * stage, commit, push to a local `file://` bare remote, and fetch.
 *
 * This is the on-device counterpart to the JVM tests; it validates that JGit's
 * `java.nio.file`/`java.time` usage behaves on `minSdk 26`. No network, no
 * credentials: the "remote" is another directory under `filesDir`.
 */
@RunWith(AndroidJUnit4::class)
class JGitWorkspaceRepositoryAndroidTest {

    private lateinit var root: File
    private val repo = JGitWorkspaceRepository()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        root = File(context.filesDir, "spike-test/${System.nanoTime()}")
        assertTrue(root.mkdirs())
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun initOrOpen_worksOnAppPrivateStorage() {
        val dir = File(root, "local-only").also { assertTrue(it.mkdirs()) }

        repo.initOrOpen(dir).getOrThrow()

        assertTrue(File(dir, ".git").isDirectory)
    }

    @Test
    fun fullCycle_initWriteCommitPushFetch_onFilesDir() {
        // Local bare repo acts as the remote; no network, no credentials.
        val bare = File(root, "remote.git").also { it.mkdirs() }
        Git.init().setBare(true).setDirectory(bare).call().close()
        val remoteUri = bare.toURI().toString()

        // Initialize a fresh repo in app-private storage (the init path).
        val workspace = File(root, "workspace").also { assertTrue(it.mkdirs()) }
        repo.initOrOpen(workspace).getOrThrow()

        // Wire origin to the local bare repo (test-side plumbing only).
        Git.open(workspace).use { git ->
            git.remoteAdd().setName("origin").setUri(URIish(remoteUri)).call()
        }

        repo.writeFile(workspace, "inbox/note.md", "# On-device\n").getOrThrow()
        repo.stageAll(workspace).getOrThrow()
        val commitId = repo.commit(workspace, "Add on-device note").getOrThrow()
        assertTrue(commitId.isNotBlank())

        repo.push(workspace).getOrThrow()
        repo.fetch(workspace).getOrThrow()

        // Verify the pushed content is observable via a fresh clone.
        val verify = File(root, "verify")
        repo.cloneFrom(remoteUri, verify).getOrThrow()
        val pulled = File(verify, "inbox/note.md")
        assertTrue(pulled.isFile)
        assertEquals("# On-device\n", pulled.readText())
    }
}
