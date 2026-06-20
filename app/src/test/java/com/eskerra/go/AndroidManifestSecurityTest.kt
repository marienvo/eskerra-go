package com.eskerra.go

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Static manifest checks for security defaults. */
class AndroidManifestSecurityTest {

    @Test
    fun internetPermission_isDeclared() {
        val manifest = File("src/main/AndroidManifest.xml")
        assertTrue("Expected manifest at ${manifest.absolutePath}", manifest.isFile)
        val text = manifest.readText()
        assertTrue(
            "HTTPS clone requires INTERNET permission",
            text.contains("android.permission.INTERNET")
        )
    }

    @Test
    fun allowBackup_isDisabled() {
        val manifest = File("src/main/AndroidManifest.xml")
        assertTrue("Expected manifest at ${manifest.absolutePath}", manifest.isFile)
        val text = manifest.readText()
        assertTrue(
            "Android backup must be disabled for credential safety",
            """android:allowBackup="false"""" in text ||
                """android:allowBackup='false'""" in text
        )
    }
}
