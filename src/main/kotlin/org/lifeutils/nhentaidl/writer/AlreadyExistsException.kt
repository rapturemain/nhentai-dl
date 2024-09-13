package org.lifeutils.nhentaidl.writer

import java.io.File

class AlreadyExistsException(file: File) : RuntimeException(
    "File already exists: ${file.absoluteFile}"
)
