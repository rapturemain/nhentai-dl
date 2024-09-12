package org.lifeutils.nhentaidl.scraper

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.contentLength
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.lifeutils.nhentaidl.config.HttpConfig
import org.lifeutils.nhentaidl.log.Logger
import org.lifeutils.nhentaidl.model.HentaiId
import org.lifeutils.nhentaidl.writer.HentaiWriter
import org.lifeutils.nhentaidl.writer.HentaiWriterMeta

private const val BASE_URL_HENTAI = "https://nhentai.net/g/%hentaiId%/"

private const val IMAGE_SIZE_LIMIT: Long = 32 * 1024 * 1024

class HentaiScraper<WriterMeta : HentaiWriterMeta>(
    private val httpClient: HttpClient,
    private val httpConfig: HttpConfig,
    private val hentaiWriter: HentaiWriter<WriterMeta>,
    private val log: Logger,
) {
    suspend fun hentai(id: HentaiId): Result<Unit> {
        log("Fetching hentai: ${id.id}")

        val url = BASE_URL_HENTAI.replace("%hentaiId%", id.id.toString())

        val response = retryHttpRequest(delayMillis = httpConfig.requestDelayInMillis) {
            httpClient.get {
                url(url)
                addHeaders(httpConfig)
            }
        }
            .getOrElse {
                return Result.failure(it)
            }

        val info = scrapHentaiTitlePage(id, response.bodyAsText())

        val writerMeta = hentaiWriter.getWriterMeta(hentaiInfo = info.hentaiInfo).getOrElse {
            return Result.failure(it)
        }

        val semaphore = Semaphore(httpConfig.concurrencyLevelImage)

        coroutineScope {
            val jobs = info.pages.map { hentaiPage ->
                async {
                    semaphore.withPermit {
                        fetchImage(writerMeta, hentaiPage)
                    }
                }
            }

            jobs.forEach {
                val result = it.await()
                if (result.isFailure) {
                    return@coroutineScope result
                }
            }

            Result.success(Unit)
        }
            .onFailure {
                return Result.failure(it)
            }

        val metaWriteResult = hentaiWriter.writeHentaiInfo(writerMeta, info.hentaiInfo)
        if (metaWriteResult.isFailure) {
            return metaWriteResult
        }

        hentaiWriter.finish(writerMeta).onFailure {
            return Result.failure(it)
        }

        log("Fetching hentai: ${id.id}... Total of ${info.hentaiInfo.pageCount} pages. Done")

        return Result.success(Unit)
    }

    private suspend fun fetchImage(writerMeta: WriterMeta, hentaiPage: HentaiPage): Result<Unit> {
        log("Fetching image ${hentaiPage.page} of hentai ${writerMeta.hentaiId.id}: ${hentaiPage.url}")

        val response = retryHttpRequest(delayMillis = httpConfig.requestDelayInMillis) {
            httpClient.get {
                url(hentaiPage.url)
                addHeaders(httpConfig)
            }
        }
            .getOrElse {
                return Result.failure(it)
            }

        val size = response.contentLength() ?: return Result.failure(IllegalArgumentException("Cannot get image size"))
        if (size > IMAGE_SIZE_LIMIT) {
            return Result.failure(IllegalArgumentException("Image size is too large: $size"))
        }

        val imageWriteResult = hentaiWriter.writeImage(writerMeta, hentaiPage.fileName, size, response.bodyAsChannel())
        if (imageWriteResult.isFailure) {
            return imageWriteResult
        }

        return Result.success(Unit)
    }
}
