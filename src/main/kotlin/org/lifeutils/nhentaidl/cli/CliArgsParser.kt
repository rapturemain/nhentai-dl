package org.lifeutils.nhentaidl.cli

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.lifeutils.nhentaidl.config.HttpConfig
import org.lifeutils.nhentaidl.config.FileHentaiIdProviderConfig
import org.lifeutils.nhentaidl.config.MigrationConfig
import org.lifeutils.nhentaidl.config.OutputFormat
import org.lifeutils.nhentaidl.config.SearchConfig
import org.lifeutils.nhentaidl.config.WriterConfig
import org.lifeutils.nhentaidl.model.HentaiId
import org.lifeutils.nhentaidl.model.oldmetadata.LanguageV3
import java.io.File

class CliArgsParser(args: Array<String>) : ArgParser("nhentai-dl") {
    // ids config
    private val idsFile: String? by option(
        ArgType.String,
        shortName = "ids",
        description = "Path to a file containing new-line separated ids"
    )
    private val id: Int? by option(ArgType.Int, shortName = "id", description = "ID of a hentai to download")
    private val searchLanguage: LanguageV3? by option(
        ArgType.Choice<LanguageV3>(),
        shortName = "lang",
        description = "Language to search IDs for"
    )
    private val searchArtist: String? by option(
        ArgType.String,
        shortName = "artist",
        description = "Artist name to search for"
    )
    private val countLimit: Int? by option(
        ArgType.Int,
        shortName = "count",
        description = "Number of titles to download (unlimited by default)"
    )

    // writer config
    private val outputDir: String by option(
        ArgType.String,
        shortName = "o",
        description = "Output directory"
    )
        .default("./")
    private val outputFormat: OutputFormat by option(
        ArgType.Choice<OutputFormat>(),
        shortName = "f",
        description = "Output format"
    )
        .default(OutputFormat.PLAIN_IMAGES)
    private val failedIdsPath: String by option(
        ArgType.String,
        shortName = "failed",
        description = "Path to a file to save failed IDs"
    )
        .default("failed-doujinshi.txt")
    private val bufferedWriterBufferSize: Int by option(
        ArgType.Int,
        shortName = "writer-buffer",
        description = "Number of titles to keep in memory before flushing to disk. Applied only to ZIP_ARCHIVE output format. " +
                "Significantly increases memory usage."
    )
        .default(20)
    private val verifyImages: Boolean by option(
        ArgType.Boolean,
        shortName = "verify-images",
        description = "Whether to verify images before saving them. Increases memory and CPU usage. " +
            "Defaults to false since I didn't find any issues with image download " +
            "(the image will be corrupted only if it is corrupted on the website)."
    )
        .default(false)

    // http config
    private val userAgent: String by option(
        ArgType.String,
        shortName = "ua",
        description = "User-Agent to use for requests"
    )
        .required()
    private val cookies: String by option(
        ArgType.String,
        shortName = "ck",
        description = "Cookies to use for requests"
    )
        .required()
    private val requestDelayInMillis: Int by option(
        ArgType.Int,
        shortName = "delay",
        description = "Delay between requests in milliseconds in case of failure. " +
                "Each subsequent failure will have greater retryDelay = [retry^2 * delay]. " +
                "Retries capped at 5"
    )
        .default(1000)
    private val concurrencyLevelImage: Int by option(
        ArgType.Int,
        shortName = "concurrency-image",
        description = "Number of concurrent requests for images per title. " +
                "Total number of simultaneous requests = [concurrency-image * concurrency-title]"
    )
        .default(10)
    private val concurrencyLevelTitle: Int by option(
        ArgType.Int,
        shortName = "concurrency-title",
        description = "Number of concurrent titles to download at once. It's recommended to set this value higher (e.g. 16) for migration."
    )
        .default(3)

    // migration
    private val migrationSourceDir: String? by option(
        ArgType.String,
        shortName = "migrate-from",
        description = "Path to a directory with doujinshi to migrate to latest metadata version"
    )
    private val migration: Boolean by option(
        ArgType.Boolean,
        shortName = "migrate",
        description = "Enable migration mode. If '-migrate-from' is not specified, '-o' value will be used"
    )
        .default(false)
    private val migrationRemoveSrc: Boolean by option(
        ArgType.Boolean,
        shortName = "migrate-remove-source",
        description = "Remove source doujinshi. Migrated doujinshi source will have 'MIGRATION_' prefix if rename to name prior migration is not possible"
    )
        .default(true)

    init {
        parse(args)
    }

    fun toConfig(): Config {
        // write/read section
        val writerConfig = WriterConfig(
            directory = File(outputDir),
            flushBufferSize = bufferedWriterBufferSize
        )
        val httpConfig = HttpConfig(
            headers = listOf(
                org.lifeutils.nhentaidl.config.Header("User-Agent", userAgent),
                org.lifeutils.nhentaidl.config.Header("Cookie", cookies)
            ),
            requestDelayInMillis = requestDelayInMillis.toLong(),
            concurrencyLevelImage = concurrencyLevelImage,
            concurrencyLevelTitle = concurrencyLevelTitle
        )

        // IDs source section
        val searchSources = listOfNotNull(searchArtist, searchLanguage)
        val searchConfig = if (searchSources.isEmpty()) {
            null
        } else {
            SearchConfig(
                searchLanguage = searchLanguage,
                searchArtist = searchArtist,
                countLimit = countLimit ?: Int.MAX_VALUE,
            )
        }

        val idsFromFileConfig = idsFile?.let {
            FileHentaiIdProviderConfig(
                file = File(it)
            )
        }

        val singleId = id?.let { HentaiId(it) }

        // migration section
        val migrationConfig = when {
            migration -> {
                val srcDir = migrationSourceDir?.let { File(it) } ?: writerConfig.directory
                MigrationConfig(
                    sourceDir = srcDir,
                    removeSrc = migrationRemoveSrc
                )
            }
            migrationSourceDir != null -> {
                MigrationConfig(
                    sourceDir = File(migrationSourceDir!!),
                    removeSrc = migrationRemoveSrc
                )
            }
            else -> null
        }

        return Config(
            writerConfig = writerConfig,
            httpConfig = httpConfig,
            idToDownload = singleId,
            searchConfig = searchConfig,
            fileHentaiProviderConfig = idsFromFileConfig,
            outputFormat = outputFormat,
            verifyImages = verifyImages,
            countLimit = countLimit ?: Int.MAX_VALUE,
            failedIdsPath = failedIdsPath,
            migrationConfig = migrationConfig
        )
    }
}

data class Config(
    // http
    val httpConfig: HttpConfig,

    // write
    val outputFormat: OutputFormat,
    val verifyImages: Boolean,
    val writerConfig: WriterConfig,

    // ids
    val idToDownload: HentaiId?,
    val searchConfig: SearchConfig?,
    val fileHentaiProviderConfig: FileHentaiIdProviderConfig?,
    val countLimit: Int,

    // failed download/migrations
    val failedIdsPath: String,

    // migration
    val migrationConfig: MigrationConfig?
) {
    init {
        if (isDownload && isMigration) {
            throw IllegalArgumentException("Cannot provide both download and migration config")
        }

        if (isDownload) {
            val sources = listOf(fileHentaiProviderConfig, searchConfig, idToDownload)

            val multipleSources = sources.count { it != null } > 1
            if (multipleSources) {
                throw IllegalArgumentException("Cannot provide both search and file hentaiIds config")
            }

            val noSources = sources.count { it != null } == 0
            if (noSources) {
                throw IllegalArgumentException("Must provide either search or file hentaiIds config")
            }
        }

        if (countLimit <= 0) {
            throw IllegalArgumentException("Count limit must be greater than 0")
        }

        writerConfig.verify()
        httpConfig.verify()
        fileHentaiProviderConfig?.verify()
        migrationConfig?.verify()
        searchConfig?.verify()
    }

    val isMigration: Boolean
        get() = migrationConfig != null

    val isDownload: Boolean
        get() = idToDownload != null || searchConfig != null || fileHentaiProviderConfig != null
}