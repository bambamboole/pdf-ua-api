package bambamboole.pdfua.models.template

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PageFormat(val widthMm: Double, val heightMm: Double) {
    @SerialName("A3") A3(297.0, 420.0),
    @SerialName("A4") A4(210.0, 297.0),
    @SerialName("A5") A5(148.0, 210.0),
    @SerialName("A6") A6(105.0, 148.0),
    @SerialName("Letter") LETTER(215.9, 279.4),
    @SerialName("Legal") LEGAL(215.9, 355.6),
    @SerialName("Tabloid") TABLOID(279.4, 431.8),
}
