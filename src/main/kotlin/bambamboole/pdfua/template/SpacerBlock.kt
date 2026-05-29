package bambamboole.pdfua.template

import bambamboole.pdfua.css.CssDeclaration
import bambamboole.pdfua.css.css
import bambamboole.pdfua.css.cssMm
import bambamboole.pdfua.html.Html
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

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
    override fun applyData(values: JsonElement): Block = this

    override fun render(): Html = Html.EMPTY

    override fun renderCss(cssId: String): List<CssDeclaration> =
        listOf(
            css(".$cssId") {
                rule("height", cssMm(config.height))
            },
        )

    override fun validateData(
        value: JsonElement,
        path: ValidationPath,
    ): List<ValidationIssue> = listOf(issue(path, ValidationCodes.INVALID_VALUE, "Spacer block does not accept data"))
}
