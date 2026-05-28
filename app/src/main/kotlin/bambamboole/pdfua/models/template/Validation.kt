package bambamboole.pdfua.models.template

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URI

@Serializable
data class ValidationIssue(
    val path: String,
    val code: String,
    val message: String,
)

@Serializable
data class ValidationErrorResponse(
    @EncodeDefault val error: String = "validation_failed",
    val issues: List<ValidationIssue>,
)

object ValidationCodes {
    const val INVALID_JSON = "invalid_json"
    const val MISSING_FIELD = "missing_field"
    const val UNKNOWN_FIELD = "unknown_field"
    const val INVALID_TYPE = "invalid_type"
    const val INVALID_VALUE = "invalid_value"
    const val UNSUPPORTED_VERSION = "unsupported_version"
    const val OUT_OF_RANGE = "out_of_range"
    const val INVALID_KEY = "invalid_key"
    const val INVALID_URI = "invalid_uri"
    const val DUPLICATE_BLOCK_ID = "duplicate_block_id"
    const val ORPHAN_DATA_ID = "orphan_data_id"
}

@JvmInline
value class ValidationPath(private val segments: List<String> = emptyList()) {
    fun child(name: String): ValidationPath = ValidationPath(segments + name)
    fun index(i: Int): ValidationPath = ValidationPath(segments + "[$i]")

    override fun toString(): String {
        if (segments.isEmpty()) return "$"
        return buildString {
            append('$')
            segments.forEach { segment ->
                if (segment.startsWith("[")) append(segment) else append('.').append(segment)
            }
        }
    }
}

internal fun issue(path: ValidationPath, code: String, message: String): ValidationIssue =
    ValidationIssue(path.toString(), code, message)

fun serializationIssue(e: SerializationException): ValidationIssue {
    val msg = e.message.orEmpty()
    val code = when {
        e is MissingFieldException -> ValidationCodes.MISSING_FIELD
        e::class.simpleName == "UnknownKeyException" -> ValidationCodes.UNKNOWN_FIELD
        msg.contains("unknown key", ignoreCase = true) -> ValidationCodes.UNKNOWN_FIELD
        else -> ValidationCodes.INVALID_JSON
    }
    return ValidationIssue(
        path = "$",
        code = code,
        message = e.message ?: "Invalid JSON payload",
    )
}

internal fun requireObject(
    value: JsonElement,
    path: ValidationPath,
): Pair<JsonObject?, List<ValidationIssue>> =
    if (value is JsonObject) value to emptyList()
    else null to listOf(issue(path, ValidationCodes.INVALID_TYPE, "Expected object"))

internal fun requireArray(
    value: JsonElement,
    path: ValidationPath,
): Pair<JsonArray?, List<ValidationIssue>> =
    if (value is JsonArray) value to emptyList()
    else null to listOf(issue(path, ValidationCodes.INVALID_TYPE, "Expected array"))

internal fun allowedKeys(
    obj: JsonObject,
    allowed: Set<String>,
    path: ValidationPath,
): List<ValidationIssue> =
    obj.keys
        .filter { it !in allowed }
        .map { issue(path.child(it), ValidationCodes.UNKNOWN_FIELD, "Unknown field '$it'") }

internal fun optionalStringField(
    obj: JsonObject,
    key: String,
    path: ValidationPath,
): List<ValidationIssue> {
    val value = obj[key] ?: return emptyList()
    return if (value is JsonPrimitive && value.isString && value !is JsonNull) emptyList()
    else listOf(issue(path.child(key), ValidationCodes.INVALID_TYPE, "Expected string"))
}

internal fun nullableStringField(
    obj: JsonObject,
    key: String,
    path: ValidationPath,
): List<ValidationIssue> {
    val value = obj[key] ?: return emptyList()
    if (value is JsonNull) return emptyList()
    return if (value is JsonPrimitive && value.isString) emptyList()
    else listOf(issue(path.child(key), ValidationCodes.INVALID_TYPE, "Expected string or null"))
}

fun Template.validate(data: Map<String, JsonElement>): List<ValidationIssue> = buildList {
    val root = ValidationPath().child("template")
    if (version != 1) {
        add(
            issue(
                root.child("version"),
                ValidationCodes.UNSUPPORTED_VERSION,
                "Unsupported template version: $version",
            ),
        )
    }
    addAll(validatePageConfig(config.page, root.child("config").child("page")))

    val seenIds = mutableSetOf<String>()
    val blocksById = mutableMapOf<String, Pair<Block, ValidationPath>>()

    fun walkRows(rows: List<Row>, rowsPath: ValidationPath) {
        rows.forEachIndexed { rowIndex, row ->
            row.blocks.forEachIndexed { blockIndex, block ->
                val blockPath = rowsPath.index(rowIndex).child("blocks").index(blockIndex)
                addAll(block.validate(blockPath))
                val id = block.id ?: return@forEachIndexed
                if (!seenIds.add(id)) {
                    add(
                        issue(
                            blockPath.child("id"),
                            ValidationCodes.DUPLICATE_BLOCK_ID,
                            "Duplicate block id: $id",
                        ),
                    )
                } else {
                    blocksById[id] = block to blockPath
                }
            }
        }
    }

    walkRows(rows, root.child("rows"))
    walkRows(
        config.page.footer.rows,
        root.child("config").child("page").child("footer").child("rows"),
    )

    val dataRoot = ValidationPath().child("data")
    data.forEach { (id, value) ->
        val dataPath = dataRoot.child(id)
        val match = blocksById[id]
        if (match == null) {
            add(
                issue(
                    dataPath,
                    ValidationCodes.ORPHAN_DATA_ID,
                    "No block with id '$id' in template",
                ),
            )
        } else {
            addAll(match.first.validateData(value, dataPath))
        }
    }
}

private fun validatePageConfig(page: PageConfig, path: ValidationPath): List<ValidationIssue> = buildList {
    val size = page.size
    if (size is CustomPageSize && (size.width <= 0 || size.height <= 0)) {
        add(
            issue(
                path.child("size"),
                ValidationCodes.OUT_OF_RANGE,
                "Page dimensions must be positive millimetres: ${size.width}x${size.height}",
            ),
        )
    }
    page.background?.let { addAll(validateBackground(it, path.child("background"))) }
}

private fun validateBackground(
    background: PageBackgroundConfig,
    path: ValidationPath,
): List<ValidationIssue> = buildList {
    val srcPath = path.child("src")
    if (background.src.isBlank()) {
        add(issue(srcPath, ValidationCodes.INVALID_URI, "Page background src cannot be blank"))
        return@buildList
    }
    if (background.src.any { it < ' ' || it == '' }) {
        add(issue(srcPath, ValidationCodes.INVALID_URI, "Page background src contains control characters"))
        return@buildList
    }
    val scheme = runCatching { URI.create(background.src).scheme?.lowercase() }.getOrNull()
    if (scheme != "http" && scheme != "https" && scheme != "data") {
        add(
            issue(
                srcPath,
                ValidationCodes.INVALID_URI,
                "Page background src must use http, https, or data URI",
            ),
        )
        return@buildList
    }
    if (scheme == "data") {
        val lower = background.src.lowercase()
        if (!lower.startsWith("data:image/") && !lower.startsWith("data:application/pdf;base64,")) {
            add(
                issue(
                    srcPath,
                    ValidationCodes.INVALID_URI,
                    "Page background data URI must be an image or application/pdf base64 URI",
                ),
            )
        }
    }
}
