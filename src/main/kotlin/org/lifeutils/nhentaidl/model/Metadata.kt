package org.lifeutils.nhentaidl.model

const val CURRENT_VERSION = 4

data class Metadata(
    val hentaiInfo: HentaiInfo,
    val appMetadata: AppMetadata,
)

data class AppMetadata (
    val metadataVersion: Int = CURRENT_VERSION,
    var imageVerificationStatus: ImageVerificationStatus = ImageVerificationStatus.NOT_VERIFIED,
    var htmlPageFeature: Boolean = false,
)

enum class ImageVerificationStatus {
    VERIFICATION_PASSED,
    VERIFICATION_FAILED,
    NOT_VERIFIED
}