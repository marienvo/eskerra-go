package com.eskerra.go.core.repository

import com.eskerra.go.core.model.PlaylistEntry
import com.eskerra.go.core.model.R2Config

/**
 * Blocking GET / PUT / DELETE for the R2 `playlist.json` object. The data-layer
 * implementation ([com.eskerra.go.data.r2.R2PlaylistObjectClient]) performs the
 * actual signed HTTP; callers wrap these on an IO dispatcher.
 */
interface R2PlaylistClient {
    /** 404 / empty → `null`; invalid shape → throws. */
    fun get(config: R2Config): PlaylistEntry?

    fun put(config: R2Config, entry: PlaylistEntry)

    /** 404 is treated as success. */
    fun delete(config: R2Config)
}
