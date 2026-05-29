package bambamboole.pdfua.template

import bambamboole.pdfua.css.CssDeclaration
import bambamboole.pdfua.html.Html
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
sealed interface Block {
    val id: String?
    val config: BlockConfig

    /** Returns a copy with content fields replaced from runtime [values]. */
    fun applyData(values: JsonElement): Block

    /** Renders the block's inner HTML (without the positioning wrapper). */
    fun render(): Html

    /** Emits block-specific CSS for the renderer-generated wrapper class. */
    fun renderCss(cssId: String): List<CssDeclaration> = emptyList()

    /** Static invariants on this block declaration (config, keys, ranges). */
    fun validate(path: ValidationPath): List<ValidationIssue> = emptyList()

    /** Runtime data-shape contract; called only when [data] for this block id is present. */
    fun validateData(
        value: JsonElement,
        path: ValidationPath,
    ): List<ValidationIssue> = emptyList()
}

internal val SAFE_KEY_VALUE_FIELD_KEY = Regex("^[A-Za-z][A-Za-z0-9_]*$")

internal fun JsonElement.string(key: String): String? = (this as? JsonObject)?.get(key)?.let { it as? JsonPrimitive }?.contentOrNull

internal fun JsonObject.stringValues(): Map<String, String?> =
    mapValues { (_, value) ->
        when (value) {
            JsonNull -> null
            else -> runCatching { value.jsonPrimitive.contentOrNull }.getOrNull()
        }
    }
