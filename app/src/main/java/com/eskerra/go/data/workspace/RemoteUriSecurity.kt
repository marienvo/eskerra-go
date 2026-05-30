package com.eskerra.go.data.workspace

import java.net.URI
import java.net.URISyntaxException

/** Guards against remote URLs that embed credentials in URI userinfo. */
object RemoteUriSecurity {

    private val AUTHORITY_WITH_USERINFO = Regex("""//[^/?#]*@""")

    fun validateNoEmbeddedCredentials(uri: String): Result<Unit> {
        if (containsEmbeddedCredentials(uri)) {
            return Result.failure(
                WorkspaceSetupException(WorkspaceSetupError.CredentialBearingRemoteUri)
            )
        }
        return Result.success(Unit)
    }

    fun containsEmbeddedCredentials(uri: String): Boolean {
        val trimmed = uri.trim()
        if (trimmed.isEmpty()) return false
        return try {
            !URI(trimmed).userInfo.isNullOrEmpty()
        } catch (_: URISyntaxException) {
            AUTHORITY_WITH_USERINFO.containsMatchIn(trimmed)
        }
    }
}
