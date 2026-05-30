package com.eskerra.go.data.git

import org.eclipse.jgit.api.TransportConfigCallback
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

/**
 * Builds in-memory JGit credential providers for HTTPS token auth.
 *
 * Tokens are supplied only through [CredentialsProvider]; they must never be
 * embedded in remote URLs or written to `.git/config`.
 */
object HttpsTokenCredentialsProviderFactory {

    /** Username accepted by GitHub and GitLab HTTPS token auth. */
    const val HTTPS_USERNAME = "x-access-token"

    fun credentialsProvider(token: String): CredentialsProvider =
        UsernamePasswordCredentialsProvider(HTTPS_USERNAME, token)

    fun transportConfigCallback(token: String): TransportConfigCallback =
        TransportConfigCallback { transport ->
            transport.credentialsProvider = credentialsProvider(token)
        }
}
