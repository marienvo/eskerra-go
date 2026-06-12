package com.eskerra.go.core.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vectors ported from `splitYamlFrontmatter.test.ts` and `vaultWikiLinkPreprocess.test.ts`.
 */
class VaultMarkdownPreprocessTest {

    // --- splitYamlFrontmatter ---

    @Test
    fun frontmatter_absent_returnsNull() {
        val md = "# Title\n\nBody"
        val result = VaultMarkdownPreprocess.splitYamlFrontmatter(md)
        assertNull(result.frontmatter)
        assertEquals(md, result.body)
        assertEquals("", result.leadingBeforeFrontmatter)
    }

    @Test
    fun frontmatter_leadingBlocksButNoFrontmatter_returnsNull() {
        val md = "\n\n# Hello"
        val result = VaultMarkdownPreprocess.splitYamlFrontmatter(md)
        assertNull(result.frontmatter)
        assertEquals(md, result.body)
    }

    @Test
    fun frontmatter_wellFormed_splits() {
        val md = "---\nfoo: bar\n---\n\n# From body\n"
        val result = VaultMarkdownPreprocess.splitYamlFrontmatter(md)
        assertEquals("---\nfoo: bar\n---", result.frontmatter)
        assertEquals("\n# From body\n", result.body)
        assertEquals("", result.leadingBeforeFrontmatter)
    }

    @Test
    fun frontmatter_allowsBlankLinesBefore() {
        val md = "\n\n---\nk: v\n---\n\nBody"
        val result = VaultMarkdownPreprocess.splitYamlFrontmatter(md)
        assertEquals("---\nk: v\n---", result.frontmatter)
        assertEquals("\nBody", result.body)
        assertEquals("\n\n", result.leadingBeforeFrontmatter)
    }

    @Test
    fun frontmatter_missingClosing_returnsNull() {
        val md = "---\nopen only\n# Still here\n"
        val result = VaultMarkdownPreprocess.splitYamlFrontmatter(md)
        assertNull(result.frontmatter)
        assertEquals(md, result.body)
    }

    @Test
    fun frontmatter_onlyOpeningLine_returnsNull() {
        val md = "---\n"
        val result = VaultMarkdownPreprocess.splitYamlFrontmatter(md)
        assertNull(result.frontmatter)
        assertEquals("---\n", result.body)
    }

    @Test
    fun frontmatter_immediateClosing_isEmptyFrontmatter() {
        val md = "---\n---\n\nHello"
        val result = VaultMarkdownPreprocess.splitYamlFrontmatter(md)
        assertEquals("---\n---", result.frontmatter)
        assertEquals("\nHello", result.body)
    }

    @Test
    fun frontmatter_normalizesCrlf() {
        val md = "---\r\nx: 1\r\n---\r\n\r\nok"
        val result = VaultMarkdownPreprocess.splitYamlFrontmatter(md)
        assertEquals("---\nx: 1\n---", result.frontmatter)
        assertEquals("\nok", result.body)
    }

    // --- wikiLinksToSyntheticMarkdownLinks ---

    @Test
    fun wiki_rewritesSimpleLink() {
        assertEquals(
            "See [Alpha](eskerra-wiki:Alpha) here.",
            VaultMarkdownPreprocess.wikiLinksToSyntheticMarkdownLinks("See [[Alpha]] here.")
        )
    }

    @Test
    fun wiki_supportsDisplayTextAfterPipe() {
        assertEquals(
            "[Beta](eskerra-wiki:Alpha%7CBeta)",
            VaultMarkdownPreprocess.wikiLinksToSyntheticMarkdownLinks("[[Alpha|Beta]]")
        )
    }

    @Test
    fun wiki_escapesBackslashesInLabel() {
        assertEquals(
            "[a\\\\b](eskerra-wiki:t%7Ca%5Cb)",
            VaultMarkdownPreprocess.wikiLinksToSyntheticMarkdownLinks("[[t|a\\b]]")
        )
    }

    // --- transformOutsideTripleBacktickFences ---

    @Test
    fun fences_doesNotRewriteInsideFencedBlocks() {
        val input = "before [[In]]\n```md\n[[Skip]]\n```\nafter [[Out]]"
        val got = VaultMarkdownPreprocess.transformOutsideTripleBacktickFences(
            input,
            VaultMarkdownPreprocess::wikiLinksToSyntheticMarkdownLinks
        )
        assertTrue(got.contains("[[Skip]]"))
        assertTrue(got.contains("[In](eskerra-wiki:In)"))
        assertTrue(got.contains("[Out](eskerra-wiki:Out)"))
    }

    // --- preprocessVaultReadonlyMarkdownBody ---

    @Test
    fun preprocess_combinesFenceSkippingAndWikiRewrite() {
        val md = "# Hi\n\n[[x]]\n\n```\n[[y]]\n```\n"
        val got = VaultMarkdownPreprocess.preprocessVaultReadonlyMarkdownBody(md)
        assertTrue(got.contains("[x](eskerra-wiki:x)"))
        assertTrue(got.contains("[[y]]"))
    }
}
