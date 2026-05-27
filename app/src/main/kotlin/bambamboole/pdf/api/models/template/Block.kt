package bambamboole.pdf.api.models.template

import bambamboole.pdf.api.util.Html
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.StringWriter
import java.util.Base64
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

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
private val SAFE_KEY_VALUE_FIELD_KEY = Regex("^[A-Za-z][A-Za-z0-9_]*$")

private val SVG_DATA_URL = Regex("^data:image/svg\\+xml(?:;charset=[^;,]+)?;base64,", RegexOption.IGNORE_CASE)

private val SAFE_SVG_ELEMENTS = setOf(
    "svg",
    "g",
    "defs",
    "title",
    "desc",
    "path",
    "rect",
    "circle",
    "ellipse",
    "line",
    "polyline",
    "polygon",
    "text",
    "tspan",
    "lineargradient",
    "radialgradient",
    "stop",
    "clippath",
)

private val SAFE_SVG_ATTRIBUTES = setOf(
    "xmlns",
    "xmlns:xlink",
    "id",
    "viewbox",
    "width",
    "height",
    "x",
    "y",
    "x1",
    "y1",
    "x2",
    "y2",
    "cx",
    "cy",
    "r",
    "rx",
    "ry",
    "d",
    "points",
    "transform",
    "fill",
    "stroke",
    "stroke-width",
    "stroke-linecap",
    "stroke-linejoin",
    "stroke-miterlimit",
    "stroke-dasharray",
    "stroke-dashoffset",
    "opacity",
    "fill-opacity",
    "stroke-opacity",
    "font-family",
    "font-size",
    "font-weight",
    "text-anchor",
    "dx",
    "dy",
    "offset",
    "stop-color",
    "stop-opacity",
    "clip-path",
    "role",
    "aria-label",
)

private sealed class SvgSource {
    data class Content(val value: String) : SvgSource()
    object Invalid : SvgSource()
}

private fun nonNegative(value: Int): Int? = value.takeIf { it >= 0 }

private fun safeCssColor(value: String): String? = value.takeIf { it.isNotBlank() && SAFE_CSS_COLOR.matches(it) }

private fun JsonObject.stringValues(): Map<String, String?> =
    mapValues { (_, value) ->
        when (value) {
            JsonNull -> null
            else -> runCatching { value.jsonPrimitive.contentOrNull }.getOrNull()
        }
    }

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
data class HeadingConfig(
    override val typography: TypographyConfig? = null,
    override val spacing: SpacingConfig? = null,
    override val width: String? = null,
    override val align: Align? = null,
    val level: Int = 2,
) : BlockConfig

@Serializable
@SerialName("heading")
data class HeadingBlock(
    override val id: String? = null,
    val text: String,
    override val config: HeadingConfig = HeadingConfig(),
) : Block {
    override fun applyData(values: JsonObject): Block = copy(text = values.string("text") ?: text)

    override fun render(): String {
        require(config.level in 1..6) { "Heading level must be between 1 and 6: ${config.level}" }
        return "<h${config.level}>${Html.escape(text)}</h${config.level}>"
    }
}

@Serializable
data class ImageConfig(
    override val typography: TypographyConfig? = null,
    override val spacing: SpacingConfig? = null,
    override val width: String? = null,
    override val align: Align? = null,
    val maxHeight: Int = 60,
) : BlockConfig

@Serializable
@SerialName("image")
data class ImageBlock(
    override val id: String? = null,
    val src: String,
    val alt: String = "",
    override val config: ImageConfig = ImageConfig(),
) : Block {
    override fun applyData(values: JsonObject): Block =
        copy(
            src = values.string("src") ?: src,
            alt = values.string("alt") ?: alt,
        )

    override fun render(): String =
        inlineSanitizedSvg(src, alt)
            ?: "<img src=\"${Html.escape(src)}\" alt=\"${Html.escape(alt)}\">"

    override fun renderCss(cssId: String): List<String> =
        nonNegative(config.maxHeight)
            ?.takeIf { it > 0 }
            ?.let { listOf(".$cssId img, .$cssId svg { max-height: ${it}px; }") }
            ?: emptyList()
}

@Serializable
data class KeyValueField(
    val key: String,
    val label: String,
)

@Serializable
data class KeyValueConfig(
    override val typography: TypographyConfig? = null,
    override val spacing: SpacingConfig? = null,
    override val width: String? = null,
    override val align: Align? = null,
    val labelWidth: String = "30mm",
    val fields: List<KeyValueField> = emptyList(),
) : BlockConfig

@Serializable
@SerialName("key-value")
data class KeyValueBlock(
    override val id: String? = null,
    val values: Map<String, String?> = emptyMap(),
    override val config: KeyValueConfig = KeyValueConfig(),
) : Block {
    override fun applyData(values: JsonObject): Block = copy(values = values.stringValues())

    override fun render(): String {
        validateFields()
        val rows = config.fields.joinToString("") { field ->
            val label = Html.escape(field.label)
            val value = Html.escape(values[field.key].orEmpty())
            "<tr><td>$label</td><td>$value</td></tr>"
        }
        return "<table class=\"key-value\"><tbody>$rows</tbody></table>"
    }

    override fun renderCss(cssId: String): List<String> {
        validateFields()
        val labelWidth = safeCssWidth(config.labelWidth) ?: return emptyList()
        return listOf(".$cssId .key-value td:first-child { width: $labelWidth; }")
    }

    private fun validateFields() {
        config.fields.forEach { field ->
            require(SAFE_KEY_VALUE_FIELD_KEY.matches(field.key)) { "Key-value field key is invalid: ${field.key}" }
        }
    }
}

private fun svgSource(source: String): SvgSource? {
    val trimmed = source.trim()
    if (trimmed.startsWith("<svg", ignoreCase = true)) {
        return SvgSource.Content(trimmed)
    }

    val match = SVG_DATA_URL.find(trimmed) ?: return null
    val encoded = trimmed.substring(match.range.last + 1)
    return runCatching {
        SvgSource.Content(Base64.getDecoder().decode(encoded).toString(Charsets.UTF_8))
    }.getOrDefault(SvgSource.Invalid)
}

private fun inlineSanitizedSvg(source: String, alt: String): String? {
    val svg = when (val result = svgSource(source) ?: return null) {
        is SvgSource.Content -> result.value
        SvgSource.Invalid -> return ""
    }
    val document = runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        factory.newDocumentBuilder().parse(ByteArrayInputStream(svg.toByteArray(Charsets.UTF_8)))
    }.getOrNull() ?: return ""

    val root = document.documentElement ?: return ""
    if (!root.hasElementName("svg")) {
        return ""
    }

    sanitizeSvgElement(root)
    if (alt.isNotEmpty()) {
        root.setAttribute("role", "img")
        root.setAttribute("aria-label", alt)
    }
    return serializeElement(root)
}

private fun sanitizeSvgElement(element: Element) {
    val attributesToRemove = buildList {
        val attributes = element.attributes
        for (index in 0 until attributes.length) {
            val attribute = attributes.item(index)
            val name = attribute.nodeName.lowercase()
            val value = attribute.nodeValue.trim().lowercase()
            if (
                name !in SAFE_SVG_ATTRIBUTES ||
                name == "href" ||
                name == "xlink:href" ||
                value.contains("url(") ||
                value.contains("javascript:")
            ) {
                add(attribute.nodeName)
            }
        }
    }
    attributesToRemove.forEach(element::removeAttribute)

    val childElementsToRemove = mutableListOf<Element>()
    val children = element.childNodes
    for (index in 0 until children.length) {
        val child = children.item(index)
        if (child is Element) {
            if (!child.hasAllowedSvgElementName()) {
                childElementsToRemove.add(child)
            } else {
                sanitizeSvgElement(child)
            }
        }
    }
    childElementsToRemove.forEach { element.removeChild(it) }
}

private fun Element.hasElementName(expected: String): Boolean {
    val name = localName ?: tagName
    return name.equals(expected, ignoreCase = true)
}

private fun Element.hasAllowedSvgElementName(): Boolean {
    val name = localName ?: tagName
    return name.lowercase() in SAFE_SVG_ELEMENTS
}

private fun serializeElement(element: Element): String {
    val writer = StringWriter()
    TransformerFactory.newInstance()
        .newTransformer()
        .apply { setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes") }
        .transform(DOMSource(element), StreamResult(writer))
    return writer.toString()
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
