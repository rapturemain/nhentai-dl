package org.lifeutils.nhentaidl.model.oldmetadata

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.ZonedDateTime

data class RicterZNhentaiMetadata(
    val title: String,
    val subtitle: String,
    @field:JsonProperty("upload_date")
    val uploadDate: ZonedDateTime,
    val parody: List<String>,
    val character: List<String>,
    val tag: List<String>,
    val artist: List<String>,
    val group: List<String>,
    val language: List<String>,
    val category: String,
    @field:JsonProperty("URL")
    val url: String,
    @field:JsonProperty("Pages")
    val pages: Int,
)
