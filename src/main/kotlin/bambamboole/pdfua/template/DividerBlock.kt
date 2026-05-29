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
    override fun applyData(values: JsonElement): Block = this

    override fun render(): Html = html { voidTag("hr") }

    override fun renderCss(cssId: String): List<CssDeclaration> =
        listOf(
            css(".$cssId hr") {
                rule("border", "none")
                rule("margin", "2.5mm 0")
                rule("border-top-width", cssPt(config.thickness))
                rule("border-top-color", safeCssColor(config.lineColor))
                rule("border-top-style", config.style.cssValue())
            },
        )

    private fun DividerStyle.cssValue(): String =
        when (this) {
            DividerStyle.SOLID -> "solid"
            DividerStyle.DASHED -> "dashed"
            DividerStyle.DOTTED -> "dotted"
            DividerStyle.DOUBLE -> "double"
            DividerStyle.NONE -> "none"
        }

    override fun validateData(
        value: JsonElement,
        path: ValidationPath,
    ): List<ValidationIssue> = listOf(issue(path, ValidationCodes.INVALID_VALUE, "Divider block does not accept data"))
}
