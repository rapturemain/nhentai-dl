package org.lifeutils.nhentaidl.scraper

import org.lifeutils.nhentaidl.config.AppHeaderConfig
import org.lifeutils.nhentaidl.log.Logger
import org.lifeutils.nhentaidl.model.HentaiId
import org.lifeutils.nhentaidl.writer.HentaiWriter
import org.lifeutils.nhentaidl.writer.HentaiWriterMeta
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

private const val BASE_URL_HENTAI = "https://nhentai.net/g/%hentaiId%/"

class HentaiScraper<WriterMeta : HentaiWriterMeta>(
    private val httpClient: HttpClient,
    private val headerConfig: AppHeaderConfig,
    private val hentaiWriter: HentaiWriter<WriterMeta>,
    private val log: Logger,
) {
    fun hentai(id: HentaiId) {
        log("Fetching hentai: ${id.id}")

        val url = BASE_URL_HENTAI.replace("%hentaiId%", id.id.toString())

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .addHeaders(headerConfig)
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        if (!response.isSucceeded()) {
            log("Failed to fetch hentai: $id")
            return
        }

        val info = scrapHentaiTitlePage(id, response.body())

        val writerMeta = hentaiWriter.getWriterMeta(hentaiInfo = info.hentaiInfo)

        for (hentaiPage in info.pages) {
            fetchImage(writerMeta, hentaiPage)
        }

        val metaWriteResult = hentaiWriter.writeHentaiInfo(writerMeta, info.hentaiInfo)
        if (metaWriteResult.isFailure) {
            log("Failed to write metadata: ${metaWriteResult.exceptionOrNull()}")
        }

        log("Fetched hentai: ${info.hentaiInfo.id.id} - ${info.hentaiInfo.title}")
    }

    private fun fetchImage(writerMeta: WriterMeta, hentaiPage: HentaiPage) {
        log("Fetching image: ${hentaiPage.url}")

        val request = HttpRequest.newBuilder()
            .uri(URI.create(hentaiPage.url))
            .addHeaders(headerConfig)
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())

        if (!response.isSucceeded()) {
            log("Failed to fetch image: ${hentaiPage.url}")
            return
        }

        val imageWriteResult = hentaiWriter.writeImage(writerMeta, hentaiPage.fileName, response.body())
        if (imageWriteResult.isFailure) {
            log("Failed to write image: ${imageWriteResult.exceptionOrNull()}")
        }
    }
}
