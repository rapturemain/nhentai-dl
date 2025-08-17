package org.lifeutils.nhentaidl.config

import java.io.File

data class WriterConfig(
    val directory: File,
    val flushBufferSize: Int,
) {
    fun verify() {
        if (directory.exists() && directory.isFile) {
            throw IllegalArgumentException("Directory ${directory.absolutePath} is a file")
        }

        if (flushBufferSize <= 0) {
            throw IllegalArgumentException("Flush buffer size must be greater than 0")
        }
    }
}