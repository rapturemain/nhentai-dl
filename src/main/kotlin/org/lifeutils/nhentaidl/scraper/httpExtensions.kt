package org.lifeutils.nhentaidl.scraper

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.HttpStatusCode
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

val noRetryStatusCodes = setOf(
    HttpStatusCode.NotFound,
)

suspend inline fun retryHttpRequest(
    times: Int = 5,
    delayMillis: Long,
    block: () -> HttpResponse
): Result<HttpResponse> {
    var lastException: Exception? = null
    var url = ""
    for (retry in 0..<times) {
        try {
            val response = block()

            if (url.isEmpty()) {
                url = response.request.url.toString()
            }

            if (response.status in noRetryStatusCodes) {
                return Result.failure(
                    CannotFetchException(
                        message = "Cannot fetch [${url}]. ${response.log()}"
                    )
                )
            }

            if (!response.status.isSuccess()) {
                throw CannotFetchException(
                    message = "Cannot fetch [${url}]. ${response.log()}"
                )
            }

            return Result.success(response)
        } catch (e: Exception) {
            lastException = e
            waitTillNextRequest(delayMillis * (retry * retry + 1))
        }
    }

    return Result.failure(
        if (lastException is CannotFetchException) {
            lastException
        } else {
            CannotFetchException(
                message = "Cannot fetch${if (url.isNotEmpty()) " [$url]" else ""}.",
                cause = lastException
            )
        }
    )
}