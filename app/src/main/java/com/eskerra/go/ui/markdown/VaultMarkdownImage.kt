package com.eskerra.go.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.eskerra.go.core.attachments.AttachmentPaths
import com.eskerra.go.core.model.NoteId
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import java.io.File
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode

/**
 * Standalone markdown image renderer (spec §13): resolves vault-relative attachment paths against
 * [workspaceRoot], loads via Coil (`file://` / remote), scales to width, and shows placeholders for
 * SVG and broken images.
 */
@Composable
fun VaultMarkdownImage(
    model: MarkdownComponentModel,
    workspaceRoot: File?,
    sourceNoteId: NoteId?,
    modifier: Modifier = Modifier
) {
    val content = model.content
    val node = model.node
    val rawSrc = imageNodeText(content, node, MarkdownElementTypes.LINK_DESTINATION)
        ?.trim()
        ?.removeSurrounding("<", ">")
        ?: return

    val alt = imageNodeText(content, node, MarkdownElementTypes.LINK_TEXT)
        ?.takeIf { it.isNotBlank() }

    val target = remember(rawSrc, sourceNoteId) {
        AttachmentPaths.resolveVaultImageLoadTarget(sourceNoteId, rawSrc)
    }

    if (target is AttachmentPaths.ResolvedImageSrc.Local &&
        AttachmentPaths.isSvgPath(target.vaultRelativePath)
    ) {
        BrokenImagePlaceholder(
            alt = alt,
            message = "SVG preview unavailable",
            modifier = modifier
        )
        return
    }

    val loadUri = remember(target, workspaceRoot) {
        vaultImageLoadUri(workspaceRoot, target)
    }

    if (loadUri == null) {
        BrokenImagePlaceholder(
            alt = alt,
            message = "Image unavailable",
            modifier = modifier
        )
        return
    }

    SubcomposeAsyncImage(
        model = loadUri,
        contentDescription = alt,
        modifier = modifier.fillMaxWidth(),
        contentScale = ContentScale.FillWidth,
        loading = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        },
        error = {
            BrokenImagePlaceholder(
                alt = alt,
                message = "Image unavailable",
                modifier = modifier
            )
        }
    )
}

internal fun vaultImageLoadUri(
    workspaceRoot: File?,
    target: AttachmentPaths.ResolvedImageSrc
): String? = when (target) {
    is AttachmentPaths.ResolvedImageSrc.Remote -> target.url
    is AttachmentPaths.ResolvedImageSrc.Local -> {
        val root = workspaceRoot ?: return null
        val file = File(root, target.vaultRelativePath)
        if (!file.isFile) null else file.toURI().toString()
    }
    is AttachmentPaths.ResolvedImageSrc.Unresolvable -> null
}

private fun imageNodeText(content: String, node: ASTNode, type: IElementType): String? =
    findChildNode(node, type)?.let { child ->
        content.substring(child.startOffset, child.endOffset)
    }

private fun findChildNode(node: ASTNode, type: IElementType): ASTNode? {
    if (node.type == type) return node
    for (child in node.children) {
        findChildNode(child, type)?.let { return it }
    }
    return null
}

@Composable
private fun BrokenImagePlaceholder(alt: String?, message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(VaultMarkdownTokens.CodeBackground)
            .padding(vertical = 16.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = alt?.let { "$message: $it" } ?: message,
            color = VaultMarkdownTokens.Muted
        )
    }
}
