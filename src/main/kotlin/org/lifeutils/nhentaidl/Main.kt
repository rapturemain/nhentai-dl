package org.lifeutils.nhentaidl

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.lifeutils.nhentaidl.config.HttpConfig
import org.lifeutils.nhentaidl.config.Header
import org.lifeutils.nhentaidl.config.WriterConfig
import org.lifeutils.nhentaidl.log.StdoutLogger
import org.lifeutils.nhentaidl.model.HentaiId
import org.lifeutils.nhentaidl.scraper.HentaiScraper
import org.lifeutils.nhentaidl.writer.BufferedZipFileHentaiWriter
import org.lifeutils.nhentaidl.writer.FileHentaiWriter
import java.io.File

suspend fun main() {
    val httpClient = HttpClient()

    val httpConfig = HttpConfig(
        headers = listOf(
            Header("User-Agent", ""),
            Header("Cookie", "")
        ),
        requestDelayInMillis = 100,
        concurrencyLevelImage = 16,
        concurrencyLevelTitle = 3
    )

    val writerConfig = WriterConfig(
        directory = File("hentaiOutput").also { it.mkdirs() }
    )

    val stdoutLogger = StdoutLogger()
    val objectMapper = ObjectMapper()
        .findAndRegisterModules()
        .enable(SerializationFeature.INDENT_OUTPUT)

    val hentaiWriter = FileHentaiWriter(
        writerConfig = writerConfig,
        objectMapper = objectMapper
    )

    val zipHentaiWriter = BufferedZipFileHentaiWriter(
        writerConfig = writerConfig,
        objectMapper = objectMapper,
        log = stdoutLogger
    )

    val hentaiScraper = HentaiScraper(
        httpClient = httpClient,
        httpConfig = httpConfig,
        hentaiWriter = zipHentaiWriter,
        log = stdoutLogger
    )

    val semaphore = Semaphore(3)

    val result = withContext(Dispatchers.IO) {
        async {
            semaphore.withPermit {
                hentaiScraper.hentai(HentaiId(529111))

            }
        }
        hentaiScraper.hentai(HentaiId(529111))
    }

    zipHentaiWriter.flush()

    println(result)

    stdoutLogger.close()
}
