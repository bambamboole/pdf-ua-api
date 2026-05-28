package bambamboole.pdfua.models.template

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TableStyle {
    @SerialName("striped") STRIPED,
    @SerialName("bordered") BORDERED,
    @SerialName("minimal") MINIMAL,
}
