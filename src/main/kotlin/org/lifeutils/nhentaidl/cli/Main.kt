package org.lifeutils.nhentaidl.cli

import org.lifeutils.nhentaidl.cli.task.DownloadTask
import org.lifeutils.nhentaidl.cli.task.MigrationTask

suspend fun main(args: Array<String>) {
    val config = CliArgsParser(args).toConfig()

    val appContext = ApplicationContext(config)

    val task = when {
        config.isDownload -> DownloadTask(appContext)
        config.isMigration -> MigrationTask(appContext)
        else -> throw IllegalArgumentException("Must provide either download or migration config")
    }

    task.run()

    appContext.close()
}