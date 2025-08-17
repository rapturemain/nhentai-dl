package org.lifeutils.nhentaidl.config

data class HttpConfig(
    val headers: List<Header>,
    val requestDelayInMillis: Long,
    val concurrencyLevelImage: Int,
    val concurrencyLevelTitle: Int,
) {
    fun verify() {
        if ("User-Agent" !in headers.map { it.name }) {
            throw IllegalArgumentException("User-Agent header is required")
        }

        if ("Cookie" !in headers.map { it.name }) {
            throw IllegalArgumentException("Cookie header is required")
        }

        if (requestDelayInMillis < 0) {
            throw IllegalArgumentException("Request delay must be non-negative")
        }

        if (concurrencyLevelImage <= 0) {
            throw IllegalArgumentException("Concurrency level for image downloads must be positive")
        }

        if (concurrencyLevelTitle <= 0) {
            throw IllegalArgumentException("Concurrency level for title downloads must be positive")
        }
    }
}

data class Header(
    val name: String,
    val value: String,
)
