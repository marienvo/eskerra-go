package com.eskerra.go.data.git

import java.io.File
import org.eclipse.jgit.api.Git

/**
 * Helpers for creating throwaway Git repositories for the spike tests.
 *
 * Everything here operates on caller-provided temporary directories (JUnit
 * [org.junit.rules.TemporaryFolder] in the tests). It never touches a real
 * remote or the developer's notes repository.
 */
object TestGitRepos {

    /** Create an empty bare repository at [dir] to act as a local `file://` remote. */
    fun initBareRemote(dir: File): File {
        dir.mkdirs()
        Git.init().setBare(true).setDirectory(dir).call().close()
        return dir
    }

    /** A `file://` URI for [dir], suitable as a JGit remote URI. */
    fun fileUri(dir: File): String = dir.toURI().toString()
}
