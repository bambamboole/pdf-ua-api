package bambamboole.pdf.api.models.template

import bambamboole.pdf.api.util.Html
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
sealed interface Block {
    val id: String?
    val config: BlockConfig

    /** Returns a copy with content fields replaced by matching keys in [values]. */
    fun applyData(values: JsonObject): Block

    /** Renders the block's inner HTML (without the positioning wrapper). */
    fun render(): String
}

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

@Serializable
@SerialName("text")
data class TextBlock(
    override val id: String? = null,
    val text: String,
    override val config: BaseBlockConfig = BaseBlockConfig(),
) : Block {
    override fun applyData(values: JsonObject): Block = copy(text = values.string("text") ?: text)

    override fun render(): String {
        val paragraphs = text.split(Regex("\\R{2,}"))
        return paragraphs.joinToString("") { paragraph ->
            "<p>" + Html.escape(paragraph).replace(Regex("\\R"), "<br>") + "</p>"
        }
    }
}

@Serializable
@SerialName("html")
data class HtmlBlock(
    override val id: String? = null,
    val html: String,
    override val config: BaseBlockConfig = BaseBlockConfig(),
) : Block {
    override fun applyData(values: JsonObject): Block = copy(html = values.string("html") ?: html)

    override fun render(): String = html
}
