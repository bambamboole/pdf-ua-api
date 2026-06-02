package bambamboole.pdfua.template

import bambamboole.pdfua.css.CSS_LENGTH_PATTERN
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
@SchemaTsType("{ key: string; label: string }")
data class KeyValueField(
    @SchemaPattern("^[A-Za-z][A-Za-z0-9_]*$") val key: String,
    val label: String,
)

@Serializable
@SerialName("key-value")
data class KeyValueBlock(
    @SchemaDescription("Stable block identifier used for runtime data overrides.")
    override val id: String? = null,
    @SchemaTitle("KeyValueValues")
    @SchemaPropertyNames(pattern = "^[A-Za-z][A-Za-z0-9_]*$")
    @SchemaGroup(SchemaGroups.DATA)
    val values: Map<String, String?> = emptyMap(),
    @SchemaStringDefault("30mm")
    @SchemaPattern(CSS_LENGTH_PATTERN)
    @SchemaGroup(SchemaGroups.LAYOUT)
    val labelWidth: String = "30mm",
    @SchemaGroup(SchemaGroups.CONTENT) val fields: List<KeyValueField> = emptyList(),
    override val config: BaseBlockConfig = BaseBlockConfig(),
) : Block {
    override fun applyData(values: JsonElement): Block = if (values is JsonObject) copy(values = values.stringValues()) else this

    override fun render(): Html {
        validateFields()
        return html {
            tag("table", "class" to "key-value") {
                tag("tbody") {
                    fields.forEach { field ->
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
        val cssLabelWidth = safeCssWidth(labelWidth) ?: return emptyList()
        return listOf(
            css(".$cssId .key-value td:first-child") {
                rule("width", cssLabelWidth)
            },
        )
    }

    private fun validateFields() {
        fields.forEach { field ->
            check(SAFE_KEY_VALUE_FIELD_KEY.matches(field.key)) { "Key-value field key is invalid: ${field.key}" }
        }
    }

    override fun validate(path: ValidationPath): List<ValidationIssue> =
        cssLengthIssues(labelWidth, path.child("labelWidth")) +
            fields.flatMapIndexed { index, field ->
                if (SAFE_KEY_VALUE_FIELD_KEY.matches(field.key)) {
                    emptyList()
                } else {
                    listOf(
                        issue(
                            path
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
        val allowed = fields.map { it.key }.toSet()
        return allowedKeys(obj, allowed, path) +
            obj.keys.filter { it in allowed }.flatMap { key -> nullableStringField(obj, key, path) }
    }
}
