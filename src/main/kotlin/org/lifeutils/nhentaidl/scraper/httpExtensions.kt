package org.lifeutils.nhentaidl.scraper

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import org.lifeutils.nhentaidl.config.HttpConfig

fun HttpRequestBuilder.addHeaders(headers: HttpConfig) {
    headers.headers.forEach {
        header(it.name, it.value)
    }
}

suspend fun HttpResponse.log() = "Response code: ${status}. Response body: ${bodyAsText()}"

suspend fun waitTillNextRequest(delayMillis: Long = 100) {
    delay(delayMillis)
}

suspend inline fun retryHttpRequest(
    times: Int = 5,
    delayMillis: Long,
    block: () -> HttpResponse
): Result<HttpResponse> {
    var lastException: Exception? = null
    repeat(times) {
        try {
            val response = block()

            if (!response.status.isSuccess()) {
                throw CannotFetchException(
                    message = "Cannot fetch [${response.request.url}]. ${response.log()}"
                )
            }

            return Result.success(response)
        } catch (e: Exception) {
            lastException = e
            waitTillNextRequest(delayMillis)
        }
    }
    return Result.failure(
        if (lastException is CannotFetchException) {
            lastException!!
        } else {
            CannotFetchException(
                message = "Cannot fetch",
                cause = lastException
            )
        }
    )
}