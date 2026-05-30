package com.eskerra.go.core.model

import java.io.File

/**
 * Normalized workspace-relative path for a note. Uses forward slashes and never
 * represents an absolute filesystem location.
 */
@JvmInline
value class NotePath(val value: String) {

    companion object {
        fun fromRelativePath(raw: String): Result<NotePath> {
            if (raw.isBlank()) {
                return Result.failure(IllegalArgumentException("Note path must not be blank"))
            }
            if (File(raw).isAbsolute) {
                return Result.failure(IllegalArgumentException("Note path must be relative"))
            }
            val normalized = raw.replace('\\', '/').trimStart('/')
            if (normalized.isBlank()) {
                return Result.failure(IllegalArgumentException("Note path must not be blank"))
            }
            if (normalized.split('/').any { it == ".." }) {
                return Result.failure(
                    IllegalArgumentException("Note path must not contain '..' segments")
                )
            }
            return Result.success(NotePath(normalized))
        }
    }
}
