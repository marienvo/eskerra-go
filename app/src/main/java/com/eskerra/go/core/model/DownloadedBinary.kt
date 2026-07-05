package com.eskerra.go.core.model

/** A binary currently present on the device, for the "Downloaded binaries" tile. */
data class DownloadedBinary(val relPath: String, val sizeBytes: Long)
