package org.lifeutils.nhentaidl.config

import org.lifeutils.nhentaidl.model.HentaiId
import java.io.File

class FileHentaiIdProvider : HentaiIdProvider<FileHentaiIdProviderConfig> {
    override suspend fun provideIdsToDownload(config: FileHentaiIdProviderConfig): Result<List<HentaiId>> {
        val ids = config.file
            .readLines()
            .mapNotNull { it.toIntOrNull() }
            .map { HentaiId(it) }
        return Result.success(ids)
    }
}

data class FileHentaiIdProviderConfig(
    val file: File,
) : HentaiIdProviderConfig {
    fun verify() {
        if (!file.exists()) {
            throw IllegalArgumentException("File ${file.absolutePath} does not exist")
        }

        if (!file.isFile) {
            throw IllegalArgumentException("File ${file.absolutePath} is not a file")
        }
    }
}
