package com.eskerra.go.core.share

/**
 * Platform-neutral view of an inbound share. Built at the Android boundary
 * (`SharedIntentReader`) so nothing below it ever sees an `Intent`.
 */
data class SharedContent(val text: String, val subject: String? = null)

/**
 * A share waiting to be turned into a note draft. [id] is a monotonic per-process counter:
 * it makes every downstream apply idempotent, so a rotation or recomposition that re-offers
 * the same share cannot prefill twice.
 */
data class PendingShare(val id: Long, val content: SharedContent)
