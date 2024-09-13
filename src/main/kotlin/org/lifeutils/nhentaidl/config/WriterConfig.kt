package org.lifeutils.nhentaidl.config

import java.io.File

data class WriterConfig(
    val directory: File,
    val flushBufferSize: Int,
)
