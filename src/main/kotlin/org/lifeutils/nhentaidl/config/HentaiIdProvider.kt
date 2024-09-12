package org.lifeutils.nhentaidl.config

import org.lifeutils.nhentaidl.model.HentaiId

interface HentaiIdProvider<T : HentaiIdProviderConfig> {
    suspend fun provideIdsToDownload(config: T): Result<List<HentaiId>>
}

interface HentaiIdProviderConfig