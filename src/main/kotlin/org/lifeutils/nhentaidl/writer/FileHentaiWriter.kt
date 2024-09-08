package org.lifeutils.nhentaidl.writer

import com.fasterxml.jackson.databind.ObjectMapper
import org.lifeutils.nhentaidl.config.WriterConfig
import org.lifeutils.nhentaidl.model.HentaiInfo
import org.lifeutils.nhentaidl.model.HentaiId
import java.io.File
import java.io.InputStream

private const val METADATA_FILE_NAME = "metadata.json"

class FileHentaiWriter(
    private val writerConfig: WriterConfig,
    private val objectMapper: ObjectMapper,
) : HentaiWriter<FileHentaiWriterMeta> {

    override fun getWriterMeta(hentaiInfo: HentaiInfo): FileHentaiWriterMeta {
        val saveDirectory = writerConfig.directory.resolve("[${hentaiInfo.id.id}] ${hentaiInfo.title}".trim())
        return FileHentaiWriterMeta(hentaiInfo.id, saveDirectory)
    }

    override fun writeImage(meta: FileHentaiWriterMeta, name: String, imageInputStream: InputStream): Result<Unit> {
        val escapedName = name.replace(Regex("[^\\w.]+"), "")

        try {
            val file = meta.directory.resolve(escapedName)
            file.parentFile.mkdirs()

            file.outputStream().use { outputStream ->
                imageInputStream.transferTo(outputStream)
            }

            return Result.success(Unit)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override fun writeHentaiInfo(meta: FileHentaiWriterMeta, hentaiInfo: HentaiInfo): Result<Unit> {
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
