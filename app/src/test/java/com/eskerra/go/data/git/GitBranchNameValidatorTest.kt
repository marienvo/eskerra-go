package com.eskerra.go.data.git

import org.junit.Assert.assertTrue
import org.junit.Test

class GitBranchNameValidatorTest {

    @Test
    fun validate_rejectsBlankBranch() {
        val result = GitBranchNameValidator.validate("   ")
        assertTrue(result.isFailure)
    }

    @Test
    fun validate_rejectsLeadingAndTrailingWhitespace() {
        assertTrue(GitBranchNameValidator.validate(" main").isFailure)
        assertTrue(GitBranchNameValidator.validate("main ").isFailure)
        assertTrue(GitBranchNameValidator.validate(" main ").isFailure)
    }

    @Test
    fun validate_rejectsInternalWhitespace() {
        val result = GitBranchNameValidator.validate("feature branch")
        assertTrue(result.isFailure)
    }

    @Test
    fun validate_rejectsControlCharacters() {
        val result = GitBranchNameValidator.validate("main\u0007")
        assertTrue(result.isFailure)
    }

    @Test
    fun validate_rejectsParentTraversal() {
        val result = GitBranchNameValidator.validate("feature..fix")
        assertTrue(result.isFailure)
    }

    @Test
    fun validate_rejectsBackslash() {
        val result = GitBranchNameValidator.validate("feature\\fix")
        assertTrue(result.isFailure)
    }

    @Test
    fun validate_rejectsLeadingSlash() {
        val result = GitBranchNameValidator.validate("/main")
        assertTrue(result.isFailure)
    }

    @Test
    fun validate_rejectsLockSuffix() {
        val result = GitBranchNameValidator.validate("main.lock")
        assertTrue(result.isFailure)
    }

    @Test
    fun validate_rejectsRefspecLikeValues() {
        assertTrue(GitBranchNameValidator.validate("refs/heads/main").isFailure)
        assertTrue(GitBranchNameValidator.validate("main:origin/main").isFailure)
        assertTrue(GitBranchNameValidator.validate("main^").isFailure)
    }

    @Test
    fun validate_allowsSimpleBranchNames() {
        val result = GitBranchNameValidator.validate("main")
        assertTrue(result.isSuccess)
    }

    @Test
    fun validate_allowsSlashSeparatedBranchNames() {
        val result = GitBranchNameValidator.validate("feature/my-work")
        assertTrue(result.isSuccess)
    }
}
