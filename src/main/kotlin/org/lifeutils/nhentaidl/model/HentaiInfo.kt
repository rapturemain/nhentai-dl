package org.lifeutils.nhentaidl.model

import java.time.ZonedDateTime

data class HentaiInfo(
    val id: HentaiId,
    val title: String,
    val subTitle: String,
    val pageCount: Int,
    val metadata: HentaiMetadata,
    val metadataVersion: Int = 2,
)

data class HentaiMetadata(
    val parodies: List<String>,
    val characters: List<String>,
    val tags: List<String>,
    val artists: List<String>,
    val language: List<Language>,
    val groups: List<String>,
    val categories: List<String>,
    val uploadedAt: ZonedDateTime,
)
