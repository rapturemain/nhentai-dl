package org.lifeutils.nhentaidl.writer

import io.ktor.utils.io.ByteReadChannel
import org.lifeutils.nhentaidl.model.HentaiInfo
import org.lifeutils.nhentaidl.model.HentaiId

interface HentaiWriter<Meta : HentaiWriterMeta> {
    suspend fun getWriterMeta(hentaiInfo: HentaiInfo): Result<Meta>

    suspend fun writeHentaiInfo(meta: Meta, hentaiInfo: HentaiInfo): Result<Unit>

    suspend fun writeImage(meta: Meta, name: String, size: Long, byteReadChannel: ByteReadChannel): Result<Unit>

    suspend fun finish(meta: Meta): Result<Unit> = Result.success(Unit)

    fun flush() = Unit
}

interface HentaiWriterMeta {
    val hentaiId: HentaiId
}
