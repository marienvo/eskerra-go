package com.eskerra.go.data.r2

/** Verb category for R2 error messages (HTTP verb label + AccessDenied grant hint). */
enum class R2Verb(val label: String, private val grant: String) {
    READ(
        "GET",
        "Grant Object Read on the R2 S3 API token for this bucket " +
            "(Cloudflare: R2 → Manage R2 API Tokens)."
    ),
    WRITE("PUT", "Grant Object Write on the R2 S3 API token for this bucket."),
    DELETE("DELETE", "Grant Object Delete on the R2 S3 API token for this bucket.");

    /** Full AccessDenied hint for this verb, including the EU jurisdiction note. */
    fun accessDeniedHint(): String = "$grant$EU_NOTE"
}

private const val EU_NOTE =
    " EU data location buckets need jurisdiction \"EU\" in settings " +
        "(or the .eu.r2.cloudflarestorage.com endpoint)."

/**
 * Builds the non-OK error message, mirroring the spec §"S3 request contract":
 *
 * ```
 * R2 <VERB> playlist.json failed: HTTP <status>[ (<Code>)][. <hint>]
 * ```
 *
 * `<Code>` is scanned from the S3 XML `<Code>...</Code>`; the `<hint>` is appended
 * only when the code is `AccessDenied`.
 */
object R2ErrorFormatter {

    private val codePattern = Regex("<Code>(.*?)</Code>", RegexOption.DOT_MATCHES_ALL)

    fun format(verb: R2Verb, status: Int, body: String, subject: String = "playlist.json"): String {
        val code = extractCode(body)
        val base = buildString {
            append("R2 ").append(verb.label).append(' ').append(subject)
                .append(" failed: HTTP ").append(status)
            if (code != null) append(" (").append(code).append(')')
        }
        return if (code == "AccessDenied") "$base. ${verb.accessDeniedHint()}" else base
    }

    private fun extractCode(body: String): String? =
        codePattern.find(body)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
}
