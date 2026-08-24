package app.melotrail.licensing

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import app.melotrail.commercial.AiUseDisclosureReview
import app.melotrail.commercial.CommercialDependency
import app.melotrail.commercial.CommercialDependencyKind
import app.melotrail.commercial.CommercialTerm

@Serializable
enum class ModelUse { TRANSCRIPTION, PLANNING, COHESION, REPAIR_ASSISTANCE, GENERATION }

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
    /** Every actual use is explicit; unlisted use cannot be presented as reviewed. */
    @SerialName("approvedUses")
    val approvedUses: Set<ModelUse> = emptySet(),
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

    /** Snapshot this specific model use with its optional release-owner AI-use review. */
    fun commercialDependency(use: ModelUse, promptContract: String? = null, aiUseReview: AiUseDisclosureReview? = null): CommercialDependency = CommercialDependency(
        kind = CommercialDependencyKind.MODEL,
        identity = id.name,
        version = id.version,
        contentHash = modelHash,
        commercialTerm = when (commercialUse) {
            LicensePermission.PERMITTED -> CommercialTerm.PERMITTED
            LicensePermission.CONDITIONAL -> CommercialTerm.CONDITIONAL
            LicensePermission.PROHIBITED -> CommercialTerm.BLOCKED
            LicensePermission.UNKNOWN -> CommercialTerm.UNKNOWN
        },
        reviewed = status == LicenseStatus.APPROVED && reviewedAt != null && use in approvedUses,
        license = listOfNotNull(codeLicense, weightLicense).joinToString(" / ").ifBlank { "unknown" },
        source = displayName,
        attribution = reviewNotes?.takeIf { attribution == AttributionRequirement.REQUIRED },
        outputRightsNote = outputRights,
        promptContract = promptContract,
        aiUseReview = aiUseReview
    )
}
