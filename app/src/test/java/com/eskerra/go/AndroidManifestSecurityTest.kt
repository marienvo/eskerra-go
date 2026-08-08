package com.eskerra.go

import java.io.File
import org.junit.Assert.assertFalse
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

    @Test
    fun shareTarget_acceptsPlainTextOnly() {
        val text = File("src/main/AndroidManifest.xml").readText()
        assertTrue(
            "Share sheet entry requires an ACTION_SEND filter",
            text.contains("android.intent.action.SEND")
        )
        assertTrue(text.contains("""<data android:mimeType="text/plain" />"""))
        assertTrue(
            "The chooser label lives on the filter, not the activity",
            text.contains("""<intent-filter android:label="Add note">""")
        )
        assertFalse(
            "ACTION_SEND_MULTIPLE is deliberately not handled",
            text.contains("android.intent.action.SEND_MULTIPLE")
        )
        assertFalse(
            "A wildcard mime type would put Eskerra in every share sheet",
            text.contains("""android:mimeType="*/*""")
        )
    }

    @Test
    fun mainActivity_isSingleTask() {
        val text = File("src/main/AndroidManifest.xml").readText()
        assertTrue(
            "A second MainActivity would duplicate the whole composition root",
            text.contains("""android:launchMode="singleTask"""")
        )
    }

    @Test
    fun mediaSessionService_isDeclaredWithPlaybackType() {
        val manifest = File("src/main/AndroidManifest.xml")
        assertTrue("Expected manifest at ${manifest.absolutePath}", manifest.isFile)
        val text = manifest.readText()
        assertTrue(text.contains(".data.player.PodcastPlaybackService"))
        assertTrue(text.contains("""android:foregroundServiceType="mediaPlayback""""))
        assertTrue(text.contains("androidx.media3.session.MediaSessionService"))
    }
}
