package bambamboole.pdfua.template

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.net.URI

private const val DESCRIBE_MAX = 64

private val MATRIX_2D =
    setOf(Symbology.QR, Symbology.DATA_MATRIX, Symbology.AZTEC, Symbology.PDF417)

@Serializable
sealed interface CodeContent {
    /** The string encoded into the symbol. */
    fun toPayload(): String

    /** Short human summary used to build the auto alt text. */
    fun describe(): String

    /** Whether this payload may be encoded in [symbology]. */
    fun supports(symbology: Symbology): Boolean

    fun applyData(values: JsonElement): CodeContent

    fun validate(path: ValidationPath): List<ValidationIssue> = emptyList()

    fun validateData(
        value: JsonElement,
        path: ValidationPath,
    ): List<ValidationIssue> = emptyList()
}

@Serializable
@SerialName("raw")
data class RawContent(
    @SchemaGroup(SchemaGroups.CONTENT) val value: String,
) : CodeContent {
    override fun toPayload(): String = value

    override fun describe(): String = value.take(DESCRIBE_MAX)

    override fun supports(symbology: Symbology): Boolean = true

    override fun applyData(values: JsonElement): CodeContent = copy(value = values.string("value") ?: value)

    override fun validate(path: ValidationPath): List<ValidationIssue> =
        if (value.isBlank()) {
            listOf(issue(path.child("value"), ValidationCodes.INVALID_VALUE, "Code value cannot be blank"))
        } else {
            emptyList()
        }

    override fun validateData(
        value: JsonElement,
        path: ValidationPath,
    ): List<ValidationIssue> {
        val (obj, errs) = requireObject(value, path)
        if (obj == null) return errs
        return allowedKeys(obj, setOf("value"), path) + optionalStringField(obj, "value", path)
    }
}

@Serializable
@SerialName("text")
data class TextContent(
    @SchemaGroup(SchemaGroups.CONTENT) val text: String,
) : CodeContent {
    override fun toPayload(): String = text

    override fun describe(): String = text.take(DESCRIBE_MAX)

    override fun supports(symbology: Symbology): Boolean = symbology in MATRIX_2D

    override fun applyData(values: JsonElement): CodeContent = copy(text = values.string("text") ?: text)

    override fun validate(path: ValidationPath): List<ValidationIssue> =
        if (text.isBlank()) {
            listOf(issue(path.child("text"), ValidationCodes.INVALID_VALUE, "Code text cannot be blank"))
        } else {
            emptyList()
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

@Serializable
@SerialName("url")
data class UrlContent(
    @SchemaGroup(SchemaGroups.CONTENT) val url: String,
) : CodeContent {
    override fun toPayload(): String = url

    override fun describe(): String = url.take(DESCRIBE_MAX)

    override fun supports(symbology: Symbology): Boolean = symbology in MATRIX_2D

    override fun applyData(values: JsonElement): CodeContent = copy(url = values.string("url") ?: url)

    override fun validate(path: ValidationPath): List<ValidationIssue> {
        val scheme = runCatching { URI.create(url).scheme?.lowercase() }.getOrNull()
        return if (scheme == "http" || scheme == "https") {
            emptyList()
        } else {
            listOf(issue(path.child("url"), ValidationCodes.INVALID_URI, "URL must use http or https"))
        }
    }

    override fun validateData(
        value: JsonElement,
        path: ValidationPath,
    ): List<ValidationIssue> {
        val (obj, errs) = requireObject(value, path)
        if (obj == null) return errs
        return allowedKeys(obj, setOf("url"), path) + optionalStringField(obj, "url", path)
    }
}
