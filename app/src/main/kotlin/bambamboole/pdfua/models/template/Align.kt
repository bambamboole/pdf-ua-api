package bambamboole.pdfua.models.template

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Align {
    @SerialName("left") LEFT,
    @SerialName("center") CENTER,
    @SerialName("right") RIGHT,
}
