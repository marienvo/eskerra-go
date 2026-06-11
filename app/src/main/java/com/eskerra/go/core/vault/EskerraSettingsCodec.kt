package com.eskerra.go.core.vault

import com.eskerra.go.core.model.EskerraSettings
import com.eskerra.go.core.model.R2Config
import com.eskerra.go.core.model.R2Jurisdiction
import com.eskerra.go.core.model.VaultSettingsError
import com.eskerra.go.core.model.VaultSettingsException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Pure parse/serialize for `EskerraSettings` with exact byte contract:
 * 2-space pretty print + trailing newline, unknown-key preservation.
 */
@OptIn(ExperimentalSerializationApi::class)
object EskerraSettingsCodec {

    private val prettyJson = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    private val PARSE_ERROR_MSG = "settings-shared.json has an invalid structure."
    private val KNOWN_KEYS = setOf("r2", "displayName")

    fun parse(raw: String): Result<EskerraSettings> {
        val obj =
            runCatching { Json.parseToJsonElement(raw).jsonObject }
                .getOrElse {
                    return Result.failure(
                        VaultSettingsException(VaultSettingsError.ParseError(PARSE_ERROR_MSG))
                    )
                }

        val r2Config =
            obj["r2"]?.let { r2El ->
                if (r2El is JsonNull) return@let null
                val r2Obj =
                    runCatching { r2El.jsonObject }.getOrElse {
                        return Result.failure(
                            VaultSettingsException(VaultSettingsError.ParseError(PARSE_ERROR_MSG))
                        )
                    }
                R2Config(
                    endpoint = r2Obj["endpoint"]?.jsonPrimitive?.content.orEmpty(),
                    bucket = r2Obj["bucket"]?.jsonPrimitive?.content.orEmpty(),
                    accessKeyId = r2Obj["accessKeyId"]?.jsonPrimitive?.content.orEmpty(),
                    secretAccessKey = r2Obj["secretAccessKey"]?.jsonPrimitive?.content.orEmpty(),
                    jurisdiction = parseJurisdiction(r2Obj["jurisdiction"]?.jsonPrimitive?.content)
                )
            }

        val extras = obj.entries
            .filterNot { it.key in KNOWN_KEYS }
            .associate { it.key to it.value }

        return Result.success(EskerraSettings(r2 = r2Config, extras = extras))
    }

    fun serialize(settings: EskerraSettings): String {
        val map = mutableMapOf<String, JsonElement>()
        settings.r2?.let { r2 ->
            val r2Map = mutableMapOf<String, JsonElement>(
                "endpoint" to JsonPrimitive(r2.endpoint),
                "bucket" to JsonPrimitive(r2.bucket),
                "accessKeyId" to JsonPrimitive(r2.accessKeyId),
                "secretAccessKey" to JsonPrimitive(r2.secretAccessKey)
            )
            if (r2.jurisdiction != R2Jurisdiction.Default) {
                r2Map["jurisdiction"] = JsonPrimitive(r2.jurisdiction.toSerialName())
            }
            map["r2"] = JsonObject(r2Map)
        }
        map.putAll(settings.extras)
        return prettyJson.encodeToString(JsonObject.serializer(), JsonObject(map)) + "\n"
    }

    private fun parseJurisdiction(raw: String?): R2Jurisdiction = when (raw?.lowercase()) {
        "eu" -> R2Jurisdiction.Eu
        "fedramp" -> R2Jurisdiction.Fedramp
        else -> R2Jurisdiction.Default
    }
}

private fun R2Jurisdiction.toSerialName(): String = when (this) {
    R2Jurisdiction.Default -> "default"
    R2Jurisdiction.Eu -> "eu"
    R2Jurisdiction.Fedramp -> "fedramp"
}
