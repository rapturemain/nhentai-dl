package org.lifeutils.nhentaidl.cli.task

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.lifeutils.nhentaidl.cli.ApplicationContext
import org.lifeutils.nhentaidl.getMessageWithCause
import org.lifeutils.nhentaidl.migration.MigrationStatus
import org.lifeutils.nhentaidl.migration.createMigrationService
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class MigrationTask(
    private val appContext: ApplicationContext,
) : Task {
    override suspend fun run() {
        appContext.logger.info("Migration task started")

        val config = appContext.config

        val migrator = createMigrationService(
            appContext.objectMapper,
            appContext.writer,
            appContext.hentaiScraper,
            config.migrationConfig!!,
        )

        val files = collectFiles()
            .getOrElse {
                appContext.logger.error("Failed to collect migration files: ${it.message}")
                return
            }

        val failedMigrations = ConcurrentLinkedQueue<Pair<String, Throwable>>()
        val succeededMigrations = AtomicInteger(0)

        val semaphore = Semaphore(config.httpConfig.concurrencyLevelTitle)

        withContext(Dispatchers.IO) {
            val jobs = files.map { file ->
                async {
                    semaphore.withPermit {
                        if (appContext.isInterrupted.get()) {
                            return@async
                        }

                        migrator.migrateFile(file)
                            .onSuccess {
                                if (it == MigrationStatus.UP_TO_DATE) {
                                    appContext.logger("Up To Date file: ${file.name}")
                                    return@onSuccess
                                }

                                appContext.logger("Migrated file: ${file.name}")
                                succeededMigrations.incrementAndGet()
                            }
                            .onFailure { exception ->
                                failedMigrations.add(file.name to exception)
                                appContext.logger.error("Failed to migrate file: ${file.name}. Exception: ${exception.getMessageWithCause()}")
                            }
                    }
                }
            }

            jobs.awaitAll()
        }

        appContext.logger.info("Migration task finished")

        if (failedMigrations.isNotEmpty()) {
            val failedIds = failedMigrations.joinToString("\n") { (fileName, _) -> fileName }
            val failedIdsFile = File("migrations_${config.failedIdsPath}")
            failedIdsFile.writeText(failedIds)

            appContext.logger.error("Failed migrations:")
            val message = failedMigrations.joinToString("\n") { (fileName, exception) ->
                "File: $fileName, Exception: ${exception.getMessageWithCause()}"
            }
            appContext.logger.error(message)
        }

        appContext.logger.info("Succeeded migrations: ${succeededMigrations.get()}")
        appContext.logger.info("Failed migrations: ${failedMigrations.size}")
    }

    private fun collectFiles(): Result<List<File>> {
        return runCatching {
            val files = appContext.config
                .migrationConfig!!
                .sourceDir
                .listFiles()!!
                .toList()

            return Result.success(files)
        }
    }
}
