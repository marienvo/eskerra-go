package com.eskerra.go.data.git

import org.eclipse.jgit.api.TransportCommand
import org.eclipse.jgit.api.TransportConfigCallback

internal const val DEFAULT_GIT_TRANSPORT_TIMEOUT_SECONDS = 30

/** Applies the bounded network policy shared by every JGit transport command. */
internal fun <C : TransportCommand<*, *>> C.configureSyncTransport(
    httpsToken: String?,
    fallbackCallback: TransportConfigCallback? = null,
    timeoutSeconds: Int = DEFAULT_GIT_TRANSPORT_TIMEOUT_SECONDS
): C {
    require(timeoutSeconds > 0) { "timeoutSeconds must be positive" }
    setTimeout(timeoutSeconds)
    val callback = httpsToken?.let {
        HttpsTokenCredentialsProviderFactory.transportConfigCallback(it)
    } ?: fallbackCallback
    callback?.let { setTransportConfigCallback(it) }
    return this
}
