package bambamboole.pdfua.models

import kotlinx.serialization.Serializable

@Serializable
data class ConvertAndValidateResponse(
    val validation: ValidationResponse,
    val pdf: String
)
