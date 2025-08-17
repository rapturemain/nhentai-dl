package org.lifeutils.nhentaidl.config

import java.io.File

data class MigrationConfig(
    val sourceDir: File,
    val removeSrc: Boolean,
) {
    fun verify() {
        if (!sourceDir.exists()) {
            throw IllegalArgumentException("Source directory ${sourceDir.absolutePath} does not exist")
        }

        if (!sourceDir.isDirectory) {
            throw IllegalArgumentException("Source directory ${sourceDir.absolutePath} is not a directory")
        }
    }
}
