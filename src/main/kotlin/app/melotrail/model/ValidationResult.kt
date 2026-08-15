package app.melotrail.model

import kotlinx.serialization.Serializable

@Serializable
data class ValidationResult(
    val valid: Boolean,
    val errors: List<String>
)
