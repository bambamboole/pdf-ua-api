package bambamboole.pdfua.template

import bambamboole.pdfua.css.CssDeclaration
import bambamboole.pdfua.css.css
import bambamboole.pdfua.css.safeCssWidth
import bambamboole.pdfua.html.Html
import bambamboole.pdfua.html.html
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class KeyValueField(
    val key: String,
    val label: String,
)

@Serializable
data class KeyValueConfig(
    override val typography: TypographyConfig? = null,
    override val spacing: SpacingConfig? = null,
    override val width: String? = null,
    override val align: Align? = null,
    val labelWidth: String = "30mm",
    val fields: List<KeyValueField> = emptyList(),
) : BlockConfig

@Serializable
@SerialName("key-value")
data class KeyValueBlock(
    override val id: String? = null,
    val values: Map<String, String?> = emptyMap(),
    override val config: KeyValueConfig = KeyValueConfig(),
) : Block {
    override fun applyData(values: JsonElement): Block = if (values is JsonObject) copy(values = values.stringValues()) else this

    override fun render(): Html {
        validateFields()
        return html {
            tag("table", "class" to "key-value") {
                tag("tbody") {
                    config.fields.forEach { field ->
                        tag("tr") {
                            tag("td") { text(field.label) }
                            tag("td") { text(values[field.key].orEmpty()) }
                        }
                    }
                }
            }
        }
    }

    override fun renderCss(cssId: String): List<CssDeclaration> {
        validateFields()
        val labelWidth = safeCssWidth(config.labelWidth) ?: return emptyList()
        return listOf(
            css(".$cssId .key-value td:first-child") {
                rule("width", labelWidth)
            },
        )
    }

    private fun validateFields() {
        config.fields.forEach { field ->
            check(SAFE_KEY_VALUE_FIELD_KEY.matches(field.key)) { "Key-value field key is invalid: ${field.key}" }
        }
    }

    override fun validate(path: ValidationPath): List<ValidationIssue> =
        config.fields.flatMapIndexed { index, field ->
            if (SAFE_KEY_VALUE_FIELD_KEY.matches(field.key)) {
                emptyList()
            } else {
                listOf(
                    issue(
                        path
                            .child("config")
                            .child("fields")
                            .index(index)
                            .child("key"),
                        ValidationCodes.INVALID_KEY,
                        "Key-value field key is invalid: ${field.key}",
                    ),
                )
            }
        }

    override fun validateData(
        value: JsonElement,
        path: ValidationPath,
    ): List<ValidationIssue> {
        val (obj, errs) = requireObject(value, path)
        if (obj == null) return errs
        val allowed = config.fields.map { it.key }.toSet()
        return allowedKeys(obj, allowed, path) +
            obj.keys.filter { it in allowed }.flatMap { key -> nullableStringField(obj, key, path) }
    }
}
