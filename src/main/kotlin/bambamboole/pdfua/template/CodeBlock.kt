package bambamboole.pdfua.template

import bambamboole.pdfua.css.CSS_LENGTH_PATTERN
import bambamboole.pdfua.css.CssDeclaration
import bambamboole.pdfua.css.css
import bambamboole.pdfua.css.safeCssWidth
import bambamboole.pdfua.html.Html
import bambamboole.pdfua.template.barcode.BarcodeRenderer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
@SerialName("code")
data class CodeBlock(
    @SchemaDescription("Stable block identifier used for runtime data overrides.")
    override val id: String? = null,
    @SchemaGroup(SchemaGroups.CONTENT) val symbology: Symbology,
    @SchemaGroup(SchemaGroups.CONTENT) val content: CodeContent,
    @SchemaDescription("Rendered code height as a CSS length, such as 20mm or 96px.")
    @SchemaPattern(CSS_LENGTH_PATTERN)
    @SchemaGroup(SchemaGroups.LAYOUT)
    val height: String? = null,
    override val config: BaseBlockConfig = BaseBlockConfig(),
) : Block {
    override fun applyData(values: JsonElement): Block = copy(content = content.applyData(values))

    override fun render(): Html {
        val svg = BarcodeRenderer.toSvg(symbology, content.toPayload())
        val altText = "${symbology.label}: ${content.describe()}"
        return Html.raw(withAccessibility(svg, altText))
    }

    override fun renderCss(cssId: String): List<CssDeclaration> =
        height?.let { value ->
            listOf(
                css(".$cssId svg") {
                    rule("max-height", safeCssWidth(value))
                    rule("height", "auto")
                },
            )
        } ?: emptyList()

    override fun validate(path: ValidationPath): List<ValidationIssue> =
        buildList {
            addAll(cssLengthIssues(height, path.child("height")))
            addAll(content.validate(path.child("content")))
            if (!content.supports(symbology)) {
                add(
                    issue(
                        path.child("symbology"),
                        ValidationCodes.INVALID_VALUE,
                        "${symbology.label} cannot encode this content type",
                    ),
                )
            }
        }

    override fun validateData(
        value: JsonElement,
        path: ValidationPath,
    ): List<ValidationIssue> = content.validateData(value, path)
}

/**
 * Injects role="img" and an escaped aria-label into the root <svg> tag of Okapi output.
 * Handles both `<svg attr...>` and `<svg>` forms.
 */
private fun withAccessibility(
    svg: String,
    altText: String,
): String {
    val openTag = svg.indexOf("<svg")
    if (openTag < 0) return svg
    val insertAt = openTag + "<svg".length
    val escaped = Html.escape(altText)
    return svg.substring(0, insertAt) + " role=\"img\" aria-label=\"$escaped\"" + svg.substring(insertAt)
}
