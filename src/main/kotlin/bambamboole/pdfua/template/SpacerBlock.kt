package bambamboole.pdfua.template

import bambamboole.pdfua.css.CSS_LENGTH_PATTERN
import bambamboole.pdfua.css.CssDeclaration
import bambamboole.pdfua.css.css
import bambamboole.pdfua.css.safeCssWidth
import bambamboole.pdfua.html.Html
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
@SerialName("spacer")
data class SpacerBlock(
    @SchemaDescription("Stable block identifier used for runtime data overrides.")
    override val id: String? = null,
    @SchemaDescription("Vertical space as a CSS length, such as 5mm or 12px.")
    @SchemaStringDefault("5mm")
    @SchemaPattern(CSS_LENGTH_PATTERN)
    @SchemaGroup(SchemaGroups.LAYOUT)
    val height: String = "5mm",
    override val config: BaseBlockConfig = BaseBlockConfig(),
) : Block {
    override fun applyData(values: JsonElement): Block = this

    override fun render(): Html = Html.EMPTY

    override fun renderCss(cssId: String): List<CssDeclaration> =
        listOf(
            css(".$cssId") {
                rule("height", safeCssWidth(height))
            },
        )

    override fun validate(path: ValidationPath): List<ValidationIssue> = cssLengthIssues(height, path.child("height"))

    override fun validateData(
        value: JsonElement,
        path: ValidationPath,
    ): List<ValidationIssue> = listOf(issue(path, ValidationCodes.INVALID_VALUE, "Spacer block does not accept data"))
}
