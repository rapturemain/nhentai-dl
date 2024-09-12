package org.lifeutils.nhentaidl.config

data class HttpConfig(
    val headers: List<Header>,
    val requestDelayInMillis: Long,
    val concurrencyLevelImage: Int,
    val concurrencyLevelTitle: Int,
)

data class Header(
    val name: String,
    val value: String,
)
