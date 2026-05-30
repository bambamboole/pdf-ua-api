package bambamboole.pdfua.template

import bambamboole.pdfua.css.CssDeclaration
import bambamboole.pdfua.css.css
import bambamboole.pdfua.css.cssMm
import bambamboole.pdfua.html.Html
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
@SchemaTsType("BlockConfig & { height?: number }")
data class SpacerConfig(
    override val typography: TypographyConfig? = null,
    override val spacing: SpacingConfig? = null,
    @SchemaDescription("CSS width for this block, such as 50%, 80mm, or auto.")
    override val width: String? = null,
    @SchemaDescription("Horizontal placement of this block within its row cell.")
    override val align: Align? = null,
    @SchemaMin(0) @SchemaIntDefault(5) val height: Int = 5,
) : BlockConfig

@Serializable
@SerialName("spacer")
data class SpacerBlock(
    @SchemaDescription("Stable block identifier used for runtime data overrides.")
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
