package com.eskerra.go.feature.sync

import java.util.Locale
import kotlin.math.abs

/** Human-readable byte size, e.g. `0 B`, `912 B`, `12.4 MB`, `1.1 GB` (base-1024). */
fun formatByteSize(bytes: Long): String {
    if (abs(bytes) < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB", "PB")
    var value = bytes.toDouble() / 1024
    var unitIndex = 0
    while (abs(value) >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unitIndex])
}
