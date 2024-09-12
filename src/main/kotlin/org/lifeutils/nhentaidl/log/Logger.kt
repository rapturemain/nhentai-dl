package org.lifeutils.nhentaidl.log

interface Logger {
    fun log(message: String)

    operator fun invoke(message: String) = log(message)
}
