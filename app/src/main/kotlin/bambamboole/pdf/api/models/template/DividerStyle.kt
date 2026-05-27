package bambamboole.pdf.api.models.template

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class DividerStyle {
    @SerialName("solid") SOLID,
    @SerialName("dashed") DASHED,
    @SerialName("dotted") DOTTED,
    @SerialName("double") DOUBLE,
    @SerialName("none") NONE,
}
