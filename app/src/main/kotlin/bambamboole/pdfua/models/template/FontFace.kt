package bambamboole.pdfua.models.template

import kotlinx.serialization.Serializable

@Serializable
data class FontFace(
    val src: String,
    /** One or more whitespace-separated [FontWeight] values, e.g. "400" or "400 700". */
    val weight: String = "400",
    val style: String = "normal",
)
