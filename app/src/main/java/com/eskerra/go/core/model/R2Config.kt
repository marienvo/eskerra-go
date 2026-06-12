package com.eskerra.go.core.model

data class R2Config(
    val endpoint: String,
    val bucket: String,
    val accessKeyId: String,
    val secretAccessKey: String,
    val jurisdiction: R2Jurisdiction = R2Jurisdiction.Default
)
