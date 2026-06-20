package com.eskerra.go.ui.markdown

import androidx.compose.runtime.staticCompositionLocalOf
import com.eskerra.go.data.notes.ParsedMarkdownCache

/** Shared default so markdown surfaces benefit from the same warm cache without explicit wiring. */
private val DefaultParsedMarkdownCache = ParsedMarkdownCache()

/**
 * The [ParsedMarkdownCache] backing the shared markdown renderers. Defaults to a process-wide
 * instance; a host (e.g. `MainActivity`) may provide the app-scoped cache so prefetch warms the same
 * store the reader reads from.
 */
val LocalParsedMarkdownCache = staticCompositionLocalOf { DefaultParsedMarkdownCache }
