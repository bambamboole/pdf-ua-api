package bambamboole.pdfua.models.template

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Orientation {
    @SerialName("portrait") PORTRAIT,
    @SerialName("landscape") LANDSCAPE,
}
