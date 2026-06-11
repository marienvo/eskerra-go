package com.eskerra.go.core.vault

import com.eskerra.go.core.model.EskerraSettings
import com.eskerra.go.core.model.R2Config
import com.eskerra.go.core.model.R2Jurisdiction
import com.eskerra.go.core.model.VaultSettingsError
import com.eskerra.go.core.model.VaultSettingsException

private const val PARTIAL_R2_ERROR =
    "Complete all Cloudflare R2 fields or clear them all."

/**
 * Pure form validation mirroring `buildEskerraSettingsFromForm` from
 * `packages/eskerra-core`. Always pass [previousShared] to avoid stripping
 * desktop-only keys (themePreference, frontmatterProperties, linkSnippetBlockedDomains).
 */
fun buildEskerraSettingsFromForm(
    r2Endpoint: String,
    r2Jurisdiction: R2Jurisdiction,
    r2Bucket: String,
    r2AccessKeyId: String,
    r2SecretAccessKey: String,
    previousShared: EskerraSettings? = null
): Result<EskerraSettings> {
    val endpoint = r2Endpoint.trim()
    val bucket = r2Bucket.trim()
    val accessKeyId = r2AccessKeyId.trim()
    val secretAccessKey = r2SecretAccessKey.trim()

    val fields = listOf(endpoint, bucket, accessKeyId, secretAccessKey)
    val r2Config = when {
        fields.all { it.isEmpty() } -> null
        fields.all { it.isNotEmpty() } ->
            R2Config(
                endpoint = endpoint,
                bucket = bucket,
                accessKeyId = accessKeyId,
                secretAccessKey = secretAccessKey,
                jurisdiction = r2Jurisdiction
            )
        else ->
            return Result.failure(
                VaultSettingsException(VaultSettingsError.ValidationError(PARTIAL_R2_ERROR))
            )
    }

    val extras = previousShared?.extras ?: emptyMap()
    return Result.success(EskerraSettings(r2 = r2Config, extras = extras))
}
