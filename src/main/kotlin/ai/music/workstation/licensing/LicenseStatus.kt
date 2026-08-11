package ai.music.workstation.licensing

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class LicenseStatus {
    @SerialName("APPROVED")
    APPROVED,

    @SerialName("REVIEW_REQUIRED")
    REVIEW_REQUIRED,

    @SerialName("BLOCKED")
    BLOCKED,

    @SerialName("UNKNOWN")
    UNKNOWN
}

@Serializable
enum class LicensePermission {
    @SerialName("PERMITTED")
    PERMITTED,

    @SerialName("PROHIBITED")
    PROHIBITED,

    @SerialName("CONDITIONAL")
    CONDITIONAL,

    @SerialName("UNKNOWN")
    UNKNOWN
}

@Serializable
enum class AttributionRequirement {
    @SerialName("REQUIRED")
    REQUIRED,

    @SerialName("NOT_REQUIRED")
    NOT_REQUIRED,

    @SerialName("OPTIONAL")
    OPTIONAL,

    @SerialName("UNKNOWN")
    UNKNOWN
}
