package org.lifeutils.nhentaidl.writer

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readFully
import org.lifeutils.nhentaidl.config.WriterConfig
import org.lifeutils.nhentaidl.model.HentaiId
import org.lifeutils.nhentaidl.model.HentaiInfo
import java.io.File

private const val METADATA_FILE_NAME = "metadata.json"

class FileHentaiWriter(
    private val writerConfig: WriterConfig,
    private val objectMapper: ObjectMapper,
) : HentaiWriter<FileHentaiWriterMeta> {

    override suspend fun getWriterMeta(hentaiInfo: HentaiInfo): Result<FileHentaiWriterMeta> {
        try {
            val saveName = "[${hentaiInfo.id.id}] ${hentaiInfo.title}".toValidFileName().trim()
            val saveDirectory = writerConfig.directory.resolve(saveName)
            if (saveDirectory.isTraversal(writerConfig.directory)) {
                return Result.failure(
                    IllegalArgumentException(
                        "Prevented directory traversal attack. " +
                                "${saveDirectory.canonicalPath} is not a subdirectory of ${writerConfig.directory.canonicalPath}"
                    )
                )
            }
            return Result.success(FileHentaiWriterMeta(hentaiInfo.id, saveDirectory))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun writeImage(meta: FileHentaiWriterMeta, name: String, size: Long, byteReadChannel: ByteReadChannel): Result<Unit> {
        val escapedName = name.replace(Regex("[^\\w.]+"), "")

        try {
            val file = meta.directory.resolve(escapedName)
            file.parentFile.mkdirs()

            val bytes = ByteArray(size.toInt())
            byteReadChannel.readFully(bytes)

            file.outputStream().use { outputStream ->
                outputStream.write(bytes)
            }

            return Result.success(Unit)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun writeHentaiInfo(meta: FileHentaiWriterMeta, hentaiInfo: HentaiInfo): Result<Unit> {
        try {
            val file = meta.directory.resolve(METADATA_FILE_NAME)
            file.parentFile.mkdirs()

            objectMapper.writeValue(file, hentaiInfo)

            return Result.success(Unit)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}

data class FileHentaiWriterMeta(
    override val hentaiId: HentaiId,
    val directory: File,
) : HentaiWriterMeta
