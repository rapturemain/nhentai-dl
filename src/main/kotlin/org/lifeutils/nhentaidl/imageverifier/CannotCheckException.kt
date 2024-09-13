package org.lifeutils.nhentaidl.imageverifier

import java.io.File

class CannotCheckException(
    val file: File?,
    message: String
) : RuntimeException(
    "Cannot check image: ${file?.absoluteFile ?: "[InMemoryContent]"}. $message"
)
