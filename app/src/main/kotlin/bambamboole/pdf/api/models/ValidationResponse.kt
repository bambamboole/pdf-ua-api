package bambamboole.pdf.api.models

import kotlinx.serialization.Serializable

@Serializable
data class ValidationResponse(
    val isCompliant: Boolean,
    val profiles: List<ProfileResult>,
    val summary: ValidationSummary,
    val documentInfo: DocumentInfo? = null,
    val failures: List<ValidationFailure> = emptyList(),
    val metadata: PdfMetadata? = null
)

@Serializable
data class DocumentInfo(
    val pages: Int,
    val tagged: Boolean,
    val language: String?,
    val structureElements: Int,
    val fonts: List<FontInfo>,
    val images: Int
)

@Serializable
data class FontInfo(
    val name: String,
    val embedded: Boolean,
    val type: String
)

@Serializable
data class ProfileResult(
    val profile: String,
    val specification: String,
    val isCompliant: Boolean,
    val totalChecks: Int,
    val passedChecks: Int,
    val failedChecks: Int
)

@Serializable
data class ValidationSummary(
    val totalChecks: Int,
    val passedChecks: Int,
    val failedChecks: Int,
    val categories: List<CategoryResult> = emptyList()
)

@Serializable
data class CategoryResult(
    val category: String,
    val passedChecks: Int,
    val failedChecks: Int
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
    val profile: String,
    val clause: String,
    val testNumber: Int,
    val category: String,
    val message: String,
    val location: String? = null,
    val errorDetails: String? = null
)
