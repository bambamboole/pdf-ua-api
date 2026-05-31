package bambamboole.pdfua.template

import bambamboole.pdfua.html.Html
import bambamboole.pdfua.html.html
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
@SchemaTsType("BlockConfig & { level?: number }")
data class HeadingConfig(
    override val typography: TypographyConfig? = null,
    override val spacing: SpacingConfig? = null,
    @SchemaDescription("CSS width for this block, such as 50%, 80mm, or auto.")
    override val width: String? = null,
    @SchemaDescription("Horizontal placement of this block within its row cell.")
    override val align: Align? = null,
    @SchemaMin(1) @SchemaMax(6) @SchemaIntDefault(2) val level: Int = 2,
) : BlockConfig

@Serializable
@SerialName("heading")
data class HeadingBlock(
    @SchemaDescription("Stable block identifier used for runtime data overrides.")
    override val id: String? = null,
    val text: String,
    override val config: HeadingConfig = HeadingConfig(),
) : Block {
    companion object {
        val HEADING_LEVEL_RANGE = 1..6
    }

    override fun applyData(values: JsonElement): Block = copy(text = values.string("text") ?: text)

    override fun render(): Html {
        check(config.level in HEADING_LEVEL_RANGE) { "Heading level must be between 1 and 6: ${config.level}" }
        return html {
            tag("h${config.level}") { text(text) }
        }
    }

    override fun validate(path: ValidationPath): List<ValidationIssue> =
        if (config.level in HEADING_LEVEL_RANGE) {
            emptyList()
        } else {
            listOf(
                issue(
                    path.child("config").child("level"),
                    ValidationCodes.OUT_OF_RANGE,
                    "Heading level must be between 1 and 6: ${config.level}",
                ),
            )
        }

    override fun validateData(
        value: JsonElement,
        path: ValidationPath,
    ): List<ValidationIssue> {
        val (obj, errs) = requireObject(value, path)
        if (obj == null) return errs
        return allowedKeys(obj, setOf("text"), path) + optionalStringField(obj, "text", path)
    }
}
