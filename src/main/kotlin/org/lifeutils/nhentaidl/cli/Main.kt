package org.lifeutils.nhentaidl.cli

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.lifeutils.nhentaidl.config.FileHentaiIdProvider
import org.lifeutils.nhentaidl.config.OutputFormat
import org.lifeutils.nhentaidl.log.StdoutLogger
import org.lifeutils.nhentaidl.model.HentaiId
import org.lifeutils.nhentaidl.scraper.HentaiScraper
import org.lifeutils.nhentaidl.scraper.SearchScraper
import org.lifeutils.nhentaidl.writer.BufferedZipFileHentaiWriter
import org.lifeutils.nhentaidl.writer.FileHentaiWriter
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

suspend fun main(args: Array<String>) {
    val config = CliArgsParser(args).toConfig()

    val httpClient = HttpClient()

    val stdoutLogger = StdoutLogger()
    val objectMapper = ObjectMapper()
        .findAndRegisterModules()
        .enable(SerializationFeature.INDENT_OUTPUT)

    val writer = when (config.outputFormat) {
        OutputFormat.PLAIN_IMAGES -> FileHentaiWriter(
            writerConfig = config.writerConfig,
            objectMapper = objectMapper
        )

        OutputFormat.ZIP_ARCHIVE -> BufferedZipFileHentaiWriter(
            writerConfig = config.writerConfig,
            objectMapper = objectMapper,
            log = stdoutLogger
        )
    }

    val hentaiScraper = HentaiScraper(
        httpClient = httpClient,
        httpConfig = config.httpConfig,
        hentaiWriter = writer,
        log = stdoutLogger
    )

    val searchScraper = SearchScraper(
        httpClient = httpClient,
        httpConfig = config.httpConfig,
        log = stdoutLogger
    )

    val fileHentaiIdProvider = FileHentaiIdProvider()

    val multipleSources = listOf(config.searchConfig, config.fileHentaiProviderConfig, config.idToDownload)
        .count { it != null } > 1
    if (multipleSources) {
        throw IllegalArgumentException("Cannot provide both search and file hentaiIds config")
    }

    val ids = when {
        config.searchConfig != null -> searchScraper.provideIdsToDownload(config.searchConfig)
        config.fileHentaiProviderConfig != null -> fileHentaiIdProvider.provideIdsToDownload(config.fileHentaiProviderConfig)
        config.idToDownload != null -> Result.success(listOf(config.idToDownload))
        else -> throw IllegalArgumentException("Must provide either search or file hentaiIds config")
    }
        .getOrElse {
            throw IllegalArgumentException("Failed to provide hentai IDs: ${it.message}")
        }

    val failedHentais = ConcurrentLinkedQueue<HentaiId>()
    val successfulHentais = AtomicInteger(0)

    val semaphore = Semaphore(config.httpConfig.concurrencyLevelTitle)

    withContext(Dispatchers.IO) {
        val jobs = ids.map {
            async {
                semaphore.withPermit {
                    hentaiScraper.hentai(it).apply {
                        if (isSuccess) {
                            successfulHentais.incrementAndGet()
                        } else {
                            stdoutLogger("Failed to download hentai: ${it.id}. Exception: ${exceptionOrNull()}")
                            failedHentais.add(it)
                        }
                    }
                }
            }
        }

        jobs.forEach {
            it.await()
        }
    }

    writer.flush()

    stdoutLogger("\n=================\nFinished downloading hentais")
    stdoutLogger("Failed hentaiIds:")
    if (failedHentais.isNotEmpty()) {
        stdoutLogger(failedHentais.joinToString(separator = "\n") { it.id.toString() })
    }
    stdoutLogger("\nSuccessfully downloaded $successfulHentais hentais")
    stdoutLogger("Failed to download ${failedHentais.size}.")
}