package org.lifeutils.nhentaidl

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.WithAssertions
import org.junit.jupiter.api.io.TempDir
import org.lifeutils.nhentaidl.cli.ApplicationContext
import org.lifeutils.nhentaidl.cli.CliArgsParser
import org.lifeutils.nhentaidl.cli.task.DownloadTask
import java.io.File
import kotlin.test.Test

class TestDownloadSearchWithFilters : WithAssertions {
    @Test
    fun testSearchDownload(@TempDir tempDir: File) {
        val count = 2

        val config = CliArgsParser(
            arrayOf(
                "-lang",
                "english",
                "-artist",
                "asami sekiya",
                "-count",
                count.toString(),
                "-o",
                tempDir.absolutePath,
                "-f",
                "plain_images",
                *httpArgs,
            )
        ).toConfig()

        val appContext = ApplicationContext(config)

        val task = DownloadTask(appContext)

        runBlocking {
            task.run()

            appContext.close()
        }

        val folders = tempDir.listFiles()
        val foldersContainingMetadata = folders.filter { hentaiDir ->
            hentaiDir.isDirectory && hentaiDir.listFiles().any { it.name == "metadata.json" }
        }

        assertThat(foldersContainingMetadata.size).isEqualTo(count)
    }
}
