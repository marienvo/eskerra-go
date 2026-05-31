package com.eskerra.go.core.model

/** Stable hash of local workspace identity for optimistic app-start gate resolution. */
@JvmInline
value class GateFingerprint(val value: String)
