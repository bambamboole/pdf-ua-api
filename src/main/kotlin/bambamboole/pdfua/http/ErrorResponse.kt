package bambamboole.pdfua.http

import bambamboole.pdfua.template.ValidationIssue
import kotlinx.serialization.Serializable

/**
 * The single error envelope returned by every endpoint. [issues] is present (and non-empty) only
 * for structured input validation, such as template validation; simple errors set just [error].
 */
@Serializable
data class ErrorResponse(
    val error: String,
    val issues: List<ValidationIssue> = emptyList(),
)
