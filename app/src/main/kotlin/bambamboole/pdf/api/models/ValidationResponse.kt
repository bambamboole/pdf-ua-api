package bambamboole.pdf.api.models

import kotlinx.serialization.Serializable

@Serializable
data class ValidationResponse(
    val isCompliant: Boolean,
    val flavour: String,
    val totalChecks: Int,
    val failedChecks: Int,
    val passedChecks: Int,
    val failures: List<ValidationFailure> = emptyList()
)

@Serializable
data class ValidationFailure(
    val clause: String,
    val testNumber: Int,
    val status: String,
    val message: String,
    val location: String? = null
)
