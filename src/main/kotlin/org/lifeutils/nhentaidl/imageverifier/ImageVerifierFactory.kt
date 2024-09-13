package org.lifeutils.nhentaidl.imageverifier

import javax.imageio.ImageIO

class ImageVerifierFactory {
    private val pngBlankVerifier = BlankLineImageVerifier(
        imageReader = ImageIO.getImageReadersByFormatName("png").next()
    )
    private val jpgBlankVerifier = BlankLineImageVerifier(
        imageReader = ImageIO.getImageReadersByFormatName("jpg").next()
    )
    private val gifBlankVerifier = BlankLineImageVerifier(
        imageReader = ImageIO.getImageReadersByFormatName("gif").next()
    )

    private val pngVerifier = PngImageVerifier(
        pngBlankLineVerifier = pngBlankVerifier
    )
    private val jpgVerifier = JpgImageVerifier(
        jpgBlankLineImageVerifier = jpgBlankVerifier
    )
    private val gifVerifier = GifImageVerifier(
        gifBlankLineImageVerifier = gifBlankVerifier
    )

    fun createImageVerifier(): GenericImageVerifier {
        return GenericImageVerifier(
            pngImageVerifier = pngVerifier,
            jpgImageVerifier = jpgVerifier,
            gifImageVerifier = gifVerifier,
        )
    }
}
