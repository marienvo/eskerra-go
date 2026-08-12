package com.eskerra.go.core.model

/**
 * One cold start, broken into the phases that make up launch-settled.
 *
 * The phases partition the launch: [processToActivityMs] starts at process start and
 * [totalMs] ends when the splash is dismissed. Durations only — no vault content ever enters
 * this model, so a report built from it carries no note titles, paths, or remote URIs.
 */
data class ColdStartSample(
    /** Process start until `MainActivity.onCreate` — class loading, before any of our code. */
    val processToActivityMs: Long,
    /** Manual dependency construction on the main thread. */
    val diBuildMs: Long,
    /** First composition until the gate starts resolving. */
    val toGateStartMs: Long,
    /** Gate resolution, including the workspace fingerprint check. */
    val gateResolveMs: Long,
    /** Reading and decoding the persisted note-registry snapshot. */
    val snapshotReadMs: Long,
    /** The first vault scan, the one that blocks the splash. */
    val firstScanMs: Long,
    /** From the end of that scan until the splash is actually dismissed. */
    val settleTailMs: Long,
    /** Process start until launch-settled. */
    val totalMs: Long,
    val noteCount: Int,
    /** False means the gate fell back to the full local resolve. */
    val fingerprintHit: Boolean,
    /** False means the registry snapshot was missing or rejected. */
    val snapshotHit: Boolean,
    /** False means the scan re-read every note instead of reusing the memo. */
    val hadMemoBase: Boolean
)
