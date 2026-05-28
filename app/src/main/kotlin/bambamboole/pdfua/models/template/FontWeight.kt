package bambamboole.pdfua.models.template

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class FontWeight(val numericValue: Int) {
    @SerialName("300") LIGHT(300),
    @SerialName("400") REGULAR(400),
    @SerialName("500") MEDIUM(500),
    @SerialName("600") SEMI_BOLD(600),
    @SerialName("700") BOLD(700),
}
