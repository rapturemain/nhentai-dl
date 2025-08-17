package org.lifeutils.nhentaidl.migration

import com.fasterxml.jackson.databind.ObjectMapper
import org.lifeutils.nhentaidl.config.MigrationConfig
import org.lifeutils.nhentaidl.scraper.HentaiScraper
import org.lifeutils.nhentaidl.writer.HentaiWriter
import org.lifeutils.nhentaidl.writer.METADATA_FILE_NAME
import java.io.File
import java.util.zip.ZipInputStream

const val SCHEDULED_FOR_MIGRATION_PREFIX = "MIGRATION_"

class GenericMigrationService(
    private val objectMapper: ObjectMapper,
    private val migrators: List<MigrationService>,
    private val migrationConfig: MigrationConfig,
) {
    suspend fun migrateFile(file: File): Result<MigrationStatus> {
        val migrator = when {
            file.isDirectory -> selectMigratorByDirectory(file)
            file.name.endsWith(".zip") -> selectMigratorByZipFile(file)
            else -> return Result.failure(
                IllegalArgumentException(
                    "Cannot migrate ${file.absolutePath}. File type is unknown"
                )
            )
        }
            .getOrElse {
                return Result.failure(it)
            }

        if (migrator is MigrationServiceCurrent) {
            return Result.success(MigrationStatus.UP_TO_DATE)
        }

        val tempRenamedFile = File(file.parentFile, "${SCHEDULED_FOR_MIGRATION_PREFIX}${file.name}")

        if (file.renameTo(tempRenamedFile)) {
            migrator.migrate(
                file = tempRenamedFile,
                outputFile = file
            )
                .onFailure {
                    tempRenamedFile.renameTo(file)
                    return Result.failure(it)
                }
        } else {
            Result.failure(
                IllegalArgumentException(
                    "Failed to rename directory ${file.absolutePath} to ${tempRenamedFile.absolutePath}"
                )
            )
        }

        if (migrationConfig.removeSrc) {
            if (tempRenamedFile.isDirectory) {
                tempRenamedFile.deleteRecursively()
            } else {
                tempRenamedFile.delete()
            }
        } else {
            // we don't care if we fail this operation
            tempRenamedFile.renameTo(file)
        }

        return Result.success(MigrationStatus.MIGRATED)
    }

    private suspend fun selectMigratorByDirectory(directory: File): Result<MigrationService> {
        return runCatching {
            val files = directory.listFiles()
                ?: return Result.failure(
                    IllegalArgumentException(
                        "Directory ${directory.absolutePath} does not exists??"
                    )
                )

            val metadataFile = files.firstOrNull { it.name == METADATA_FILE_NAME }
                ?: return Result.failure(
                    IllegalArgumentException(
                        "Directory ${directory.absolutePath} does not contain metadata file"
                    )
                )

            val metadata = metadataFile.readText()

            return selectMigrator(metadata)
                ?.let { Result.success(it) }
                ?: return Result.failure(
                    IllegalArgumentException(
                        "No migrator found for metadata of hentai: ${directory.absolutePath}. Probably is not a hentai or file is modified."
                    )
                )
        }
    }

    private suspend fun selectMigratorByZipFile(zipFile: File): Result<MigrationService> {
        return runCatching {
            val foundMetadata = zipFile.inputStream().use zip@{
                ZipInputStream(it).use { zip ->
                    for (zipEntry in generateSequence { zip.nextEntry }) {
                        if (zipEntry.name == METADATA_FILE_NAME) {
                            return@zip zip.readBytes()
                        }
                        zip.closeEntry()
                    }
                    return@zip null
                }
            }
                ?: return Result.failure(
                    IllegalArgumentException(
                        "ZIP file ${zipFile.absolutePath} does not contain metadata file"
                    )
                )

            val metadata = String(foundMetadata)

            return selectMigrator(metadata)
                ?.let { Result.success(it) }
                ?: return Result.failure(
                    IllegalArgumentException(
                        "No migrator found for metadata of hentai: ${zipFile.absolutePath}. Probably is not a hentai or file is modified."
                    )
                )
        }
    }

    private suspend fun selectMigrator(metadata: String): MigrationService? {
        val json = objectMapper.readTree(metadata)
        return migrators.firstOrNull {
            it.canMigrate(json)
        }
    }
}

fun createMigrationService(
    objectMapper: ObjectMapper,
    hentaiWriter: HentaiWriter<*>,
    hentaiScraper: HentaiScraper<*>,
    migrationConfig: MigrationConfig,
): GenericMigrationService {
    val v3 = createMigrationServiceV3V2V1(
        objectMapper = objectMapper,
        hentaiScraper = hentaiScraper,
        hentaiWriter = hentaiWriter,
    )

    val ricterZ = createMigrationServiceRicterZ(
        objectMapper = objectMapper,
        hentaiScraper = hentaiScraper,
        hentaiWriter = hentaiWriter,
    )

    val current = MigrationServiceCurrent(
        objectMapper = objectMapper,
    )

    return GenericMigrationService(
        objectMapper = objectMapper,
        migrators = listOf(v3, ricterZ, current),
        migrationConfig = migrationConfig,
    )
}

enum class MigrationStatus {
    MIGRATED,
    UP_TO_DATE,
}