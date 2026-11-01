package com.eskerra.go.data.r2

import com.eskerra.go.core.model.R2Config
import com.eskerra.go.core.repository.DesktopActivityRepository
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class R2DesktopActivityRepository(private val objectClient: R2BinaryObjectClient) :
    DesktopActivityRepository {
    override suspend fun latestActivityAtEpochMillis(config: R2Config): Long? =
        withContext(Dispatchers.IO) {
            objectClient.list(config, PRESENCE_PREFIX).mapNotNull { presence ->
                if (!presence.key.endsWith(".json")) return@mapNotNull null
                runCatching {
                    val json = Json.parseToJsonElement(
                        objectClient.readText(config, presence.key)
                    ).jsonObject
                    if (json.getValue("version").jsonPrimitive.int != 1) return@runCatching null
                    Instant.parse(
                        json.getValue("lastActivityAt").jsonPrimitive.content
                    ).toEpochMilli()
                }.getOrNull()
            }.maxOrNull()
        }

    private companion object {
        const val PRESENCE_PREFIX = "presence/desktop/"
    }
}
