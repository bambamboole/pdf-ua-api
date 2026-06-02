package bambamboole.pdfua.template

import bambamboole.pdfua.html.Html
import bambamboole.pdfua.html.html
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
@SerialName("heading")
data class HeadingBlock(
    @SchemaDescription("Stable block identifier used for runtime data overrides.")
    override val id: String? = null,
    @SchemaGroup(SchemaGroups.CONTENT) val text: String,
    @SchemaMin(1) @SchemaMax(6) @SchemaIntDefault(2) @SchemaGroup(SchemaGroups.CONTENT) val level: Int = 2,
    override val config: BaseBlockConfig = BaseBlockConfig(),
) : Block {
    companion object {
        val HEADING_LEVEL_RANGE = 1..6
    }

    override fun applyData(values: JsonElement): Block = copy(text = values.string("text") ?: text)

    override fun render(): Html {
        check(level in HEADING_LEVEL_RANGE) { "Heading level must be between 1 and 6: $level" }
        return html {
            tag("h$level") { text(text) }
        }
    }

    override fun validate(path: ValidationPath): List<ValidationIssue> =
        if (level in HEADING_LEVEL_RANGE) {
            emptyList()
        } else {
            listOf(
                issue(
                    path.child("level"),
                    ValidationCodes.OUT_OF_RANGE,
                    "Heading level must be between 1 and 6: $level",
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
