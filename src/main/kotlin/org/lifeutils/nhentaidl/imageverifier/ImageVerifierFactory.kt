package org.lifeutils.nhentaidl.imageverifier

class ImageVerifierFactory {
    private val blankLineVerifier = BlankLineImageVerifier()

    private val pngVerifier = PngImageVerifier(
        pngBlankLineVerifier = blankLineVerifier
    )
    private val jpgVerifier = JpgImageVerifier(
        jpgBlankLineImageVerifier = blankLineVerifier
    )
    private val gifVerifier = GifImageVerifier(
        gifBlankLineImageVerifier = blankLineVerifier
    )

    fun createImageVerifier(): GenericImageVerifier {
        return GenericImageVerifier(
            pngImageVerifier = pngVerifier,
            jpgImageVerifier = jpgVerifier,
            gifImageVerifier = gifVerifier,
        )
    }
}
