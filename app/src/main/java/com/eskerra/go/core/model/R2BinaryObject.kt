package com.eskerra.go.core.model

/** A single object listed under the R2 `binaries/` prefix. */
data class R2BinaryObject(val key: String, val size: Long, val etag: String)
