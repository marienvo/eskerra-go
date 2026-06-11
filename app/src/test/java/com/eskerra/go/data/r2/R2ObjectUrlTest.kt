package com.eskerra.go.data.r2

import com.eskerra.go.core.model.R2Config
import com.eskerra.go.core.model.R2Jurisdiction
import org.junit.Assert.assertEquals
import org.junit.Test

class R2ObjectUrlTest {

    @Test
    fun `builds account-base bucket key url`() {
        val config = R2Config(
            endpoint = "https://abc.r2.cloudflarestorage.com",
            bucket = "my-bucket",
            accessKeyId = "k",
            secretAccessKey = "s"
        )
        assertEquals(
            "https://abc.r2.cloudflarestorage.com/my-bucket/playlist.json",
            R2ObjectUrl.buildR2ObjectUrl(config, "playlist.json")
        )
    }

    @Test
    fun `eu jurisdiction rewrites the host`() {
        val config = R2Config(
            endpoint = "https://abc.r2.cloudflarestorage.com",
            bucket = "my-bucket",
            accessKeyId = "k",
            secretAccessKey = "s",
            jurisdiction = R2Jurisdiction.Eu
        )
        assertEquals(
            "https://abc.eu.r2.cloudflarestorage.com/my-bucket/playlist.json",
            R2ObjectUrl.buildR2ObjectUrl(config, "playlist.json")
        )
    }

    @Test
    fun `strips trailing bucket segment from pasted s3 api url`() {
        val config = R2Config(
            endpoint = "https://abc.r2.cloudflarestorage.com/my-bucket",
            bucket = "my-bucket",
            accessKeyId = "k",
            secretAccessKey = "s"
        )
        assertEquals(
            "https://abc.r2.cloudflarestorage.com/my-bucket/playlist.json",
            R2ObjectUrl.buildR2ObjectUrl(config, "playlist.json")
        )
    }

    @Test
    fun `encodes object key but keeps slashes`() {
        val config = R2Config(
            endpoint = "https://abc.r2.cloudflarestorage.com",
            bucket = "b",
            accessKeyId = "k",
            secretAccessKey = "s"
        )
        assertEquals(
            "https://abc.r2.cloudflarestorage.com/b/nested/a%20b.json",
            R2ObjectUrl.buildR2ObjectUrl(config, "nested/a b.json")
        )
    }
}
