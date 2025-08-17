package org.lifeutils.nhentaidl.config

import org.lifeutils.nhentaidl.model.oldmetadata.LanguageV3

data class SearchConfig(
    val searchLanguage: LanguageV3?,
    val searchArtist: String?,
    val countLimit: Int,
) : HentaiIdProviderConfig {
    fun verify() {
        if (countLimit <= 0) {
            throw IllegalArgumentException("Count limit must be greater than 0")
        }
        if (searchArtist == null && searchLanguage == null) {
            throw IllegalArgumentException("Search language or search artist must be specified")
        }
        if (searchArtist?.contains(':') == true) {
            throw IllegalArgumentException("Search artist must not contains ':'")
        }
    }
}
