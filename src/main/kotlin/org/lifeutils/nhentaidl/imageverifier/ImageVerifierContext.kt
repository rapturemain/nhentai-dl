package org.lifeutils.nhentaidl.imageverifier

import java.io.File
import java.io.RandomAccessFile

data class ImageVerifierContext(
    val file: File,
    val randomAccess: RandomAccessFile,
)
