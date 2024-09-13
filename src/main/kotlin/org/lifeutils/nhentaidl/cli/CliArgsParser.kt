package org.lifeutils.nhentaidl.cli

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.lifeutils.nhentaidl.config.HttpConfig
import org.lifeutils.nhentaidl.config.FileHentaiIdProviderConfig
import org.lifeutils.nhentaidl.config.OutputFormat
import org.lifeutils.nhentaidl.config.SearchConfig
import org.lifeutils.nhentaidl.config.WriterConfig
import org.lifeutils.nhentaidl.model.HentaiId
import org.lifeutils.nhentaidl.model.Language
import java.io.File

class CliArgsParser(args: Array<String>) : ArgParser("nhentai-dl") {
    // ids config
    private val idsFile: String? by option(ArgType.String, shortName = "ids", description = "Path to a file containing new-line separated ids")
    private val searchLanguage: Language? by option(ArgType.Choice<Language>(), shortName = "lang", description = "Language to search IDs for")
    private val id: Int? by option(ArgType.Int, shortName = "id", description = "ID of a hentai to download")
    private val countLimit: Int? by option(ArgType.Int, shortName = "count", description = "Number of titles to download (unlimited by default)")

    // writer config
    private val outputDir: String by option(ArgType.String, shortName = "o", description = "Output directory").default("./")
    private val outputFormat: OutputFormat by option(ArgType.Choice<OutputFormat>(), shortName = "f", description = "Output format").default(OutputFormat.PLAIN_IMAGES)
    private val failedIdsPath: String by option(ArgType.String, shortName = "failed", description = "Path to a file to save failed IDs").default("failed-ids.txt")
    private val bufferedWriterBufferSize: Int by option(
        ArgType.Int,
        shortName = "writer-buffer",
        description = "Number of titles to keep in memory until flushing to disk. Applied only to ZIP_ARCHIVE output format. " +
                "Useful for saving HDD resource by writing large chunk of data at once. Significantly increases memory usage." +
                "(actual reason is that my external HDD always clicks when it is used, so I prefer having less clicks by writing in large chunks)"
    )
        .default(20)

    // http config
    private val userAgent: String by option(ArgType.String, shortName = "ua", description = "User-Agent to use for requests").required()
    private val cookies: String by option(ArgType.String, shortName = "ck", description = "Cookies to use for requests").required()
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
        description = "Number of concurrent titles to download at once"
    )
        .default(2)

    private val verifyImages: Boolean by option(
        ArgType.Boolean,
        shortName = "verify-images",
        description = "Whether to verify images before saving them. Increases memory and CPU usage."
    )
        .default(false)

    init {
        parse(args)

        val multipleSources = listOf(idsFile, searchLanguage, id)
            .count { it != null } > 1
        if (multipleSources) {
            throw IllegalArgumentException("Cannot provide both search and file hentaiIds config")
        }

        val noSources = listOf(idsFile, searchLanguage, id)
            .count { it != null } == 0
        if (noSources) {
            throw IllegalArgumentException("Must provide either search or file hentaiIds config")
        }

        if (requestDelayInMillis < 0) {
            throw IllegalArgumentException("Request delay must be >= 0")
        }

        if (concurrencyLevelImage < 1) {
            throw IllegalArgumentException("Concurrency level for images must be >= 1")
        }

        if (concurrencyLevelTitle < 1) {
            throw IllegalArgumentException("Concurrency level for titles must be >= 1")
        }
    }

    fun toConfig(): Config {
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
        val searchConfig = searchLanguage?.let { SearchConfig(
            searchLanguage = it
        ) }
        val fileHentaiProviderConfig = idsFile?.let { FileHentaiIdProviderConfig(
            file = File(it)
        ) }

        return Config(
            writerConfig = writerConfig,
            httpConfig = httpConfig,
            idToDownload = id?.let { HentaiId(it) },
            searchConfig = searchConfig,
            fileHentaiProviderConfig = fileHentaiProviderConfig,
            outputFormat = outputFormat,
            verifyImages = verifyImages,
            countLimit = countLimit,
            failedIdsPath = failedIdsPath
        )
    }
}

data class Config(
    val writerConfig: WriterConfig,
    val httpConfig: HttpConfig,
    val idToDownload: HentaiId?,
    val searchConfig: SearchConfig?,
    val fileHentaiProviderConfig: FileHentaiIdProviderConfig?,
    val outputFormat: OutputFormat,
    val verifyImages: Boolean,
    val countLimit: Int?,
    val failedIdsPath: String,
)