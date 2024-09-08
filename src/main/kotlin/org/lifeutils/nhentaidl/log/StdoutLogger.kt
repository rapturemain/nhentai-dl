package org.lifeutils.nhentaidl.log

import java.io.Closeable
import java.lang.AutoCloseable
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.LinkedBlockingQueue

class StdoutLogger : Logger, AutoCloseable, Closeable {

    private val messageQueue = LinkedBlockingQueue<LogEntry>()

    private val workerThread = Thread {
        while (!Thread.interrupted()) {
            try {
                printMessage(messageQueue.take())
            } catch (e: InterruptedException) {
                messageQueue.forEach(::printMessage)
                break
            }
        }
    }
        .apply {
            name = "Logger"
        }

    init {
        workerThread.start()
    }

    override fun log(message: String) {
        val timestamp = System.currentTimeMillis()
        messageQueue.add(
            LogEntry(
                message = message,
                timestampMillis = timestamp
            )
        )
    }

    override fun close() {
        workerThread.interrupt()
    }
}

private data class LogEntry(
    val message: String,
    val timestampMillis: Long
)

private val DEFAULT_ZONE = ZoneOffset.systemDefault()

private fun printMessage(entry: LogEntry) {
    val time = Instant.ofEpochMilli(entry.timestampMillis)
        .atZone(DEFAULT_ZONE)
        .toLocalDateTime()
    println("[$time] ${entry.message}")
}
