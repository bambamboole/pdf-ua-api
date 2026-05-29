package bambamboole.pdfua.http

import bambamboole.pdfua.template.ValidationIssue
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

@Serializable
data class ValidationErrorResponse(
    @EncodeDefault val error: String = "validation_failed",
    val issues: List<ValidationIssue>,
)
