package org.lifeutils.nhentaidl.imageverifier

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.awt.image.BufferedImage
import java.io.File
import java.io.InputStream
import javax.imageio.ImageReader
import kotlin.math.abs

private val BROKEN_IMAGE_THRESHOLD = 0.07

class BlankLineImageVerifier(
    private val imageReader: ImageReader,
) : ImageVerifier {

    private val imageReaderMutex = Mutex()

    override suspend fun verify(context: ImageVerifierContext): Result<Unit> {
        return runCatching {
            context.file.inputStream().use {
                return verifyInputStream(it)
            }
        }
    }

    override suspend fun verify(content: ByteArray): Result<Unit> {
        return runCatching {
            content.inputStream().use {
                return verifyInputStream(it)
            }
        }
    }

    private suspend fun verifyInputStream(inputStream: InputStream): Result<Unit> {
        return runCatching {
            imageReaderMutex.withLock {
                imageReader.input = inputStream
                val imageCount = imageReader.getNumImages(true)
                val images = (0..<imageCount).map {
                    imageReader.read(it)
                }
                for (image in images) {
                    val result = verifyInner(image)
                    if (result.isFailure) {
                        return result
                    }
                }
                return Result.success(Unit)
            }
        }
    }

    private fun verifyInner(image: BufferedImage, file: File? = null): Result<Unit> {
        val width = image.width
        val height = image.height

        val lastPixel = Color(image.getRGB(width - 1, height - 1))
        if (!lastPixel.isGrayish()) {
            return Result.success(Unit)
        }

        var blankLineCount = 0
        for (y in height - 1 downTo 0) {
            var isBlank = true

            for (x in width - 1 downTo 0) {
                val pixel = Color(image.getRGB(x, y))
                if (pixel != lastPixel) {
                    isBlank = false
                    break
                }
            }

            if (isBlank) {
                blankLineCount++
            } else {
                break
            }
        }

        val percentage = blankLineCount.toDouble() / height
        if (percentage > BROKEN_IMAGE_THRESHOLD) {
            return Result.failure(InvalidImageException(file, "Broken image. Blank line percentage: $percentage"))
        }
        return Result.success(Unit)
    }
}

private data class Color(
    val red: Int,
    val green: Int,
    val blue: Int,
    val alpha: Int,
) {
    constructor(argb: Int) : this(
        alpha = (argb ushr 24) and 0xFF,
        red = (argb shr 16) and 0xFF,
        green = (argb shr 8) and 0xFF,
        blue = argb and 0xFF
    )

    fun isGrayish(): Boolean {
        val redGreenDiff = abs(red - green)
        val redBlueDiff = abs(red - blue)
        val greenBlueDiff = abs(green - blue)
        return redGreenDiff < 10 && redBlueDiff < 10 && greenBlueDiff < 10
    }
}
