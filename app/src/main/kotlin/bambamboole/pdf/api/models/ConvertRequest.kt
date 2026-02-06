package bambamboole.pdf.api.models

import kotlinx.serialization.Serializable

@Serializable
data class ConvertRequest(
    val html: String
)
