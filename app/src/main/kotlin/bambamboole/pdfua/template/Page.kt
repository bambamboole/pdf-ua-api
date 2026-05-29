package bambamboole.pdfua.template

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

@Serializable(with = PageSizeSerializer::class)
sealed interface PageSize

@Serializable
data class PresetPageSize(
    val format: PageFormat = PageFormat.A4,
    val orientation: Orientation = Orientation.PORTRAIT,
) : PageSize

@Serializable
data class CustomPageSize(
    val width: Int,
    val height: Int,
) : PageSize

object PageSizeSerializer : JsonContentPolymorphicSerializer<PageSize>(PageSize::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<PageSize> {
        val keys = element.jsonObject.keys
        return if ("width" in keys || "height" in keys) CustomPageSize.serializer() else PresetPageSize.serializer()
    }
}

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

@Serializable
enum class Orientation {
    @SerialName("portrait") PORTRAIT,
    @SerialName("landscape") LANDSCAPE,
}
