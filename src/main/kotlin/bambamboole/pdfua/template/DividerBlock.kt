package bambamboole.pdfua.template

import bambamboole.pdfua.css.CSS_LENGTH_PATTERN
import bambamboole.pdfua.css.CssDeclaration
import bambamboole.pdfua.css.css
import bambamboole.pdfua.css.safeCssColor
import bambamboole.pdfua.css.safeCssWidth
import bambamboole.pdfua.html.Html
import bambamboole.pdfua.html.html
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
enum class DividerStyle {
    @SerialName("solid")
    SOLID,

    @SerialName("dashed")
    DASHED,

    @SerialName("dotted")
    DOTTED,

    @SerialName("double")
    DOUBLE,

    @SerialName("none")
    NONE,
}

@Serializable
@SerialName("divider")
data class DividerBlock(
    @SchemaDescription("Stable block identifier used for runtime data overrides.")
    override val id: String? = null,
    @SchemaDescription("Rule thickness as a CSS length, such as 1pt or 2px.")
    @SchemaStringDefault("1pt")
    @SchemaPattern(CSS_LENGTH_PATTERN)
    @SchemaGroup(SchemaGroups.STYLE)
    val thickness: String = "1pt",
    @SchemaPattern("^#[0-9A-Fa-f]{3,8}$")
    @SchemaStringDefault("#d1d5db")
    @SchemaGroup(SchemaGroups.STYLE)
    val lineColor: String = "#d1d5db",
    @SchemaEnumDefault("solid")
    @SchemaGroup(SchemaGroups.STYLE)
    val style: DividerStyle = DividerStyle.SOLID,
    override val config: BaseBlockConfig = BaseBlockConfig(),
) : Block {
    override fun applyData(values: JsonElement): Block = this

    override fun render(): Html = html { voidTag("hr") }

    override fun renderCss(cssId: String): List<CssDeclaration> =
        listOf(
            css(".$cssId hr") {
                rule("border", "none")
                rule("margin", "2.5mm 0")
                rule("border-top-width", safeCssWidth(thickness))
                rule("border-top-color", safeCssColor(lineColor))
                rule("border-top-style", style.name.lowercase())
            },
        )

    override fun validate(path: ValidationPath): List<ValidationIssue> = cssLengthIssues(thickness, path.child("thickness"))

    override fun validateData(
        value: JsonElement,
        path: ValidationPath,
    ): List<ValidationIssue> = listOf(issue(path, ValidationCodes.INVALID_VALUE, "Divider block does not accept data"))
}
