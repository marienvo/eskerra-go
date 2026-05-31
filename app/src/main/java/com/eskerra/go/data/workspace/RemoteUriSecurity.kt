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

    /** Returns true for `file://` and `https://` remotes supported in Step 9. */
    fun isSupportedRemoteScheme(uri: String): Boolean {
        val trimmed = uri.trim()
        return isHttpsRemoteUri(trimmed) || isFileRemoteUri(trimmed)
    }

    fun containsEmbeddedCredentials(uri: String): Boolean {
        val trimmed = uri.trim()
        if (trimmed.isEmpty()) return false
        return try {
            val parsed = URI(trimmed)
            !parsed.userInfo.isNullOrEmpty() ||
                parsed.rawAuthority?.contains("@") == true ||
                AUTHORITY_WITH_USERINFO.containsMatchIn(trimmed)
        } catch (_: URISyntaxException) {
            AUTHORITY_WITH_USERINFO.containsMatchIn(trimmed)
        }
    }

    private fun isHttpsRemoteUri(uri: String): Boolean =
        uri.startsWith("https://", ignoreCase = true)

    private fun isFileRemoteUri(uri: String): Boolean =
        uri.startsWith("file://") || uri.startsWith("file:/")
}
