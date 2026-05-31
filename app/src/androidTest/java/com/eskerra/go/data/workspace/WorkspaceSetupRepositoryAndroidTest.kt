package com.eskerra.go.data.workspace

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eskerra.go.data.git.JGitWorkspaceRepository
import java.io.File
import kotlinx.coroutines.runBlocking
import org.eclipse.jgit.api.Git
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceSetupRepositoryAndroidTest {

    private lateinit var root: File
    private lateinit var filesDir: File
    private val gitRepository = JGitWorkspaceRepository()
    private val repository = DefaultWorkspaceSetupRepository(gitRepository)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        root = File(context.filesDir, "setup-test/${System.nanoTime()}")
        assertTrue(root.mkdirs())
        filesDir = root
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun initializeLocal_onFilesDir() = runBlocking {
        val result = repository.completeSetup(
            mode = WorkspaceSetupMode.InitializeLocal,
            name = "Device Notes",
            branch = "",
            remoteUri = null,
            credential = null,
            filesDir = filesDir
        )

        assertTrue(result.isSuccess)
        val config = result.getOrThrow()
        assertEquals("Device Notes", config.name)
        assertTrue(
            WorkspacePaths.isValidGitWorkspace(File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH))
        )
    }

    @Test
    fun cloneFromFileRemote_onFilesDir() = runBlocking {
        val bare = File(root, "remote.git").also { it.mkdirs() }
        Git.init().setBare(true).setDirectory(bare).call().close()
        val remoteUri = bare.toURI().toString()

        val producer = File(root, "producer")
        gitRepository.cloneFrom(remoteUri, producer).getOrThrow()
        gitRepository.writeFile(producer, "notes/device.md", "# Device\n").getOrThrow()
        gitRepository.stageAll(producer).getOrThrow()
        gitRepository.commit(producer, "Seed device note").getOrThrow()
        gitRepository.push(producer).getOrThrow()

        val cloneTargetRoot = File(root, "clone-consumer")
        assertTrue(cloneTargetRoot.mkdirs())

        val result = repository.completeSetup(
            mode = WorkspaceSetupMode.Clone,
            name = "Cloned Device Notes",
            branch = "master",
            remoteUri = remoteUri,
            credential = null,
            filesDir = cloneTargetRoot
        )

        assertTrue(result.isSuccess)
        assertTrue(File(cloneTargetRoot, "workspace/notes/device.md").isFile)
    }
}
