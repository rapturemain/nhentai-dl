package org.lifeutils.nhentaidl.scraper

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import org.jsoup.Jsoup
import org.lifeutils.nhentaidl.config.HttpConfig
import org.lifeutils.nhentaidl.config.HentaiIdProvider
import org.lifeutils.nhentaidl.config.SearchConfig
import org.lifeutils.nhentaidl.log.Logger
import org.lifeutils.nhentaidl.model.HentaiId
import org.lifeutils.nhentaidl.model.Language

private const val BASE_URL_LANGUAGE = "https://nhentai.net/language/%language%/"

private const val HREF_HENTAI_SELECTOR = "div.container div.gallery a"
private fun extractIdFromHref(href: String) = href.split("/")
    .last { it.isNotEmpty() && it.toIntOrNull() != null }
    .toInt()

private const val HREF_LAST_PAGE_SELECTOR = "section.pagination a.last"
private fun extractLastPageFromHref(href: String) = href.split("=")
    .last { it.isNotEmpty() && it.toIntOrNull() != null }
    .toInt()

class SearchScraper(
    private val httpClient: HttpClient,
    private val httpConfig: HttpConfig,
    private val log: Logger,
) : HentaiIdProvider<SearchConfig> {

    suspend fun search(language: Language, pages: IntRange = 1..<Int.MAX_VALUE): SearchResult {
        return fetchPages(BASE_URL_LANGUAGE.replace("%language%", language.searchParam), pages)
    }

    private suspend fun fetchPages(baseUrl: String, pages: IntRange): SearchResult {
        val allIds = mutableSetOf<HentaiId>()
        val failedPages = mutableListOf<String>()

        var totalPages: Int? = null

        for (page in pages) {
            try {
                val content = fetchPage(baseUrl, page, totalPages)

                val scrapResult = scrapPage(content)
                totalPages = scrapResult.totalPages ?: totalPages

                if (scrapResult.ids.isEmpty()) {
                    break // no more pages
                }
                allIds += scrapResult.ids
            } catch (e: CannotFetchException) {
                failedPages.add("BaseUrl: $baseUrl, page=$page")
            }

            waitTillNextRequest(httpConfig.requestDelayInMillis)
        }

        return SearchResult(
            totalCount = allIds.size,
            ids = allIds.toList(),
            failedPages = failedPages
        )
    }

    private fun scrapPage(pageContent: String): ScrapResult {
        val doc = Jsoup.parse(pageContent)
        val idElements = doc.select(HREF_HENTAI_SELECTOR)
        val ids = idElements.map {
            val href = it.attr("href")
            HentaiId(extractIdFromHref(href))
        }

        val lastPageElement = doc.select(HREF_LAST_PAGE_SELECTOR).lastOrNull()
        val lastPage = lastPageElement?.let {
            extractLastPageFromHref(it.attr("href"))
        }

        return ScrapResult(ids, lastPage)
    }

    private suspend fun fetchPage(baseUrl: String, page: Int, totalPages: Int?): String {
        val url = "$baseUrl?page=$page"
        return retryHttpRequest(delayMillis = httpConfig.requestDelayInMillis) {
            log("Fetching page $url" + if (totalPages != null) " of $totalPages" else "")

            httpClient.get {
                url(url)
                addHeaders(httpConfig)
            }
        }
            .getOrElse {
                log.error("Failed to fetch page $url after several retries. Exception: $it")
                throw it
            }
            .bodyAsText()
    }

    override suspend fun provideIdsToDownload(config: SearchConfig): Result<List<HentaiId>> {
        val searchResult = search(config.searchLanguage)
        if (searchResult.failedPages.isNotEmpty()) {
            return Result.failure(CannotFetchException("Failed to fetch pages: ${searchResult.failedPages}"))
        }
        return Result.success(searchResult.ids.sortedBy { it.id })
    }
}

private data class ScrapResult(
    val ids: List<HentaiId>,
    val totalPages: Int?,
)
