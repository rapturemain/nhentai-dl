package org.lifeutils.nhentaidl.scraper

import org.lifeutils.nhentaidl.dto.HentaiId

data class SearchResult(
    val totalCount: Int,
    val ids: List<HentaiId>,
    val failedPages: List<String>
)