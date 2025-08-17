package org.lifeutils.nhentaidl.writer

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.sync.Mutex
import org.lifeutils.nhentaidl.config.WriterConfig
import org.lifeutils.nhentaidl.getMessageWithCause
import org.lifeutils.nhentaidl.imageverifier.ImageVerifier
import org.lifeutils.nhentaidl.log.Logger
import org.lifeutils.nhentaidl.model.AppMetadata
import org.lifeutils.nhentaidl.model.HentaiId
import org.lifeutils.nhentaidl.model.HentaiInfo
import org.lifeutils.nhentaidl.model.ImageVerificationStatus
import org.lifeutils.nhentaidl.model.Metadata
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BufferedZipFileHentaiWriter(
    private val writerConfig: WriterConfig,
    private val objectMapper: ObjectMapper,
    private val log: Logger,
    private val imageVerifier: ImageVerifier? = null,
) : HentaiWriter<BufferedZipFileHentaiWriterMeta> {

    private val filesToSaveBuffer = ConcurrentLinkedDeque<InMemoryFile>()
    private val flushLock = Mutex()

    override suspend fun getWriterMeta(
        hentaiInfo: HentaiInfo,
        allowRewrite: Boolean,
        desiredName: String?,
    ): Result<BufferedZipFileHentaiWriterMeta> {
        try {
            val fixedDesiredName = desiredName?.toValidFileName()
                ?.addExtensionIfNotPresent(".zip")

            val saveName =
                fixedDesiredName ?: ("[${hentaiInfo.id.id}] ${hentaiInfo.title}".toValidFileName().trim() + ".zip")

            val zipFile = writerConfig.directory.resolve(saveName)

            if (zipFile.isTraversal(writerConfig.directory)) {
                return Result.failure(
                    IllegalArgumentException(
                        "Prevented directory traversal attack. " +
                                "${zipFile.canonicalPath} is not a subdirectory of ${writerConfig.directory.canonicalPath}"
                    )
                )
            }

            if (!allowRewrite && zipFile.exists()) {
                return Result.failure(AlreadyExistsException(zipFile))
            }

            val appMetadata = AppMetadata(
                imageVerificationStatus = when (imageVerifier) {
                    null -> ImageVerificationStatus.NOT_VERIFIED
                    // do not cover VERIFICATION_FAILED, since we don't save failed doujinshi
                    else -> ImageVerificationStatus.VERIFICATION_PASSED
                }
            )

            return Result.success(
                BufferedZipFileHentaiWriterMeta(
                    hentaiId = hentaiInfo.id,
                    appMetadata = appMetadata,
                    zipFile = zipFile,
                )
            )
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun abort(meta: BufferedZipFileHentaiWriterMeta): Result<Unit> {
        // since we're buffering everything in a metadata object itself, it's enough just to let GC clean it up
        return Result.success(Unit)
    }

    override suspend fun finish(meta: BufferedZipFileHentaiWriterMeta): Result<Unit> {
        try {
            val buffer = ByteArrayOutputStream()

            ZipOutputStream(buffer).use { zos ->
                for (file in meta.files) {
                    zos.putNextEntry(ZipEntry(file.name))
                    zos.write(file.contents)
                    zos.closeEntry()
                }
            }

            filesToSaveBuffer.add(InMemoryFile(meta.zipFile, buffer.toByteArray()))

            if (filesToSaveBuffer.size > writerConfig.flushBufferSize) {
                flush()
            }

            return Result.success(Unit)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun writeImage(
        meta: BufferedZipFileHentaiWriterMeta,
        name: String,
        contents: ByteArray,
    ): Result<Unit> {
        val escapedName = name.replace(Regex("[^\\w.]+"), "")

        imageVerifier?.verify(contents)?.onFailure {
            log.error("Image verification failed: ${meta.hentaiId.id}:$name. ${it.getMessageWithCause()}")
            return Result.failure(it)
        }

        meta.files.add(InMemoryContents(escapedName, contents))

        return Result.success(Unit)
    }

    override suspend fun writeHentaiInfo(meta: BufferedZipFileHentaiWriterMeta, hentaiInfo: HentaiInfo): Result<Unit> {
        try {
            val metadata = Metadata(
                hentaiInfo = hentaiInfo,
                appMetadata = meta.appMetadata
            )

            val bytes = objectMapper.writeValueAsBytes(metadata)

            meta.files.add(InMemoryContents(METADATA_FILE_NAME, bytes))

            return Result.success(Unit)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun flush() {
        if (filesToSaveBuffer.size > writerConfig.flushBufferSize * 4) {
            log.info("Writer buffer is too large. Flushing...")
            flushInternal()
            return
        }

        if (!flushLock.tryLock()) {
            return
        }

        try {
            flushInternal()
        } finally {
            flushLock.unlock()
        }
    }

    private fun flushInternal() {
        log("Flushing files to disk")

        var count = 0
        for (inMemFile in generateSequence { filesToSaveBuffer.poll() }) {
            count++

            inMemFile.file.parentFile.mkdirs()

            inMemFile.file.outputStream().use { outputStream ->
                outputStream.write(inMemFile.contents)
            }
        }

        log("Flushed $count files to disk")
    }
}

data class BufferedZipFileHentaiWriterMeta(
    override val hentaiId: HentaiId,
    override val appMetadata: AppMetadata,
    val zipFile: File,
    val files: MutableList<InMemoryContents> = mutableListOf(),
) : HentaiWriterMeta

data class InMemoryContents(
    val name: String,
    val contents: ByteArray,
)

private data class InMemoryFile(
    val file: File,
    val contents: ByteArray,
)
