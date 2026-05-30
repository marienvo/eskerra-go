package com.eskerra.go.app

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Pure Kotlin codec for note route arguments. Encodes slash-containing note ids into
 * a single safe route segment and decodes them back deterministically.
 */
object NoteRouteCodec {

    private const val UTF_8 = "UTF-8"

    fun encode(noteIdValue: String): String =
        URLEncoder.encode(noteIdValue, UTF_8).replace("+", "%20")

    fun decode(encoded: String): String = URLDecoder.decode(encoded, UTF_8)
}
