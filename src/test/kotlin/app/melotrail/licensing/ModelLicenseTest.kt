package app.melotrail.licensing

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

class ModelLicenseTest {
    @Test
    fun `approved model should pass commercial use check`() {
        val license = ModelLicense(
            id = ModelId("test-model", "1.0"),
            displayName = "Test Model",
            description = "A test model",
            commercialUse = LicensePermission.PERMITTED,
            status = LicenseStatus.APPROVED
        )

        assertTrue(license.isApprovedForCommercialUse())
    }

    @Test
    fun `blocked model should fail commercial use check`() {
        val license = ModelLicense(
            id = ModelId("blocked-model", "1.0"),
            displayName = "Blocked Model",
            description = "A blocked model",
            commercialUse = LicensePermission.PROHIBITED,
            status = LicenseStatus.BLOCKED
        )

        assertFalse(license.isApprovedForCommercialUse())
    }

    @Test
    fun `unknown license should require review`() {
        val license = ModelLicense(
            id = ModelId("unknown-model", "1.0"),
            displayName = "Unknown Model",
            description = "An unknown model",
            status = LicenseStatus.UNKNOWN
        )

        assertTrue(license.requiresReview())
    }

    @Test
    fun `approved model should not require review`() {
        val license = ModelLicense(
            id = ModelId("approved-model", "1.0"),
            displayName = "Approved Model",
            description = "An approved model",
            commercialUse = LicensePermission.PERMITTED,
            status = LicenseStatus.APPROVED
        )

        assertFalse(license.requiresReview())
    }

    @Test
    fun `blocked model should be blocked`() {
        val license = ModelLicense(
            id = ModelId("blocked-model", "1.0"),
            displayName = "Blocked Model",
            description = "A blocked model",
            status = LicenseStatus.BLOCKED
        )

        assertTrue(license.isBlocked())
    }

    @Test
    fun `approved model should not be blocked`() {
        val license = ModelLicense(
            id = ModelId("approved-model", "1.0"),
            displayName = "Approved Model",
            description = "An approved model",
            status = LicenseStatus.APPROVED
        )

        assertFalse(license.isBlocked())
    }
}
