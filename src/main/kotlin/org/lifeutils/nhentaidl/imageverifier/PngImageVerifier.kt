package org.lifeutils.nhentaidl.imageverifier

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val EXPECTED_TERMINATOR = byteArrayOf(0xAE.toByte(), 0x42, 0x60, 0x82.toByte())

class PngImageVerifier(
    private val pngBlankLineVerifier: BlankLineImageVerifier? = null,
) : ImageVerifier {
    override suspend fun verify(context: ImageVerifierContext): Result<Unit> {
        return runCatching {
            val terminator = ByteArray(EXPECTED_TERMINATOR.size)
            withContext(Dispatchers.IO) {
                context.randomAccess.seek(context.randomAccess.length() - EXPECTED_TERMINATOR.size)
                context.randomAccess.readFully(terminator)
            }

            if (isFileTerminatorOk(terminator)) {
                return Result.success(Unit)
            }

            return pngBlankLineVerifier?.verify(context) ?: Result.success(Unit)
        }
    }

    override suspend fun verify(content: ByteArray): Result<Unit> {
        return runCatching {
            val terminator = content.copyOfRange(content.size - EXPECTED_TERMINATOR.size, content.size)

            if (isFileTerminatorOk(terminator)) {
                return Result.success(Unit)
            }

            return pngBlankLineVerifier?.verify(content) ?: Result.success(Unit)
        }
    }

    private fun isFileTerminatorOk(terminator: ByteArray): Boolean {
        return terminator.contentEquals(EXPECTED_TERMINATOR)
    }
}
