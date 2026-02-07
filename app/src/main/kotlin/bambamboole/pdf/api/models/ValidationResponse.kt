package bambamboole.pdf.api.models

import kotlinx.serialization.Serializable

@Serializable
data class ValidationResponse(
    val isCompliant: Boolean,
    val flavour: String,
    val totalChecks: Int,
    val failedChecks: Int,
    val passedChecks: Int,
    val failures: List<ValidationFailure> = emptyList(),
    val metadata: PdfMetadata? = null
)

@Serializable
data class PdfMetadata(
    val title: String? = null,
    val subject: String? = null,
    val author: String? = null,
    val creator: String? = null,
    val producer: String? = null,
    val creationDate: String? = null
)

@Serializable
data class ValidationFailure(
    val clause: String,
    val testNumber: Int,
    val status: String,
    val message: String,
    val location: String? = null
)
