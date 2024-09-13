package org.lifeutils.nhentaidl.imageverifier

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 'P'.code.toByte())
private val JPEG_SIGNATURE = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
private val GIF_SIGNATURES = arrayOf(
    byteArrayOf('G'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), '8'.code.toByte(), '7'.code.toByte(), 'a'.code.toByte()),
    byteArrayOf('G'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), '8'.code.toByte(), '9'.code.toByte(), 'a'.code.toByte()),
)
private val SIGNATURE_LENGTH = listOf(
    PNG_SIGNATURE,
    JPEG_SIGNATURE,
    *GIF_SIGNATURES
)
    .maxOf { it.size }

class GenericImageVerifier(
    private val pngImageVerifier: ImageVerifier,
    private val jpgImageVerifier: ImageVerifier,
    private val gifImageVerifier: ImageVerifier,
) : ImageVerifier {
    override suspend fun verify(context: ImageVerifierContext): Result<Unit> {
        val verifier = selectVerifier(context).getOrElse {
            return Result.failure(it)
        }
        return verifier.verify(context)
    }

    override suspend fun verify(content: ByteArray): Result<Unit> {
        val verifier = selectVerifier(content).getOrElse {
            return Result.failure(it)
        }
        return verifier.verify(content)
    }

    private suspend fun selectVerifier(context: ImageVerifierContext): Result<ImageVerifier> {
        return selectVerifierInner(
            file = context.file,
            getLength = { context.randomAccess.length() },
            readSignature = {
                val signature = ByteArray(SIGNATURE_LENGTH)
                context.randomAccess.readFully(signature)
                signature
            }
        )
    }

    private suspend fun selectVerifier(content: ByteArray): Result<ImageVerifier> {
        return selectVerifierInner(
            getLength = { content.size.toLong() },
            readSignature = { content.copyOfRange(0, SIGNATURE_LENGTH) }
        )
    }

    private suspend fun selectVerifierInner(
        file: File? = null,
        getLength: suspend () -> Long,
        readSignature: suspend () -> ByteArray,
    ): Result<ImageVerifier> {
        val length = withContext(Dispatchers.IO) {
            getLength()
        }

        if (length < SIGNATURE_LENGTH) {
            return Result.failure(InvalidImageException(file, "File is too short"))
        }

        val signature = withContext(Dispatchers.IO) {
            readSignature()
        }

        return when {
            signature.startsWith(PNG_SIGNATURE) -> Result.success(pngImageVerifier)
            signature.startsWith(JPEG_SIGNATURE) -> Result.success(jpgImageVerifier)
            GIF_SIGNATURES.any { signature.startsWith(it) } -> Result.success(gifImageVerifier)
            else -> Result.failure(CannotCheckException(file, "Unsupported image format"))
        }
    }
}

private fun ByteArray.startsWith(other: ByteArray): Boolean {
    if (size < other.size) {
        return false
    }
    for (i in other.indices) {
        if (this[i] != other[i]) {
            return false
        }
    }
    return true
}
