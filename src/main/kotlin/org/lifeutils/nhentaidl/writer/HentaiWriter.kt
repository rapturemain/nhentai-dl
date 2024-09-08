package org.lifeutils.nhentaidl.writer

import org.lifeutils.nhentaidl.model.HentaiInfo
import org.lifeutils.nhentaidl.model.HentaiId
import java.io.InputStream

interface HentaiWriter<Meta : HentaiWriterMeta> {
    fun getWriterMeta(hentaiInfo: HentaiInfo): Meta

    fun writeHentaiInfo(meta: Meta, hentaiInfo: HentaiInfo): Result<Unit>

    fun writeImage(meta: Meta, name: String, imageInputStream: InputStream): Result<Unit>
}

interface HentaiWriterMeta {
    val hentaiId: HentaiId
}