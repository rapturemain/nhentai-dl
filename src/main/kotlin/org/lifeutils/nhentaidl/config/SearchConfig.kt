package org.lifeutils.nhentaidl.config

import org.lifeutils.nhentaidl.model.Language

data class SearchConfig(
    val searchLanguage: Language,
) : HentaiIdProviderConfig
