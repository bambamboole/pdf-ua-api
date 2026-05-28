package bambamboole.pdfua.template

import bambamboole.pdfua.css.CssDeclaration
import bambamboole.pdfua.html.Html
import bambamboole.pdfua.css.css
import bambamboole.pdfua.css.cssPx
import bambamboole.pdfua.html.html
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
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
    override fun applyData(values: JsonElement): Block =
        copy(
            src = values.string("src") ?: src,
            alt = values.string("alt") ?: alt,
        )

    override fun render(): Html =
        inlineSanitizedSvg(src, alt)?.let(Html::raw)
            ?: html { voidTag("img", "src" to src, "alt" to alt) }

    override fun renderCss(cssId: String): List<CssDeclaration> =
        listOf(
            css(".$cssId img, .$cssId svg") {
                rule("max-height", cssPx(config.maxHeight))
            },
        )

    override fun validateData(value: JsonElement, path: ValidationPath): List<ValidationIssue> {
        val (obj, errs) = requireObject(value, path)
        if (obj == null) return errs
        return allowedKeys(obj, setOf("src", "alt"), path) +
            optionalStringField(obj, "src", path) +
            optionalStringField(obj, "alt", path)
    }
}

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
