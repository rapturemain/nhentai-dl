package org.lifeutils.nhentaidl.migration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.lifeutils.nhentaidl.model.HentaiId
import org.lifeutils.nhentaidl.model.oldmetadata.RicterZNhentaiMetadata
import org.lifeutils.nhentaidl.scraper.HentaiScraper
import org.lifeutils.nhentaidl.writer.HentaiWriter
import org.lifeutils.nhentaidl.writer.METADATA_FILE_NAME
import java.io.File

fun createMigrationServiceRicterZ(
    objectMapper: ObjectMapper,
    hentaiScraper: HentaiScraper<*>,
    hentaiWriter: HentaiWriter<*>,
): MigrationService {
    return MigrationServiceOld(
        hentaiScraper = hentaiScraper,
        hentaiWriter = hentaiWriter,
        extensions = MigrationExtensionsRicterZ(objectMapper),
    )
}

class MigrationExtensionsRicterZ(
    private val objectMapper: ObjectMapper,
) : MigrationExtensions {
    companion object {
        private val IMAGE_NAME_REGEX = Regex("\\d+\\.\\w+")
        private val ZEROS_PADDING_REGEX = Regex("^0+")
    }

    override fun isFileAllowed(name: String): Boolean {
        return name.matches(IMAGE_NAME_REGEX)
            || name == METADATA_FILE_NAME
            || name == "index.html"
    }

    override fun canMigrate(metadataJsonTreeRoot: JsonNode): Boolean {
        return hasRicterZMetadata(metadataJsonTreeRoot)
    }

    override fun getHentaiIdFromMeta(metadataContents: ByteArray): Result<HentaiId> {
        return runCatching {
            val metadataJsonTreeRoot = objectMapper.readTree(metadataContents)
            val hentaiId = metadataJsonTreeRoot["URL"]!!
                .textValue()
                .substringAfterLast('/')
                .toInt()
            return Result.success(HentaiId(hentaiId))
        }
    }

    override fun filterZipImages(zipFile: List<ZipFile>): List<ZipFile> {
        return zipFile.filter { it.zipEntry.name.matches(IMAGE_NAME_REGEX) }
    }

    override fun filterFileImages(files: List<File>): List<File> {
        return files.filter { it.name.matches(IMAGE_NAME_REGEX) }
    }

    override fun renameImage(name: String): String {
        return name.replace(ZEROS_PADDING_REGEX, "")
    }

    private fun hasRicterZMetadata(metadataJsonTreeRoot: JsonNode): Boolean {
        val parsedMeta = try {
            objectMapper.treeToValue(metadataJsonTreeRoot, RicterZNhentaiMetadata::class.java)
        } catch (_: Exception) {
            null
        }

        return parsedMeta != null
    }
}