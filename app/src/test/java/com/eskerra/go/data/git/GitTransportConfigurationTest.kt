package com.eskerra.go.data.git

import org.eclipse.jgit.api.TransportCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GitTransportConfigurationTest {

    @Test
    fun configureSyncTransport_setsTimeoutAndCredentials() {
        val command = RecordingTransportCommand()

        command.configureSyncTransport(
            httpsToken = "token",
            timeoutSeconds = 7
        )

        assertEquals(7, command.configuredTimeout)
        assertNotNull(command.configuredCallback)
    }

    private class RecordingTransportCommand :
        TransportCommand<RecordingTransportCommand, Unit>(null) {

        val configuredTimeout: Int
            get() = timeout

        val configuredCallback
            get() = transportConfigCallback

        override fun call() = Unit
    }
}
