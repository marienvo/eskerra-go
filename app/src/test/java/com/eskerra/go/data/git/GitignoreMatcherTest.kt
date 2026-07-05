package com.eskerra.go.data.git

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GitignoreMatcherTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun matcherFor(gitignore: String, exclude: String? = null): GitignoreMatcher {
        val root = temp.newFolder("workspace")
        File(root, ".gitignore").writeText(gitignore)
        if (exclude != null) {
            File(root, ".git/info").mkdirs()
            File(root, ".git/info/exclude").writeText(exclude)
        }
        return GitignoreMatcher.forWorkspace(root)
    }

    @Test
    fun `ignores files under an ignored directory`() {
        val matcher = matcherFor("Assets/\nbuild/\n")
        assertTrue(matcher.isIgnored("Assets/photo.jpg"))
        assertTrue(matcher.isIgnored("build/output.bin"))
    }

    @Test
    fun `does not ignore tracked paths`() {
        val matcher = matcherFor("Assets/\n")
        assertFalse(matcher.isIgnored("notes/today.md"))
        assertFalse(matcher.isIgnored("README.md"))
    }

    @Test
    fun `honours glob and negation`() {
        val matcher = matcherFor("*.log\n!keep.log\n")
        assertTrue(matcher.isIgnored("run.log"))
        assertFalse(matcher.isIgnored("keep.log"))
    }

    @Test
    fun `applies git info exclude rules`() {
        val matcher = matcherFor(gitignore = "", exclude = "secret/\n")
        assertTrue(matcher.isIgnored("secret/token.bin"))
    }

    @Test
    fun `empty gitignore ignores nothing`() {
        val matcher = matcherFor("")
        assertFalse(matcher.isIgnored("Assets/photo.jpg"))
    }
}
