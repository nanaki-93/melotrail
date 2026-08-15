package app.melotrail.licensing

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ModelId(
    @SerialName("name")
    val name: String,
    @SerialName("version")
    val version: String
) {
    override fun toString(): String = "$name/$version"
}

@Serializable
data class ModelLicense(
    @SerialName("id")
    val id: ModelId,
    @SerialName("displayName")
    val displayName: String,
    @SerialName("description")
    val description: String,
    @SerialName("codeLicense")
    val codeLicense: String? = null,
    @SerialName("weightLicense")
    val weightLicense: String? = null,
    @SerialName("commercialUse")
    val commercialUse: LicensePermission = LicensePermission.UNKNOWN,
    @SerialName("outputRights")
    val outputRights: String? = null,
    @SerialName("redistribution")
    val redistribution: LicensePermission = LicensePermission.UNKNOWN,
    @SerialName("attribution")
    val attribution: AttributionRequirement = AttributionRequirement.UNKNOWN,
    @SerialName("datasetRestrictions")
    val datasetRestrictions: String? = null,
    @SerialName("platformCompatibility")
    val platformCompatibility: List<String> = emptyList(),
    @SerialName("reviewedAt")
    val reviewedAt: Instant? = null,
    @SerialName("reviewer")
    val reviewer: String? = null,
    @SerialName("reviewNotes")
    val reviewNotes: String? = null,
    @SerialName("status")
    var status: LicenseStatus = LicenseStatus.UNKNOWN,
    @SerialName("installed")
    var installed: Boolean = false,
    @SerialName("installedAt")
    val installedAt: Instant? = null,
    @SerialName("modelHash")
    val modelHash: String? = null,
    @SerialName("minimumRAM")
    val minimumRAM: String? = null,
    @SerialName("tags")
    val tags: List<String> = emptyList(),
    @SerialName("createdAt")
    val createdAt: Instant = kotlinx.datetime.Clock.System.now(),
    @SerialName("updatedAt")
    val updatedAt: Instant = kotlinx.datetime.Clock.System.now()
) {
    fun isApprovedForCommercialUse(): Boolean {
        return status == LicenseStatus.APPROVED &&
                commercialUse == LicensePermission.PERMITTED
    }

    fun requiresReview(): Boolean {
        return status == LicenseStatus.REVIEW_REQUIRED ||
                status == LicenseStatus.UNKNOWN ||
                commercialUse == LicensePermission.UNKNOWN ||
                commercialUse == LicensePermission.CONDITIONAL
    }

    fun isBlocked(): Boolean {
        return status == LicenseStatus.BLOCKED
    }
}
