package com.eskerra.go.data.r2

/** Raised for non-OK R2 responses and invalid playlist payloads. */
class R2PlaylistException(message: String) : Exception(message)
