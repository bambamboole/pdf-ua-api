package bambamboole.pdfua.models.template

import kotlinx.serialization.DeserializationStrategy
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
