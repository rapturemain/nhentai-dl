package org.lifeutils.nhentaidl.scraper

class CannotFetchException(
    message: String? = null,
    cause: Exception? = null
) : RuntimeException(message, cause)
