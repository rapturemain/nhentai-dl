package org.lifeutils.nhentaidl.writer

import com.fasterxml.jackson.databind.ObjectMapper
import org.lifeutils.nhentaidl.config.WriterConfig
import org.lifeutils.nhentaidl.getMessageWithCause
import org.lifeutils.nhentaidl.imageverifier.ImageVerifier
import org.lifeutils.nhentaidl.log.Logger
import org.lifeutils.nhentaidl.model.AppMetadata
import org.lifeutils.nhentaidl.model.HentaiId
import org.lifeutils.nhentaidl.model.HentaiInfo
import org.lifeutils.nhentaidl.model.ImageVerificationStatus
import org.lifeutils.nhentaidl.model.Metadata
import java.io.File

class FileHentaiWriter(
    private val writerConfig: WriterConfig,
    private val objectMapper: ObjectMapper,
    private val log: Logger,
    private val imageVerifier: ImageVerifier? = null,
) : HentaiWriter<FileHentaiWriterMeta> {

    override suspend fun getWriterMeta(
        hentaiInfo: HentaiInfo,
        allowRewrite: Boolean,
        desiredName: String?,
    ): Result<FileHentaiWriterMeta> {
        try {
            if (desiredName != null && !desiredName.isValidFileName()) {
                return Result.failure(IllegalArgumentException("Desired name is not a valid file name"))
            }

            val saveName = desiredName ?: "[${hentaiInfo.id.id}] ${hentaiInfo.title}".toValidFileName().trim()
            val saveDirectory = writerConfig.directory.resolve(saveName)
            if (saveDirectory.isTraversal(writerConfig.directory)) {
                return Result.failure(
                    IllegalArgumentException(
                        "Prevented directory traversal attack. " +
                                "${saveDirectory.canonicalPath} is not a subdirectory of ${writerConfig.directory.canonicalPath}"
                    )
                )
            }

            if (saveDirectory.exists() && !saveDirectory.isDirectory) {
                return Result.failure(IllegalArgumentException("${saveDirectory.absolutePath} is already exists and it is not a directory"))
            }

            if (saveDirectory.exists() && !allowRewrite) {
                return Result.failure(AlreadyExistsException(saveDirectory))
            } else {
                saveDirectory.deleteRecursively()
                saveDirectory.mkdirs()
            }

            val appMetadata = AppMetadata(
                imageVerificationStatus = when (imageVerifier) {
                    null -> ImageVerificationStatus.NOT_VERIFIED
                    // do not cover VERIFICATION_FAILED, since we don't save failed doujinshi
                    else -> ImageVerificationStatus.VERIFICATION_PASSED
                }
            )

            return Result.success(FileHentaiWriterMeta(hentaiInfo.id, appMetadata, saveDirectory))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun abort(meta: FileHentaiWriterMeta): Result<Unit> {
        try {
            meta.directory.deleteRecursively()
            return Result.success(Unit)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun finish(meta: FileHentaiWriterMeta): Result<Unit> {
        // since we're writing files to disk immediately, there's nothing to do here
        return Result.success(Unit)
    }

    override suspend fun writeImage(meta: FileHentaiWriterMeta, name: String, contents: ByteArray): Result<Unit> {
        val escapedName = name.replace(Regex("[^\\w.]+"), "")

        try {
            val file = meta.directory.resolve(escapedName)

            imageVerifier?.verify(contents,)?.onFailure {
                log.error("Image verification failed: ${meta.hentaiId.id}:$name. ${it.getMessageWithCause()}")
                return Result.failure(it)
            }

            file.outputStream().use { outputStream ->
                outputStream.write(contents)
            }

            return Result.success(Unit)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun writeHentaiInfo(meta: FileHentaiWriterMeta, hentaiInfo: HentaiInfo): Result<Unit> {
        try {
            val file = meta.directory.resolve(METADATA_FILE_NAME)

            val metadata = Metadata(
                hentaiInfo = hentaiInfo,
                appMetadata = meta.appMetadata
            )

            objectMapper.writeValue(file, metadata)

            return Result.success(Unit)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}

data class FileHentaiWriterMeta(
    override val hentaiId: HentaiId,
    override val appMetadata: AppMetadata,
    val directory: File,
) : HentaiWriterMeta
