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
    val idsFile: String? by option(ArgType.String, shortName = "ids", description = "Path to a file containing new-line separated ids")
    val searchLanguage: Language? by option(ArgType.Choice<Language>(), shortName = "lang", description = "Language to search IDs for")
    val id: Int? by option(ArgType.Int, shortName = "id", description = "ID of a hentai to download")

    val outputDir: String by option(ArgType.String, shortName = "o", description = "Output directory").default("./")
    val outputFormat: OutputFormat by option(ArgType.Choice<OutputFormat>(), shortName = "f", description = "Output format").default(OutputFormat.PLAIN_IMAGES)

    val userAgent: String by option(ArgType.String, shortName = "ua", description = "User-Agent to use for requests").required()
    val cookies: String by option(ArgType.String, shortName = "ck", description = "Cookies to use for requests").required()
    val requestDelayInMillis: Int by option(ArgType.Int, shortName = "delay", description = "Delay between requests in milliseconds").default(100)
    val concurrencyLevelImage: Int by option(ArgType.Int, shortName = "concurrency-image", description = "Number of concurrent requests").default(10)
    val concurrencyLevelTitle: Int by option(ArgType.Int, shortName = "concurrency-title", description = "Number of concurrent requests").default(2)

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
            directory = File(outputDir)
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
            outputFormat = outputFormat
        )
    }
}

data class Config(
    val writerConfig: WriterConfig,
    val httpConfig: HttpConfig,
    val idToDownload: HentaiId?,
    val searchConfig: SearchConfig?,
    val fileHentaiProviderConfig: FileHentaiIdProviderConfig?,
    val outputFormat: OutputFormat
)