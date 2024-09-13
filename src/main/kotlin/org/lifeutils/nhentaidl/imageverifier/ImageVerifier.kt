package org.lifeutils.nhentaidl.imageverifier

interface ImageVerifier {
    suspend fun verify(context: ImageVerifierContext): Result<Unit>

    suspend fun verify(content: ByteArray): Result<Unit>
}
