package org.lifeutils.nhentaidl.cli

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import org.lifeutils.nhentaidl.config.FileHentaiIdProvider
import org.lifeutils.nhentaidl.config.OutputFormat
import org.lifeutils.nhentaidl.imageverifier.ImageVerifierFactory
import org.lifeutils.nhentaidl.log.StdoutLogger
import org.lifeutils.nhentaidl.scraper.HentaiScraper
import org.lifeutils.nhentaidl.scraper.SearchScraper
import org.lifeutils.nhentaidl.writer.BufferedZipFileHentaiWriter
import org.lifeutils.nhentaidl.writer.FileHentaiWriter
import org.lifeutils.nhentaidl.writer.HentaiWriter
import sun.misc.Signal
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.exitProcess

class ApplicationContext(val config: Config) {
    val writer: HentaiWriter<*>
    val searchScraper: SearchScraper
    val hentaiScraper: HentaiScraper<*>
    val fileHentaiIdProvider: FileHentaiIdProvider = FileHentaiIdProvider()
    val logger = StdoutLogger()
    val objectMapper: ObjectMapper

    val isInterrupted = AtomicBoolean(false)
    private val isInterruptedTime = AtomicLong(0)

    init {
        Signal.handle(Signal("INT")) {
            val time = System.currentTimeMillis()
            if (time - isInterruptedTime.get() < 5000) {
                println("Forcing exit...")
                exitProcess(0)
            } else {
                println("Shutting down... Please wait till we finish downloading partially downloaded titles and" +
                    "save already downloaded to the drive. Press Ctrl+C again in 5 secs to force exit.")
                isInterrupted.set(true)
                isInterruptedTime.set(time)
            }
        }
    }

    init {
        val httpClient = HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
            }
        }

        objectMapper = ObjectMapper()
            .registerModule(JavaTimeModule())
            .registerKotlinModule()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

        val imageVerifier = if (config.verifyImages) {
            ImageVerifierFactory().createImageVerifier()
        } else {
            null
        }

        writer = when (config.outputFormat) {
            OutputFormat.PLAIN_IMAGES -> FileHentaiWriter(
                writerConfig = config.writerConfig,
                objectMapper = objectMapper,
                log = logger,
                imageVerifier = imageVerifier,
            )

            OutputFormat.ZIP_ARCHIVE -> BufferedZipFileHentaiWriter(
                writerConfig = config.writerConfig,
                objectMapper = objectMapper,
                log = logger,
                imageVerifier = imageVerifier
            )
        }

        hentaiScraper = HentaiScraper(
            httpClient = httpClient,
            httpConfig = config.httpConfig,
            writer = writer,
            log = logger
        )

        searchScraper = SearchScraper(
            httpClient = httpClient,
            httpConfig = config.httpConfig,
            log = logger
        )
    }

    suspend fun close() {
        writer.flush()
        logger.close()
    }
}
