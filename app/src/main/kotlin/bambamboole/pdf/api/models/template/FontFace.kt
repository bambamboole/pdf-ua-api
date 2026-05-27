package bambamboole.pdf.api.models.template

import kotlinx.serialization.Serializable

@Serializable
data class FontFace(
    val src: String,
    val weight: Int = 400,
    val style: String = "normal",
)
