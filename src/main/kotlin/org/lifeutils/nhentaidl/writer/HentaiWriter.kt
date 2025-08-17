package org.lifeutils.nhentaidl.writer

import org.lifeutils.nhentaidl.model.AppMetadata
import org.lifeutils.nhentaidl.model.HentaiInfo
import org.lifeutils.nhentaidl.model.HentaiId

interface HentaiWriter<Meta : HentaiWriterMeta> {
    suspend fun getWriterMeta(
        hentaiInfo: HentaiInfo,
        allowRewrite: Boolean = false,
        desiredName: String? = null
    ): Result<Meta>

    suspend fun writeHentaiInfo(meta: Meta, hentaiInfo: HentaiInfo): Result<Unit>

    suspend fun writeImage(meta: Meta, name: String, contents: ByteArray): Result<Unit>

    suspend fun finish(meta: Meta): Result<Unit>

    /**
     * It is called whenever [writeHentaiInfo] or [writeImage] fails.
     * You can call it by yourself to abort the writing process and remove all the written data.
     */
    suspend fun abort(meta: Meta): Result<Unit>

    suspend fun flush() = Unit
}

interface HentaiWriterMeta {
    val hentaiId: HentaiId
    val appMetadata: AppMetadata
}
