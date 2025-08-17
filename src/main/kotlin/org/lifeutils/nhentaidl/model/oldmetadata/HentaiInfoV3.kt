package org.lifeutils.nhentaidl.model.oldmetadata

import org.lifeutils.nhentaidl.model.HentaiId
import java.time.ZonedDateTime

/**
 * Metadata V3, V2
 */
data class HentaiInfoV3(
    val id: HentaiId,
    val title: String,
    val subTitle: String,
    val pageCount: Int,
    val metadata: HentaiMetadataV3,
    val metadataVersion: Int = 3,
)

/**
 * Metadata V3, V2, V1
 */
data class HentaiMetadataV3(
    val parodies: List<String>,
    val characters: List<String>,
    val tags: List<String>,
    val artists: List<String>,
    val language: List<LanguageV3>,
    val groups: List<String>,
    val categories: List<String>,
    val uploadedAt: ZonedDateTime,
)
