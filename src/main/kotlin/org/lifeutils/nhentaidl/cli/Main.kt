package org.lifeutils.nhentaidl.cli

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.lifeutils.nhentaidl.config.FileHentaiIdProvider
import org.lifeutils.nhentaidl.config.OutputFormat
import org.lifeutils.nhentaidl.log.StdoutLogger
import org.lifeutils.nhentaidl.model.HentaiId
import org.lifeutils.nhentaidl.getMessageWithCause
import org.lifeutils.nhentaidl.imageverifier.ImageVerifierFactory
import org.lifeutils.nhentaidl.scraper.HentaiScraper
import org.lifeutils.nhentaidl.scraper.SearchScraper
import org.lifeutils.nhentaidl.writer.AlreadyExistsException
import org.lifeutils.nhentaidl.writer.BufferedZipFileHentaiWriter
import org.lifeutils.nhentaidl.writer.FileHentaiWriter
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

suspend fun main(args: Array<String>) {
    val config = CliArgsParser(args).toConfig()

    val httpClient = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
        }
    }

    val stdoutLogger = StdoutLogger()
    val objectMapper = ObjectMapper()
        .findAndRegisterModules()
        .enable(SerializationFeature.INDENT_OUTPUT)

    val imageVerifier = if (config.verifyImages) {
        ImageVerifierFactory().createImageVerifier()
    } else {
        null
    }

    val writer = when (config.outputFormat) {
        OutputFormat.PLAIN_IMAGES -> FileHentaiWriter(
            writerConfig = config.writerConfig,
            objectMapper = objectMapper,
            log = stdoutLogger,
            imageVerifier = imageVerifier,
        )

        OutputFormat.ZIP_ARCHIVE -> BufferedZipFileHentaiWriter(
            writerConfig = config.writerConfig,
            objectMapper = objectMapper,
            log = stdoutLogger,
            imageVerifier = imageVerifier
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

    val ids = when {
        config.searchConfig != null -> searchScraper.provideIdsToDownload(config.searchConfig)
        config.fileHentaiProviderConfig != null -> fileHentaiIdProvider.provideIdsToDownload(config.fileHentaiProviderConfig)
        config.idToDownload != null -> Result.success(listOf(config.idToDownload))
        else -> throw IllegalArgumentException("Must provide either search or file hentaiIds config")
    }
        .getOrElse {
            throw IllegalArgumentException("Failed to provide hentai IDs: ${it.message}")
        }
        .toSet()
        .take(config.countLimit ?: Int.MAX_VALUE)

    val failedHentais = ConcurrentLinkedQueue<Pair<HentaiId, Throwable>>()
    val successfulHentais = AtomicInteger(0)

    val semaphore = Semaphore(config.httpConfig.concurrencyLevelTitle)

    withContext(Dispatchers.IO) {
        val jobs = ids.map {
            async {
                semaphore.withPermit {
                    hentaiScraper.hentai(it)
                        .onSuccess {
                            successfulHentais.incrementAndGet()
                        }
                        .onFailure { exception ->
                            when (exception) {
                                is AlreadyExistsException -> {
                                    stdoutLogger("Hentai: ${it.id} already exists. Skipping.")
                                    successfulHentais.incrementAndGet()
                                }
                                else -> {
                                    stdoutLogger.error("Failed to download hentai: ${it.id}. Exception: ${exception.stackTraceToString()}")
                                    failedHentais.add(it to exception)
                                }
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
    if (failedHentais.isNotEmpty()) {
        val failedIds = failedHentais.map { (hentaiId, _) -> hentaiId.id }
            .joinToString("\n")
        val failedHentaisFile = File(config.failedIdsPath)
        failedHentaisFile.writeText(failedIds)

        val messages = failedHentais.map { (hentaiId, exception) ->
            "Hentai: ${hentaiId.id}, Exception: ${exception.getMessageWithCause()}"
        }
            .joinToString(separator = "\n")

        stdoutLogger("Failed hentaiIds:")
        stdoutLogger(messages)
    }
    stdoutLogger("\nSuccessfully downloaded $successfulHentais hentais")
    stdoutLogger("Failed to download ${failedHentais.size}.")

    stdoutLogger.close()
}