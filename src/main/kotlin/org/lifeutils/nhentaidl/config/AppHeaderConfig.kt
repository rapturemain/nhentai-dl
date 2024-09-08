package org.lifeutils.nhentaidl.config

data class AppHeaderConfig(
    val headers: List<Header>
)

data class Header(
    val name: String,
    val value: String,
)
