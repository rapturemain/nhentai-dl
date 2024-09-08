package org.lifeutils.nhentaidl.scraper

import org.jsoup.Jsoup
import org.lifeutils.nhentaidl.config.AppHeaderConfig
import org.lifeutils.nhentaidl.model.HentaiId
import org.lifeutils.nhentaidl.model.Language
import org.lifeutils.nhentaidl.log.Logger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

private const val BASE_URL_LANGUAGE = "https://nhentai.net/language/%language%/"

private const val PAGE_FETCH_RETRY_COUNT = 5

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
    private val headerConfig: AppHeaderConfig,
    private val log: Logger,
) {

    fun search(language: Language, pages: IntRange = 1..<Int.MAX_VALUE): SearchResult {
        return fetchPages(BASE_URL_LANGUAGE.replace("%language%", language.searchParam), pages)
    }

    private fun fetchPages(baseUrl: String, pages: IntRange): SearchResult {
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

            waitTillNextRequest()
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

    private fun fetchPage(baseUrl: String, page: Int, totalPages: Int?): String {
        val uri = makeUri(baseUrl, page)

        val request = HttpRequest.newBuilder(uri)
            .GET()
            .addHeaders(headerConfig)
            .build()

        var lastException: Exception? = null
        repeat(PAGE_FETCH_RETRY_COUNT) {
            try {
                log("Fetching page $uri" + if (totalPages != null) " of $totalPages" else "")

                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                if (!response.isSucceeded()) {
                    throw CannotFetchException(
                        message = "Cannot fetch [$uri]. Response code: ${response.statusCode()}. Body: ${response.body()}"
                    )
                }
                return response.body()
            } catch (e: Exception) {
                log("Failed to fetch page $uri. Retrying...\n Failed page exception: $e")
                lastException = e
                waitTillNextRequest()
            }
        }

        log("Failed to fetch page $uri after $PAGE_FETCH_RETRY_COUNT retries. Exception: $lastException")

        throw if (lastException is CannotFetchException) {
            lastException!!
        } else {
            CannotFetchException(
                message = "Cannot fetch [$uri]",
                cause = lastException
            )
        }
    }
}

private fun makeUri(baseUrl: String, page: Int): URI {
    return URI.create("$baseUrl?page=$page")
}

data class ScrapResult(
    val ids: List<HentaiId>,
    val totalPages: Int?
)
