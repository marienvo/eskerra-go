package com.eskerra.go.feature.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart

/**
 * Holds each `true` value for at least [minMs] before it is allowed to flip back to `false`, so a
 * sync that finishes almost instantly does not flash the shell's sync spinner on and off. A `false`
 * passes through immediately — only the transition away from `true` is delayed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun Flow<Boolean>.holdTrueAtLeast(minMs: Long): Flow<Boolean> = flatMapLatest { visible ->
    if (visible) {
        flowOf(true)
    } else {
        flowOf(false).onStart { delay(minMs) }
    }
}.distinctUntilChanged()
