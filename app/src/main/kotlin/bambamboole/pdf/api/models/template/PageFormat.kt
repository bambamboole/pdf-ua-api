package bambamboole.pdf.api.models.template

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PageFormat(val widthMm: Double, val heightMm: Double, val cssSize: String) {
    @SerialName("A4") A4(210.0, 297.0, "A4"),
    @SerialName("Letter") LETTER(215.9, 279.4, "Letter"),
    @SerialName("ParcelLabel4x6") PARCEL_LABEL_4X6(101.6, 152.4, "101.6mm 152.4mm"),
}
