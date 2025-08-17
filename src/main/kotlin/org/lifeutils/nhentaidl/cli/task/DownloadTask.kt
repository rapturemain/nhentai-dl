package org.lifeutils.nhentaidl.cli.task

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.lifeutils.nhentaidl.cli.ApplicationContext
import org.lifeutils.nhentaidl.getMessageWithCause
import org.lifeutils.nhentaidl.model.HentaiId
import org.lifeutils.nhentaidl.writer.AlreadyExistsException
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class DownloadTask(
    private val appContext: ApplicationContext,
) : Task {
    override suspend fun run() {
        println("Download task started")

        val config = appContext.config

        val ids = when {
            config.searchConfig != null -> appContext.searchScraper.provideIdsToDownload(config.searchConfig)
            config.fileHentaiProviderConfig != null -> appContext.fileHentaiIdProvider.provideIdsToDownload(config.fileHentaiProviderConfig)
            config.idToDownload != null -> Result.success(listOf(config.idToDownload))
            else -> throw IllegalArgumentException("Must provide either search or file doujinshiIds config")
        }
            .getOrElse {
                throw IllegalArgumentException("Failed to provide doujinshi IDs: ${it.message}")
            }
            .toSet()
            .take(config.countLimit)

        val failedDownloads = ConcurrentLinkedQueue<Pair<HentaiId, Throwable>>()
        val successfulDownloads = AtomicInteger(0)

        val semaphore = Semaphore(config.httpConfig.concurrencyLevelTitle)

        withContext(Dispatchers.IO) {
            val jobs = ids.map {
                async {
                    semaphore.withPermit {
                        if (appContext.isInterrupted.get()) {
                            return@async
                        }

                        appContext.hentaiScraper.hentai(it)
                            .onSuccess {
                                successfulDownloads.incrementAndGet()
                            }
                            .onFailure { exception ->
                                when (exception) {
                                    is AlreadyExistsException -> {
                                        appContext.logger("Doujinshi: ${it.id} already exists. Skipping.")
                                        successfulDownloads.incrementAndGet()
                                    }

                                    else -> {
                                        appContext.logger.error("Failed to download doujinshi: ${it.id}. Exception: ${exception.stackTraceToString()}")
                                        failedDownloads.add(it to exception)
                                    }
                                }
                            }
                    }
                }
            }

            jobs.awaitAll()
        }

        appContext.logger("=================\nFinished downloading doujinshi")
        if (failedDownloads.isNotEmpty()) {
            val failedIds = failedDownloads.joinToString("\n") { (hentaiId, _) -> "${hentaiId.id}" }
            val failedIdsFile = File(config.failedIdsPath)
            failedIdsFile.writeText(failedIds)

            val messages = failedDownloads.joinToString(separator = "\n") { (hentaiId, exception) ->
                "Doujinshi: ${hentaiId.id}, Exception: ${exception.getMessageWithCause()}"
            }
            appContext.logger("Failed doujinshi IDs:")
            appContext.logger(messages)
        }
        appContext.logger("Successfully downloaded $successfulDownloads doujinshi")
        appContext.logger("Failed to download ${failedDownloads.size}.")
    }
}
