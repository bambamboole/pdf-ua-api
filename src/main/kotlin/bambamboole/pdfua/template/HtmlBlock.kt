package bambamboole.pdfua.template

import bambamboole.pdfua.html.Html
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
@SerialName("html")
data class HtmlBlock(
    @SchemaDescription("Stable block identifier used for runtime data overrides.")
    override val id: String? = null,
    @SchemaGroup(SchemaGroups.CONTENT) val html: String,
    override val config: BaseBlockConfig = BaseBlockConfig(),
) : Block {
    override fun applyData(values: JsonElement): Block = copy(html = values.string("html") ?: html)

    override fun render(): Html = Html.raw(html)

    override fun validateData(
        value: JsonElement,
        path: ValidationPath,
    ): List<ValidationIssue> {
        val (obj, errs) = requireObject(value, path)
        if (obj == null) return errs
        return allowedKeys(obj, setOf("html"), path) + optionalStringField(obj, "html", path)
    }
}
