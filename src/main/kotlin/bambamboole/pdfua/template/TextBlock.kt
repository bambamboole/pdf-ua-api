package bambamboole.pdfua.template

import bambamboole.pdfua.html.Html
import bambamboole.pdfua.html.html
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
@SerialName("text")
data class TextBlock(
    override val id: String? = null,
    val text: String,
    override val config: BaseBlockConfig = BaseBlockConfig(),
) : Block {
    override fun applyData(values: JsonElement): Block = copy(text = values.string("text") ?: text)

    override fun render(): Html =
        html {
            text.split(Regex("\\R{2,}")).forEach { paragraph ->
                tag("p") {
                    paragraph.split(Regex("\\R")).forEachIndexed { index, line ->
                        if (index > 0) voidTag("br")
                        text(line)
                    }
                }
            }
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
