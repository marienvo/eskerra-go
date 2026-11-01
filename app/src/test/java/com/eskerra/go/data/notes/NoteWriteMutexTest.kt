package com.eskerra.go.data.notes

import com.eskerra.go.core.model.NotePath
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.git.GitSyncMutex
import com.eskerra.go.data.git.JGitWorkspaceRepository
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NoteWriteMutexTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun noteWritesWaitForTheSyncMutex() = runBlocking {
        val filesDir = temp.newFolder()
        val root = File(filesDir, "vault/Inbox").apply { mkdirs() }
        val note = File(root, "note.md").apply { writeText("before") }
        val mutex = GitSyncMutex()
        val repository = FileNoteWriteRepository(JGitWorkspaceRepository(), mutex)
        val config = WorkspaceConfig("Test", "vault", null, "main", 1)
        mutex.mutex.lock()
        try {
            val pending = async {
                repository.write(config, filesDir, NotePath("Inbox/note.md"), "after")
            }
            assertNull(withTimeoutOrNull(100) { pending.await() })
            assertEquals("before", note.readText())
            mutex.mutex.unlock()
            assertTrue(pending.await().isSuccess)
            assertEquals("after", note.readText())
        } finally {
            if (mutex.mutex.isLocked) mutex.mutex.unlock()
        }
    }
}
