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
import org.lifeutils.nhentaidl.model.oldmetadata.LanguageV3
import java.net.URLEncoder

private const val BASE_SEARCH_URL = "https://nhentai.net/search/"

private fun buildSearchUrl(language: LanguageV3?, artist: String?): String {
    val query = StringBuilder()
    if (language != null) {
        query.append("language:${language.searchParam} ")
    }
    if (artist != null) {
        query.append("artist:${artist} ")
    }
    val urlEncodedQuery = URLEncoder.encode(query.toString().trim(), Charsets.UTF_8)
    return "${BASE_SEARCH_URL}?q=${urlEncodedQuery}"
}

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

    suspend fun search(
        language: LanguageV3?,
        artist: String?,
        pages: IntRange = 1..<Int.MAX_VALUE,
        limit: Int,
    ): SearchResult {
        val searchUrl = buildSearchUrl(language, artist)
        return fetchPages(searchUrl, pages, limit)
    }

    private suspend fun fetchPages(baseUrl: String, pages: IntRange, limit: Int): SearchResult {
        val allIds = mutableSetOf<HentaiId>()
        val failedPages = mutableListOf<String>()

        var totalPages: Int? = null

        for (page in pages) {
            if (allIds.size >= limit) {
                break
            }
            if (page > (totalPages ?: Int.MAX_VALUE)) {
                break
            }

            try {
                val content = fetchPage(baseUrl, page, totalPages)
                val scrapResult = scrapPage(content)
                if (scrapResult.ids.isEmpty()) {
                    break // no more pages
                }

                totalPages = scrapResult.totalPages ?: totalPages
                allIds += scrapResult.ids
            } catch (_: CannotFetchException) {
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
        val url = if (baseUrl.contains("?")) {
            "${baseUrl}&page=$page"
        } else {
            "${baseUrl}?page=$page"
        }

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
        val searchResult = search(
            language = config.searchLanguage,
            artist = config.searchArtist,
            limit = config.countLimit
        )
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
