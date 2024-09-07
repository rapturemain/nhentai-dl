package org.lifeutils.nhentaidl.scraper

import org.lifeutils.nhentaidl.config.AppHeaderConfig
import java.net.http.HttpRequest
import java.net.http.HttpResponse

fun HttpRequest.Builder.addHeaders(headers: AppHeaderConfig): HttpRequest.Builder {
    headers.headers.forEach {
        setHeader(it.name, it.value)
    }
    return this
}

fun HttpResponse<*>.isSucceeded() = statusCode() in 200..<300

fun waitTillNextRequest() = Thread.sleep(500L)