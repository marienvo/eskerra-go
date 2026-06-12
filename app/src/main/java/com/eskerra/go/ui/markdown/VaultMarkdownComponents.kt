package com.eskerra.go.ui.markdown

import androidx.compose.runtime.Composable
import com.eskerra.go.core.model.NoteId
import com.mikepenz.markdown.compose.components.MarkdownComponents
import com.mikepenz.markdown.compose.components.markdownComponents
import java.io.File

/** Shared markdown component overrides for the vault read-only renderer (spec §8 + §13 images). */
@Composable
fun vaultMarkdownComponents(workspaceRoot: File?, sourceNoteId: NoteId?): MarkdownComponents =
    markdownComponents(
        image = { model ->
            VaultMarkdownImage(
                model = model,
                workspaceRoot = workspaceRoot,
                sourceNoteId = sourceNoteId
            )
        }
    )
