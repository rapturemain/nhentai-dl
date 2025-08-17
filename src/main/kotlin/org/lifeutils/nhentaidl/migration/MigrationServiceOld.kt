package org.lifeutils.nhentaidl.migration

import com.fasterxml.jackson.databind.JsonNode
import org.lifeutils.nhentaidl.model.HentaiId
import org.lifeutils.nhentaidl.model.HentaiInfo
import org.lifeutils.nhentaidl.scraper.HentaiScraper
import org.lifeutils.nhentaidl.writer.HentaiWriter
import org.lifeutils.nhentaidl.writer.HentaiWriterMeta
import org.lifeutils.nhentaidl.writer.METADATA_FILE_NAME
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

interface MigrationExtensions {
    fun isFileAllowed(name: String): Boolean

    fun canMigrate(metadataJsonTreeRoot: JsonNode): Boolean

    fun getHentaiIdFromMeta(metadataContents: ByteArray): Result<HentaiId>

    fun filterZipImages(zipFile: List<ZipFile>): List<ZipFile>

    fun filterFileImages(files: List<File>): List<File>

    fun renameImage(name: String): String
}

data class ZipFile(
    val zipEntry: ZipEntry,
    val contents: ByteArray,
)

/**
 * Migration service for V3, V2, V1 and RicterZ nhentai metadata versions.
 */
class MigrationServiceOld<T : HentaiWriterMeta>(
    private val hentaiWriter: HentaiWriter<T>,
    private val hentaiScraper: HentaiScraper<*>,
    private val extensions: MigrationExtensions
) : MigrationService {
    override suspend fun migrate(file: File, outputFile: File): Result<Unit> {
        return if (file.name.endsWith(".zip")) {
            migrateZip(file, outputFile)
        } else {
            migrateDir(file, outputFile)
        }
    }

    override suspend fun canMigrate(metadataJsonTreeRoot: JsonNode): Boolean {
        return extensions.canMigrate(metadataJsonTreeRoot)
    }

    private suspend fun migrateZip(zipFile: File, outputFile: File): Result<Unit> {
        val files = mutableListOf<ZipFile>()

        // read zip file
        zipFile.inputStream().use {
            ZipInputStream(it).use { zis ->
                for (entry in generateSequence { zis.nextEntry }) {
                    if (!extensions.isFileAllowed(entry.name)) {
                        return Result.failure(IllegalArgumentException("File ${entry.name} is not allowed in ZIP"))
                    }

                    val contents = zis.readBytes()

                    val crcValid = contents.verifyCrc32(entry.crc)
                    if (!crcValid) {
                        return Result.failure(IllegalArgumentException("CRC32 verification failed for ${entry.name}"))
                    }

                    files.add(ZipFile(entry, contents))

                    zis.closeEntry()
                }
            }
        }

        // get old meta
        val oldMetaContents = files.firstOrNull { it.zipEntry.name == METADATA_FILE_NAME }?.contents
            ?: return Result.failure(IllegalArgumentException("Metadata file not found in ZIP"))

        val hentaiId = extensions.getHentaiIdFromMeta(oldMetaContents)
            .getOrElse {
                return Result.failure(it)
            }

        // fetch new meta
        val hentaiInfo = hentaiScraper.getHentaiInfo(hentaiId)
            .getOrElse {
                return Result.failure(it)
            }

        // verify contents
        val imageFiles = extensions.filterZipImages(files)

        val expectedPageNames = hentaiInfo.pages.map { it.fileName }.toSet()
        val foundPages = imageFiles.filter { extensions.renameImage(it.zipEntry.name) in expectedPageNames }
        if (foundPages.size != hentaiInfo.pages.size) {
            return Result.failure(IllegalArgumentException("ZIP does not contain all images"))
        }

        return write(
            hentaiInfo = hentaiInfo.hentaiInfo,
            imageContents = foundPages.asSequence().map { InMemoryFile(extensions.renameImage(it.zipEntry.name), it.contents) },
            outputFile = outputFile
        )
    }

    private suspend fun migrateDir(dir: File, outputFile: File): Result<Unit> {
        // read files
        val files = dir.listFiles()!!
            .onEach {
                if (it.isDirectory) {
                    return Result.failure(IllegalArgumentException("Directory in directory"))
                }
                if (!extensions.isFileAllowed(it.name)) {
                    return Result.failure(IllegalArgumentException("File ${it.name} is not allowed in directory"))
                }
            }
            .toList()

        // get old meta
        val oldMetaContents = runCatching {
            files.firstOrNull { it.name == METADATA_FILE_NAME }?.readBytes()
                ?: return Result.failure(IllegalArgumentException("Metadata file not found in directory"))
        }
            .getOrElse {
                return Result.failure(it)
            }

        val hentaiId = extensions.getHentaiIdFromMeta(oldMetaContents)
            .getOrElse {
                return Result.failure(it)
            }

        // fetch new meta
        val hentaiInfo = hentaiScraper.getHentaiInfo(hentaiId)
            .getOrElse {
                return Result.failure(it)
            }

        // verify contents
        val imageFiles = extensions.filterFileImages(files)

        val expectedPageNames = hentaiInfo.pages.map { it.fileName }.toSet()
        val foundPages = imageFiles.filter { extensions.renameImage(it.name) in expectedPageNames }
        if (foundPages.size != hentaiInfo.pages.size) {
            return Result.failure(IllegalArgumentException("Directory does not contain all images"))
        }

        return write(
            hentaiInfo = hentaiInfo.hentaiInfo,
            imageContents = foundPages.asSequence().map { InMemoryFile(extensions.renameImage(it.name), it.readBytes()) },
            outputFile = outputFile
        )
    }

    private suspend fun write(
        hentaiInfo: HentaiInfo,
        imageContents: Sequence<InMemoryFile>,
        outputFile: File,
    ): Result<Unit> {
        val writerMeta = hentaiWriter.getWriterMeta(
            hentaiInfo,
            allowRewrite = true,
            desiredName = outputFile.name
        )
            .getOrElse {
                return Result.failure(it)
            }

        val result = run {
            hentaiWriter.writeHentaiInfo(writerMeta, hentaiInfo)
                .onFailure {
                    return@run Result.failure(it)
                }


            runCatching {
                for (file in imageContents) {
                    hentaiWriter.writeImage(writerMeta, file.name, file.contents)
                        .getOrThrow()
                }
            }
                .onFailure {
                    return@run Result.failure(it)
                }

            hentaiWriter.finish(writerMeta)
        }
            .onFailure {
                hentaiWriter.abort(writerMeta)
            }

        return result
    }
}

private data class InMemoryFile(
    val name: String,
    val contents: ByteArray,
)

private fun ByteArray.verifyCrc32(expectedCrc32: Long): Boolean {
    val crc32 = CRC32()
    crc32.update(this)
    return crc32.value == expectedCrc32
}
