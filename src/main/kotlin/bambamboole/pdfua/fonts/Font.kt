package bambamboole.pdfua.fonts

import bambamboole.pdfua.template.SchemaDescription
import bambamboole.pdfua.template.SchemaStringDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FontFace(
    val src: String,
    /** One or more whitespace-separated [FontWeight] values, e.g. "400" or "400 700". */
    @SchemaDescription("One or more whitespace-separated FontWeight values, e.g. \"400\" or \"400 700\".")
    @SchemaStringDefault("400")
    val weight: String = "400",
    @SchemaStringDefault("normal")
    val style: String = "normal",
)

@Serializable
enum class FontWeight(
    val numericValue: Int,
) {
    @SerialName("300")
    LIGHT(300),

    @SerialName("400")
    REGULAR(400),

    @SerialName("500")
    MEDIUM(500),

    @SerialName("600")
    SEMI_BOLD(600),

    @SerialName("700")
    BOLD(700),
}
