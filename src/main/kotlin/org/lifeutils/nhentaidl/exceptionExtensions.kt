package org.lifeutils.nhentaidl

fun Throwable?.getMessageWithCause(): String {
    if (this == null) {
        return ""
    }
    return "${this::class.simpleName}: ${this.message}." +
            if (this.cause != null) " Caused by ${this.cause.getMessageWithCause()}" else ""
}
