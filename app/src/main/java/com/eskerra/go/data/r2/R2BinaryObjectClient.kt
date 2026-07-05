package com.eskerra.go.data.r2

import com.eskerra.go.core.model.R2BinaryObject
import com.eskerra.go.core.model.R2Config
import java.io.File
import java.io.IOException
import java.time.Instant
import okhttp3.OkHttpClient

/**
 * Lists and downloads objects under the R2 `binaries/` prefix. All requests are
 * SigV4 presigned-query (no `Authorization` header), reusing [buildSignedListRequest]
 * and [buildSignedObjectRequest].
 */
class R2BinaryObjectClient(
    private val httpClient: OkHttpClient,
    private val clock: () -> Instant = Instant::now
) {

    /** Lists every object under [BINARIES_PREFIX], following `ListObjectsV2` pagination. */
    fun list(config: R2Config): List<R2BinaryObject> {
        val collected = mutableListOf<R2BinaryObject>()
        var continuationToken: String? = null
        do {
            val request =
                buildSignedListRequest(config, clock(), BINARIES_PREFIX, continuationToken)
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw R2PlaylistException(
                        R2ErrorFormatter.format(R2Verb.READ, response.code, body, "list binaries/")
                    )
                }
                val page = R2ListObjectsParser.parse(body)
                collected += page.objects
                continuationToken =
                    if (page.isTruncated) page.nextContinuationToken else null
            }
        } while (continuationToken != null)
        return collected
    }

    /** Downloads object [key] to [dest] via a temp file, then atomically renames it. */
    fun download(config: R2Config, key: String, dest: File) {
        val request = buildSignedObjectRequest(config, "GET", key, clock())
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val text = response.body?.string().orEmpty()
                throw R2PlaylistException(
                    R2ErrorFormatter.format(R2Verb.READ, response.code, text, key)
                )
            }
            val source = response.body?.byteStream()
                ?: throw R2PlaylistException("R2 GET $key failed: empty response body")
            val parent = dest.parentFile
            if (parent != null && !parent.isDirectory && !parent.mkdirs()) {
                throw IOException("Failed to create directory ${parent.path}")
            }
            val temp = File.createTempFile("r2bin", ".part", parent)
            try {
                temp.outputStream().use { output -> source.copyTo(output) }
                if (dest.exists() && !dest.delete()) {
                    throw IOException("Failed to replace ${dest.path}")
                }
                if (!temp.renameTo(dest)) {
                    throw IOException("Failed to move download into ${dest.path}")
                }
            } finally {
                if (temp.exists()) temp.delete()
            }
        }
    }
}
