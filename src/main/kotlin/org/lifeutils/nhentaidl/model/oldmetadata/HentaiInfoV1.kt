package org.lifeutils.nhentaidl.model.oldmetadata

import org.lifeutils.nhentaidl.model.HentaiId

data class HentaiInfoV1(
    val id: HentaiId,
    val title: String,
    val pageCount: Int,
    val metadata: HentaiMetadataV3,
)