package org.lifeutils.nhentaidl.imageverifier

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val EXPECTED_TERMINATOR = byteArrayOf(0xFF.toByte(), 0xD9.toByte())
private const val TERMINATOR_LENGTH_TO_SEEK_FOR = 4

private fun ByteArray.contains(other: ByteArray): Boolean {
    if (other.isEmpty()) {
        return true
    }

    if (this.size < other.size) {
        return false
    }

    for (i in 0..this.size - other.size) {
        var found = true

        for (j in other.indices) {
            if (this[i + j] != other[j]) {
                found = false
                break
            }
        }

        if (found) {
            return true
        }
    }

    return false
}

class JpgImageVerifier(
    private val jpgBlankLineImageVerifier: BlankLineImageVerifier? = null
) : ImageVerifier {
    override suspend fun verify(context: ImageVerifierContext): Result<Unit> {
        return runCatching{
            val terminator = ByteArray(TERMINATOR_LENGTH_TO_SEEK_FOR)
            withContext(Dispatchers.IO) {
                context.randomAccess.seek(context.randomAccess.length() - TERMINATOR_LENGTH_TO_SEEK_FOR)
                context.randomAccess.readFully(terminator)
            }

            if (isFileTerminatorOk(terminator)) {
                return Result.success(Unit)
            }

            return jpgBlankLineImageVerifier?.verify(context) ?: Result.success(Unit)
        }
    }

    override suspend fun verify(content: ByteArray): Result<Unit> {
        return runCatching {
            val terminator = content.copyOfRange(content.size - TERMINATOR_LENGTH_TO_SEEK_FOR, content.size)

            if (isFileTerminatorOk(terminator)) {
                return Result.success(Unit)
            }

            return jpgBlankLineImageVerifier?.verify(content) ?: Result.success(Unit)
        }
    }

    private fun isFileTerminatorOk(terminator: ByteArray): Boolean {
        return terminator.contains(EXPECTED_TERMINATOR)
    }
}
