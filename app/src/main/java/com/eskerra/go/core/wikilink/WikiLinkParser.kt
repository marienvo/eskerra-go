package com.eskerra.go.core.wikilink

import com.eskerra.go.core.model.WikiLink

/**
 * Scans raw markdown text for `[[wiki link]]` tokens. Does not understand markdown
 * fences, escapes, or nested links.
 */
object WikiLinkParser {

    fun parse(markdown: String): List<WikiLink> {
        val links = mutableListOf<WikiLink>()
        var searchFrom = 0

        while (searchFrom < markdown.length) {
            val tokenStart = markdown.indexOf("[[", searchFrom)
            if (tokenStart == -1) {
                break
            }

            val tokenEnd = markdown.indexOf("]]", tokenStart + 2)
            if (tokenEnd == -1) {
                break
            }

            val innerText = markdown.substring(tokenStart + 2, tokenEnd)
            val fullTokenEnd = tokenEnd + 1

            if (isMalformedInnerText(innerText)) {
                searchFrom = tokenEnd + 2
                continue
            }

            val pipeIndex = innerText.indexOf('|')
            val rawTarget: String
            val rawLabel: String?
            if (pipeIndex == -1) {
                rawTarget = innerText
                rawLabel = null
            } else {
                rawTarget = innerText.substring(0, pipeIndex)
                rawLabel = innerText.substring(pipeIndex + 1)
            }

            val target = rawTarget.trim()
            val label = rawLabel?.trim()
            val displayText = label?.takeIf { it.isNotEmpty() } ?: target

            links.add(
                WikiLink(
                    target = target,
                    displayText = displayText,
                    sourceRange = tokenStart..fullTokenEnd,
                    hasValidTarget = target.isNotEmpty()
                )
            )

            searchFrom = tokenEnd + 2
        }

        return links
    }

    private fun isMalformedInnerText(innerText: String): Boolean =
        innerText.contains('[') || innerText.contains(']')
}
