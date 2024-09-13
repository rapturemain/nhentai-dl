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
            isDaemon = true
        }

    init {
        workerThread.start()
    }

    override fun info(message: String) {
        val timestamp = System.currentTimeMillis()
        messageQueue.add(
            LogEntry(
                message = message,
                timestampMillis = timestamp,
                type = LogType.INFO,
            )
        )
    }

    override fun error(message: String) {
        val timestamp = System.currentTimeMillis()
        messageQueue.add(
            LogEntry(
                message = message,
                timestampMillis = timestamp,
                type = LogType.ERROR,
            )
        )
    }

    override fun close() {
        workerThread.interrupt()
    }
}

private data class LogEntry(
    val message: String,
    val timestampMillis: Long,
    val type: LogType
)

private enum class LogType {
    INFO,
    ERROR,
}

private val DEFAULT_ZONE = ZoneOffset.systemDefault()

private fun printMessage(entry: LogEntry) {
    val time = Instant.ofEpochMilli(entry.timestampMillis)
        .atZone(DEFAULT_ZONE)
        .toLocalDateTime()
    val message = "[$time] ${entry.message}"
    when (entry.type) {
        LogType.INFO -> println(message)
        LogType.ERROR -> System.err.println(message)
    }
}
