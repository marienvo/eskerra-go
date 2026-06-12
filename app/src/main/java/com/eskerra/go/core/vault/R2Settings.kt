package com.eskerra.go.core.vault

import com.eskerra.go.core.model.EskerraSettings
import com.eskerra.go.core.model.R2Config
import com.eskerra.go.core.model.R2Jurisdiction

/** Pure helpers mirroring `packages/eskerra-core/src/r2Settings.ts`. */
object R2Settings {

    /**
     * All four R2 fields non-empty after trim — jurisdiction is irrelevant for this gate.
     */
    fun isVaultR2PlaylistConfigured(settings: EskerraSettings): Boolean {
        val r2 = settings.r2 ?: return false
        return r2.endpoint.trim().isNotEmpty() &&
            r2.bucket.trim().isNotEmpty() &&
            r2.accessKeyId.trim().isNotEmpty() &&
            r2.secretAccessKey.trim().isNotEmpty()
    }

    /**
     * For EU/FedRAMP jurisdiction, rewrites the bare account hostname to include the
     * jurisdiction subdomain. Already-correct hostnames pass through unchanged.
     *
     * Example: `https://abc.r2.cloudflarestorage.com` + EU →
     *          `https://abc.eu.r2.cloudflarestorage.com`
     */
    fun effectiveR2Endpoint(config: R2Config): String {
        val endpoint = config.endpoint.trim().trimEnd('/')
        return when (config.jurisdiction) {
            R2Jurisdiction.Default -> endpoint
            R2Jurisdiction.Eu -> rewriteJurisdiction(endpoint, "eu")
            R2Jurisdiction.Fedramp -> rewriteJurisdiction(endpoint, "fedramp")
        }
    }

    /**
     * Base origin for R2 S3 object URLs. Strips a trailing `/<bucket>` segment
     * when the user pasted Cloudflare's "S3 API URL including bucket" form.
     * Object URLs are then built as `{accountBase}/{bucket}/{key}`.
     */
    fun r2S3AccountBaseUrl(config: R2Config): String {
        val base = effectiveR2Endpoint(config).trimEnd('/')
        val bucket = config.bucket.trim()
        return if (bucket.isNotEmpty() && base.endsWith("/$bucket")) {
            base.dropLast(bucket.length + 1)
        } else {
            base
        }
    }

    private val jurisdictionPattern =
        Regex("""^(https?://)([^./]+)(\.r2\.cloudflarestorage\.com)$""", RegexOption.IGNORE_CASE)

    private fun rewriteJurisdiction(endpoint: String, subdomain: String): String {
        val match = jurisdictionPattern.find(endpoint) ?: return endpoint
        val (scheme, account, suffix) = match.destructured
        return if (account.contains('.')) endpoint else "$scheme$account.$subdomain$suffix"
    }
}
