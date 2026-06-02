package bambamboole.pdfua.template

import bambamboole.pdfua.css.CssDeclaration
import bambamboole.pdfua.css.css
import bambamboole.pdfua.css.cssPt
import bambamboole.pdfua.css.safeCssColor
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
    @SchemaMin(0) @SchemaIntDefault(1) val thickness: Int = 1,
    @SchemaPattern("^#[0-9A-Fa-f]{3,8}$") @SchemaStringDefault("#d1d5db") val lineColor: String = "#d1d5db",
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
                rule("border-top-width", cssPt(thickness))
                rule("border-top-color", safeCssColor(lineColor))
                rule("border-top-style", style.name.lowercase())
            },
        )

    override fun validateData(
        value: JsonElement,
        path: ValidationPath,
    ): List<ValidationIssue> = listOf(issue(path, ValidationCodes.INVALID_VALUE, "Divider block does not accept data"))
}
