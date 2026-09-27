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
 *
 * @param onWarmed optional telemetry hook (core stays free of a concrete logger dependency per the
 *   layering ADR; the composition root wires this to `NoteNavTrace`). Reports how long the content
 *   load and the parse-warm step each took, and whether the note actually loaded. A near-zero
 *   [contentMs]/[parseMs] indicates a cache hit; a real load/parse takes measurably longer.
 */
class WarmNote(
    private val contentCache: NoteContentCachePort,
    private val parsedMarkdownCache: ParsedMarkdownCachePort,
    private val onWarmed: (
        noteId: NoteId,
        contentMs: Long,
        parseMs: Long,
        loaded: Boolean
    ) -> Unit =
        { _, _, _, _ -> }
) {
    suspend operator fun invoke(config: WorkspaceConfig, filesDir: File, noteId: NoteId) {
        val contentStartMs = System.currentTimeMillis()
        val content = contentCache.load(config, filesDir, noteId).getOrNull()
        val contentMs = System.currentTimeMillis() - contentStartMs
        if (content == null) {
            onWarmed(noteId, contentMs, 0L, false)
            return
        }
        val parseStartMs = System.currentTimeMillis()
        parsedMarkdownCache.warm(VaultMarkdownPreprocess.stripTitleHeading(content.markdown))
        val parseMs = System.currentTimeMillis() - parseStartMs
        onWarmed(noteId, contentMs, parseMs, true)
    }
}
