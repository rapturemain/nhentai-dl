package org.lifeutils.nhentaidl.writer

import java.io.File

fun File.isTraversal(containingDirectory: File) = !canonicalPath.startsWith(containingDirectory.canonicalPath)

private val invalidFileNameCharacters = Regex("[:\\\\/*\"?|<>']")

private const val truncatedNameSuffix = "..."

private const val maxFileNameLength = 200
private const val truncatedLength = maxFileNameLength - truncatedNameSuffix.length

fun String.toValidFileName(): String {
    val filteredName = replace(invalidFileNameCharacters, " ")
    return if (filteredName.length <= maxFileNameLength) {
        filteredName
    } else {
        filteredName.substring(0, truncatedLength) + truncatedNameSuffix
    }
}
