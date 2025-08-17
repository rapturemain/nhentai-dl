package org.lifeutils.nhentaidl

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.WithAssertions
import org.junit.jupiter.api.io.TempDir
import org.lifeutils.nhentaidl.cli.ApplicationContext
import org.lifeutils.nhentaidl.cli.CliArgsParser
import org.lifeutils.nhentaidl.cli.task.DownloadTask
import org.lifeutils.nhentaidl.model.AppMetadata
import org.lifeutils.nhentaidl.model.HentaiId
import org.lifeutils.nhentaidl.model.HentaiInfo
import org.lifeutils.nhentaidl.model.HentaiMetadata
import org.lifeutils.nhentaidl.model.ImageVerificationStatus
import org.lifeutils.nhentaidl.model.Metadata
import java.io.File
import java.time.ZonedDateTime
import kotlin.test.Test

class SingleDownloadTests : WithAssertions {
    private val hentaiId = 123

    val expectedMetadata = Metadata(
        hentaiInfo = HentaiInfo(
            id = HentaiId(hentaiId),
            title = "(C69) [Nakayohi Mogudan (Mogudan)] Omakehon 2005 (Neon Genesis Evangelion)",
            subTitle = "(C69) [なかよひモグダン (モグダン)] おまけ本 2005 (新世紀エヴァンゲリオン)",
            pageCount = 16,
            metadata = HentaiMetadata(
                parodies = listOf("neon genesis evangelion"),
                tags = listOf("big breasts", "sole female", "schoolgirl uniform", "bloomers", "gymshorts"),
                artists = listOf("mogudan"),
                language = listOf("japanese"),
                groups = listOf("nakayohi mogudan"),
                categories = listOf("doujinshi"),
                uploadedAt = ZonedDateTime.parse("2014-06-28T14:14:15.321934Z"),
                characters = listOf("rei ayanami"),
            ),
        ), appMetadata = AppMetadata(
            metadataVersion = 4,
            imageVerificationStatus = ImageVerificationStatus.NOT_VERIFIED,
            htmlPageFeature = false,
        )
    )

    val expectedDirectoryName = "[${hentaiId}] ${expectedMetadata.hentaiInfo.title}"
    val expectedImageExtension = ".jpg"
    val expectedFirstImageSizeInBytes = 227258L

    @Test
    fun testFileDownload(@TempDir tempDir: File) {
        val config = CliArgsParser(
            arrayOf(
                "-id",
                hentaiId.toString(),
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

        assertExpectedHentaiValid(appContext, tempDir)
    }

    @Test
    fun testMultipleFileDownload(@TempDir tempDir: File) {
        val count = 5
        val idsFilename = "ids.txt"

        val idsFile = tempDir.resolve(idsFilename)
        idsFile.writeText(
            (0..<count).map { hentaiId + it }.joinToString("\n")
        )

        val config = CliArgsParser(
            arrayOf(
                "-ids",
                idsFile.absolutePath,
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

        // check only first entry, assume other are ok too
        assertExpectedHentaiValid(appContext, tempDir)

        assertThat(tempDir.listFiles()).hasSize(count + 1) // include ids file
        assertThat(tempDir.listFiles().filter { it.isDirectory }).hasSize(count)
    }

    private fun assertExpectedHentaiValid(appContext: ApplicationContext, tempDir: File) {
        val hentaiDir = tempDir.resolve(expectedDirectoryName)

        val metadata = hentaiDir.resolve("metadata.json")
        val metadataContents = appContext.objectMapper.readValue<Metadata>(metadata)
        assertThat(metadataContents).isEqualTo(expectedMetadata)

        val filesCount = hentaiDir.listFiles().size
        assertThat(filesCount).isEqualTo(1 + expectedMetadata.hentaiInfo.pageCount)


        for (i in 1..expectedMetadata.hentaiInfo.pageCount) {
            val file = hentaiDir.resolve("$i$expectedImageExtension")
            assertThat(file.exists()).isTrue
            if (i == 1) {
                assertThat(file.length()).isEqualTo(expectedFirstImageSizeInBytes)
            }
        }
    }
}
