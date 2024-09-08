package org.lifeutils.nhentaidl

import com.fasterxml.jackson.databind.ObjectMapper
import org.lifeutils.nhentaidl.config.AppHeaderConfig
import org.lifeutils.nhentaidl.config.Header
import org.lifeutils.nhentaidl.config.WriterConfig
import org.lifeutils.nhentaidl.log.StdoutLogger
import org.lifeutils.nhentaidl.model.HentaiId
import org.lifeutils.nhentaidl.scraper.HentaiScraper
import org.lifeutils.nhentaidl.writer.FileHentaiWriter
import java.io.File
import java.net.http.HttpClient

fun main() {
    val httpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)
        .build()

    val appHeaderConfig = AppHeaderConfig(
        headers = listOf(
            Header("User-Agent", ""),
            Header("Cookie", "")
        )
    )

    val stdoutLogger = StdoutLogger()
    val objectMapper = ObjectMapper()
        .findAndRegisterModules()

    val hentaiWriter = FileHentaiWriter(
        writerConfig = WriterConfig(
            directory = File("hentaiOutput").also { it.mkdirs() }
        ),
        objectMapper = objectMapper
    )

    val hentaiScraper = HentaiScraper(
        httpClient = httpClient,
        headerConfig = appHeaderConfig,
        hentaiWriter = hentaiWriter,
        log = stdoutLogger
    )

    hentaiScraper.hentai(HentaiId(529107))

    stdoutLogger.close()
}
