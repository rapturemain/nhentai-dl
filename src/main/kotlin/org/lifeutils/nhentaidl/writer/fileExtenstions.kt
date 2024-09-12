package org.lifeutils.nhentaidl.writer

import java.io.File

fun File.isTraversal(containingDirectory: File) = !canonicalPath.startsWith(containingDirectory.canonicalPath)

private val invalidFileNameCharacters = Regex("[:\\\\/*\"?|<>']")

fun String.toValidFileName() = replace(invalidFileNameCharacters, " ")