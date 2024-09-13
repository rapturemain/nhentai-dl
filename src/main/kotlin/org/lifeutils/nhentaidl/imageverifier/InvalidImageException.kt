package org.lifeutils.nhentaidl.imageverifier

import java.io.File

class InvalidImageException(val file: File?, message: String) : RuntimeException(
    "Invalid image: ${file?.absoluteFile ?: "[InMemoryContent]"}. $message"
)
