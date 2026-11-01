package com.eskerra.go.core.inbox

import java.io.StringReader
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.ScalarNode

/** Parse frontmatter without serializing it back to YAML. */
internal object InboxYamlFrontmatter {
    private val opening = Regex("\\A\\uFEFF?(?:[ \\t]*\\r?\\n)*---[ \\t]*(?:\\r?\\n|$)")
    private val closing = Regex("(?m)^---[ \\t]*\\r?$")

    data class Block(val start: Int, val end: Int, val inner: String, val node: MappingNode?)

    fun parse(markdown: String): Block? {
        val match = opening.find(markdown) ?: return null
        val start = match.range.last + 1
        val close = closing.find(markdown, start) ?: error("Unclosed frontmatter")
        val inner = markdown.substring(start, close.range.first)
        val options = LoaderOptions().apply {
            isAllowDuplicateKeys = false
            maxAliasesForCollections = 20
        }
        val yaml = Yaml(SafeConstructor(options))
        val parsed = yaml.load<Any?>(inner)
        require(parsed == null || parsed is Map<*, *>) { "Frontmatter must be a mapping" }
        val node = yaml.compose(StringReader(inner))
        require(node == null || node is MappingNode) { "Frontmatter must be a mapping" }
        return Block(start, close.range.first, inner, node as? MappingNode)
    }

    fun valueNode(block: Block): ScalarNode? {
        val pair = block.node?.value?.firstOrNull {
            (it.keyNode as? ScalarNode)?.value == "snoozed_until"
        } ?: return null
        val value = pair.valueNode as? ScalarNode ?: error("Snooze value must be a scalar")
        require(value.anchor == null && value.startMark.index >= pair.keyNode.endMark.index) {
            "Snooze aliases and anchors are unsupported"
        }
        return value
    }
}
