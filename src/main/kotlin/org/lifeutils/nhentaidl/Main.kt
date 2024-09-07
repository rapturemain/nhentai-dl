package org.lifeutils.nhentaidl

import org.lifeutils.nhentaidl.config.AppHeaderConfig
import org.lifeutils.nhentaidl.config.Header
import org.lifeutils.nhentaidl.scraper.Language
import org.lifeutils.nhentaidl.scraper.SearchScraper
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

    val searchScraper = SearchScraper(httpClient, appHeaderConfig)

    val ids = searchScraper.search(Language.ENGLISH)

    val file = File("searchResult.txt")

    file.writeText(ids.ids.map { it.id }.joinToString("\n"))

    println("Total count: ${ids.totalCount}")
    println("Failed pages: ${ids.failedPages.size}")
}