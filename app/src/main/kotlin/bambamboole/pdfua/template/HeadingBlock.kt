package bambamboole.pdfua.template

import bambamboole.pdfua.html.Html
import bambamboole.pdfua.html.html
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

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
    override fun applyData(values: JsonElement): Block = copy(text = values.string("text") ?: text)

    override fun render(): Html {
        check(config.level in 1..6) { "Heading level must be between 1 and 6: ${config.level}" }
        return html {
            tag("h${config.level}") { text(text) }
        }
    }

    override fun validate(path: ValidationPath): List<ValidationIssue> =
        if (config.level in 1..6) {
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
