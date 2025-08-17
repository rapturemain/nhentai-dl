package org.lifeutils.nhentaidl.migration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.lifeutils.nhentaidl.model.HentaiId
import org.lifeutils.nhentaidl.model.oldmetadata.HentaiInfoV1
import org.lifeutils.nhentaidl.model.oldmetadata.HentaiInfoV3
import org.lifeutils.nhentaidl.scraper.HentaiScraper
import org.lifeutils.nhentaidl.writer.HentaiWriter
import org.lifeutils.nhentaidl.writer.METADATA_FILE_NAME
import java.io.File

fun createMigrationServiceV3V2V1(
    objectMapper: ObjectMapper,
    hentaiScraper: HentaiScraper<*>,
    hentaiWriter: HentaiWriter<*>,
): MigrationService {
    return MigrationServiceOld(
        hentaiScraper = hentaiScraper,
        hentaiWriter = hentaiWriter,
        extensions = MigrationExtensionsV3V2V1(objectMapper),
    )
}

class MigrationExtensionsV3V2V1(
    private val objectMapper: ObjectMapper,
) : MigrationExtensions {
    companion object {
        private val IMAGE_NAME_REGEX = Regex("\\d+\\.\\w+")
    }

    override fun isFileAllowed(name: String): Boolean {
        return name.matches(IMAGE_NAME_REGEX)
            || name == METADATA_FILE_NAME
    }

    override fun canMigrate(metadataJsonTreeRoot: JsonNode): Boolean {
        return hasV3V2Metadata(metadataJsonTreeRoot)
            || hasV1Metadata(metadataJsonTreeRoot)
    }

    override fun getHentaiIdFromMeta(metadataContents: ByteArray): Result<HentaiId> {
        val metadataJsonTreeRoot = objectMapper.readTree(metadataContents)
        val hentaiId = metadataJsonTreeRoot["id"]!!.intValue()
        return Result.success(HentaiId(hentaiId))
    }

    override fun filterZipImages(zipFile: List<ZipFile>): List<ZipFile> {
        return zipFile.filter { it.zipEntry.name.matches(IMAGE_NAME_REGEX) }
    }

    override fun filterFileImages(files: List<File>): List<File> {
        return files.filter { it.name.matches(IMAGE_NAME_REGEX) }
    }

    override fun renameImage(name: String): String {
        return name
    }

    private fun hasV3V2Metadata(metadataJsonTreeRoot: JsonNode): Boolean {
        if (metadataJsonTreeRoot["metadataVersion"]?.intValue() !in 2..3) {
            return false
        }

        val parsedMeta = try {
            objectMapper.treeToValue(metadataJsonTreeRoot, HentaiInfoV3::class.java)
        } catch (_: Exception) {
            null
        }

        return parsedMeta != null
    }

    private fun hasV1Metadata(metadataJsonTreeRoot: JsonNode): Boolean {
        val parsedMeta = try {
            objectMapper.treeToValue(metadataJsonTreeRoot, HentaiInfoV1::class.java)
        } catch (_: Exception) {
            null
        }

        return parsedMeta != null
    }
}