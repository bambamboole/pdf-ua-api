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

    /** Emits block-specific CSS for the renderer-generated wrapper class. */
    fun renderCss(cssId: String): List<String> = emptyList()
}

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

private val SAFE_CSS_COLOR = Regex("^[#a-zA-Z0-9(),.%\\s-]+$")

private fun nonNegative(value: Int): Int? = value.takeIf { it >= 0 }

private fun safeCssColor(value: String): String? = value.takeIf { it.isNotBlank() && SAFE_CSS_COLOR.matches(it) }

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

@Serializable
data class SpacerConfig(
    override val typography: TypographyConfig? = null,
    override val spacing: SpacingConfig? = null,
    override val width: String? = null,
    override val align: Align? = null,
    val height: Int = 5,
) : BlockConfig

@Serializable
@SerialName("spacer")
data class SpacerBlock(
    override val id: String? = null,
    override val config: SpacerConfig = SpacerConfig(),
) : Block {
    override fun applyData(values: JsonObject): Block = this

    override fun render(): String = ""

    override fun renderCss(cssId: String): List<String> =
        nonNegative(config.height)
            ?.let { listOf(".$cssId { height: ${it}mm; }") }
            ?: emptyList()
}

@Serializable
data class DividerConfig(
    override val typography: TypographyConfig? = null,
    override val spacing: SpacingConfig? = null,
    override val width: String? = null,
    override val align: Align? = null,
    val thickness: Int = 1,
    val lineColor: String = "#d1d5db",
    val style: DividerStyle = DividerStyle.SOLID,
) : BlockConfig

@Serializable
@SerialName("divider")
data class DividerBlock(
    override val id: String? = null,
    override val config: DividerConfig = DividerConfig(),
) : Block {
    override fun applyData(values: JsonObject): Block = this

    override fun render(): String = "<hr>"

    override fun renderCss(cssId: String): List<String> {
        val declarations = buildList {
            add("border: none")
            add("margin: 2.5mm 0")
            nonNegative(config.thickness)?.let { add("border-top-width: ${it}pt") }
            safeCssColor(config.lineColor)?.let { add("border-top-color: $it") }
            add("border-top-style: ${config.style.cssValue()}")
        }
        return if (declarations.isEmpty()) {
            emptyList()
        } else {
            listOf(".$cssId hr { ${declarations.joinToString("; ")}; }")
        }
    }

    private fun DividerStyle.cssValue(): String =
        when (this) {
            DividerStyle.SOLID -> "solid"
            DividerStyle.DASHED -> "dashed"
            DividerStyle.DOTTED -> "dotted"
            DividerStyle.DOUBLE -> "double"
            DividerStyle.NONE -> "none"
        }
}
