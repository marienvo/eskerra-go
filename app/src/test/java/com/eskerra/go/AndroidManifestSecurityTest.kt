package com.eskerra.go

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Static manifest checks for PoC security defaults. */
class AndroidManifestSecurityTest {

    @Test
    fun allowBackup_isDisabled() {
        val manifest = File("src/main/AndroidManifest.xml")
        assertTrue("Expected manifest at ${manifest.absolutePath}", manifest.isFile)
        val text = manifest.readText()
        assertTrue(
            "Android backup must be disabled for PoC credential safety",
            """android:allowBackup="false"""" in text ||
                """android:allowBackup='false'""" in text
        )
    }
}
