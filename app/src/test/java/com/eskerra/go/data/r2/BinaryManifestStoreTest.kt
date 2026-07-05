package com.eskerra.go.data.r2

import com.eskerra.go.core.model.BinaryManifestEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BinaryManifestStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `round-trips entries`() {
        val store = BinaryManifestStore(temp.newFolder("files"))
        val entries = listOf(
            BinaryManifestEntry("Assets/a.pdf", "binaries/Assets/a.pdf", 100, "e1"),
            BinaryManifestEntry("b.bin", "binaries/b.bin", 200, "e2")
        )

        store.write(entries)

        assertEquals(entries, store.read())
    }

    @Test
    fun `missing file reads as empty`() {
        val store = BinaryManifestStore(temp.newFolder("files"))
        assertTrue(store.read().isEmpty())
    }

    @Test
    fun `corrupt file reads as empty`() {
        val dir = temp.newFolder("files")
        val store = BinaryManifestStore(dir)
        java.io.File(dir, "binaries-manifest.json").writeText("{ not json")
        assertTrue(store.read().isEmpty())
    }
}
