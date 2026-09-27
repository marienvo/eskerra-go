package com.eskerra.go.core.usecase

import com.eskerra.go.core.markdown.VaultMarkdownPreprocess
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.NoteContentCachePort
import com.eskerra.go.core.repository.ParsedMarkdownCachePort
import java.io.File

/**
 * Warms one note's content and parsed body into the shared caches, so a later reader open (a tap,
 * or the note landing back in view) can paint atomically from memory. Shared by
 * [NotePrefetchScheduler]'s background batches and by a direct tap's short "wait briefly, then
 * switch" budget — both warm exactly the same way, so a tap that lands on an in-flight or
 * already-warm prefetch target does no redundant work (the caches themselves de-duplicate; see
 * `NoteContentCache` and `ParsedMarkdownCache`).
 *
 * Best effort: a missing or unreadable note is silently skipped (`load` returns a `Result`), never
 * surfaced here — the caller's own load path is what reports real failures to the user.
 */
class WarmNote(
    private val contentCache: NoteContentCachePort,
    private val parsedMarkdownCache: ParsedMarkdownCachePort
) {
    suspend operator fun invoke(config: WorkspaceConfig, filesDir: File, noteId: NoteId) {
        val content = contentCache.load(config, filesDir, noteId).getOrNull() ?: return
        parsedMarkdownCache.warm(VaultMarkdownPreprocess.stripTitleHeading(content.markdown))
    }
}
