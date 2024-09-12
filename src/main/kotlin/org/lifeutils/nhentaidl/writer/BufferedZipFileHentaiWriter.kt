package org.lifeutils.nhentaidl.writer

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readFully
import org.lifeutils.nhentaidl.config.WriterConfig
import org.lifeutils.nhentaidl.log.Logger
import org.lifeutils.nhentaidl.model.HentaiId
import org.lifeutils.nhentaidl.model.HentaiInfo
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val METADATA_FILE_NAME = "metadata.json"
private const val MAX_FILES_TO_SAVE_IN_MEMORY = 100

class BufferedZipFileHentaiWriter(
    private val writerConfig: WriterConfig,
    private val objectMapper: ObjectMapper,
    private val log: Logger,
) : HentaiWriter<BufferedZipFileHentaiWriterMeta> {

    private val filesToSaveBuffer = ConcurrentLinkedDeque<InMemoryFile>()

    override suspend fun getWriterMeta(hentaiInfo: HentaiInfo): Result<BufferedZipFileHentaiWriterMeta> {
        try {
            val saveName = "[${hentaiInfo.id.id}] ${hentaiInfo.title}".toValidFileName().trim()
            val zipFile = writerConfig.directory.resolve("$saveName.zip".trim())
            if (zipFile.isTraversal(writerConfig.directory)) {
                return Result.failure(
                    IllegalArgumentException(
                        "Prevented directory traversal attack. " +
                                "${zipFile.canonicalPath} is not a subdirectory of ${writerConfig.directory.canonicalPath}"
                    )
                )
            }
            return Result.success(BufferedZipFileHentaiWriterMeta(hentaiInfo.id, zipFile))
        } catch (e: Exception) {
            return Result.failure(e)
        }
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

            if (filesToSaveBuffer.size > MAX_FILES_TO_SAVE_IN_MEMORY) {
                flush()
            }

            return Result.success(Unit)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun writeImage(meta: BufferedZipFileHentaiWriterMeta, name: String, size: Long, byteReadChannel: ByteReadChannel): Result<Unit> {
        val escapedName = name.replace(Regex("[^\\w.]+"), "")
        val byteArray = ByteArray(size.toInt())

        try {
            byteReadChannel.readFully(byteArray)
        } catch (e: Exception) {
            return Result.failure(e)
        }

        meta.files.add(InMemoryContents(escapedName, byteArray))

        return Result.success(Unit)
    }

    override suspend fun writeHentaiInfo(meta: BufferedZipFileHentaiWriterMeta, hentaiInfo: HentaiInfo): Result<Unit> {
        try {
            val bytes = objectMapper.writeValueAsBytes(hentaiInfo)

            meta.files.add(InMemoryContents(METADATA_FILE_NAME, bytes))

            return Result.success(Unit)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override fun flush() {
        log("Flushing files to disk")

        var count = 0
        var inMemFile = filesToSaveBuffer.poll()
        while (inMemFile != null) {
            count++

            inMemFile.file.parentFile.mkdirs()

            inMemFile.file.outputStream().use { outputStream ->
                outputStream.write(inMemFile.contents)
            }

            inMemFile = filesToSaveBuffer.poll()
        }

        log("Flushed $count files to disk")
    }
}

data class BufferedZipFileHentaiWriterMeta(
    override val hentaiId: HentaiId,
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
