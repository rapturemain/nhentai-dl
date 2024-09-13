package org.lifeutils.nhentaidl.log

interface Logger {
    fun info(message: String)

    fun error(message: String)

    operator fun invoke(message: String) = info(message)
}
