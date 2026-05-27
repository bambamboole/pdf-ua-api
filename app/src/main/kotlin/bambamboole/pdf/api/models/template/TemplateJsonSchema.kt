package bambamboole.pdf.api.models.template

import bambamboole.pdf.api.services.BundledFonts
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object TemplateJsonSchema {
    private const val TEMPLATE_VERSION = 1
    private const val RENDER_ENDPOINT = "/render/template"

    private val blockOrder = listOf("text", "html", "heading", "image", "spacer", "divider")

    fun current(): JsonObject =
        buildJsonObject {
            put("\$schema", "https://json-schema.org/draft/2020-12/schema")
            put("\$id", "https://pdf-ua-api.com/schemas/template-v1.json")
            put("title", "Template")
            put("type", "object")
            putJsonArray("required") { add("version") }
            put("x-pdfUa", pdfUaMetadata())
            putJsonObject("properties") {
                put("version", constInteger(TEMPLATE_VERSION))
                put("config", ref("templateConfig"))
                put("fonts", fontsProperty())
                put("attachments", arrayOf(ref("fileAttachment")))
                put("rows", arrayOf(ref("row")))
            }
            put("additionalProperties", false)
            put("\$defs", definitions())
        }

    private fun pdfUaMetadata(): JsonObject =
        buildJsonObject {
            put("kind", "template")
            put("templateVersion", TEMPLATE_VERSION)
            put("renderEndpoint", RENDER_ENDPOINT)
            putJsonArray("templateFields") {
                listOf("version", "config", "fonts", "attachments", "rows").forEach(::add)
            }
            putJsonArray("attachmentFields") {
                listOf("name", "content", "mimeType", "description", "relationship").forEach(::add)
            }
            putJsonArray("externalFontFields") {
                listOf("src", "weight", "style").forEach(::add)
            }
            putJsonArray("bundledFonts") {
                BundledFonts.families.sorted().forEach(::add)
            }
            putJsonArray("blockOrder") {
                blockOrder.forEach(::add)
            }
            putJsonArray("pageFormats") {
                PageFormat.entries.forEach { format ->
                    add(
                        buildJsonObject {
                            put("name", format.serializedName())
                            put("widthMm", format.widthMm)
                            put("heightMm", format.heightMm)
                        },
                    )
                }
            }
        }

    private fun definitions(): JsonObject =
        buildJsonObject {
            put("align", stringEnum("Align", Align.entries.map { it.serializedName() }))
            put("pageFormat", stringEnum("PageFormat", PageFormat.entries.map { it.serializedName() }))
            put("orientation", stringEnum("Orientation", Orientation.entries.map { it.serializedName() }))
            put("dividerStyle", stringEnum("DividerStyle", DividerStyle.entries.map { it.serializedName() }))
            put("pageBackgroundType", stringEnum("PageBackgroundType", PageBackgroundType.entries.map { it.serializedName() }))
            put("typographyConfig", typographyConfig())
            put("spacingConfig", spacingConfig())
            put("blockConfig", blockConfig())
            put(
                "headingConfig",
                extendedBlockConfig(
                    "HeadingConfig",
                    "BlockConfig & { level?: number }",
                    "level" to int(min = 1, max = 6, default = 2),
                ),
            )
            put(
                "imageConfig",
                extendedBlockConfig(
                    "ImageConfig",
                    "BlockConfig & { maxHeight?: number }",
                    "maxHeight" to int(min = 1, default = 60),
                ),
            )
            put(
                "spacerConfig",
                extendedBlockConfig(
                    "SpacerConfig",
                    "BlockConfig & { height?: number }",
                    "height" to int(min = 0, default = 5),
                ),
            )
            put(
                "dividerConfig",
                extendedBlockConfig(
                    "DividerConfig",
                    "BlockConfig & { thickness?: number; lineColor?: string; style?: DividerStyle }",
                    "thickness" to int(min = 0, default = 1),
                    "lineColor" to string(pattern = "^#[0-9A-Fa-f]{3,8}$", default = "#d1d5db"),
                    "style" to ref("dividerStyle"),
                ),
            )
            put("textBlock", block("TextBlock", "text", listOf("text"), "blockConfig", "text" to string()))
            put("htmlBlock", block("HtmlBlock", "html", listOf("html"), "blockConfig", "html" to string()))
            put("headingBlock", block("HeadingBlock", "heading", listOf("text"), "headingConfig", "text" to string()))
            put(
                "imageBlock",
                block(
                    "ImageBlock",
                    "image",
                    listOf("src"),
                    "imageConfig",
                    "src" to string(description = "Public image URL, SVG markup, or uploaded image data URL."),
                    "alt" to string(default = "", description = "Alternative text for screen readers and PDF accessibility."),
                ),
            )
            put("spacerBlock", block("SpacerBlock", "spacer", emptyList(), "spacerConfig"))
            put("dividerBlock", block("DividerBlock", "divider", emptyList(), "dividerConfig"))
            put("block", oneOf(blockOrder.map { ref("${it.camelCase()}Block") }))
            put("row", row())
            put("presetPageSize", presetPageSize())
            put("customPageSize", customPageSize())
            put("pageSize", oneOf(listOf(ref("presetPageSize"), ref("customPageSize")), title = "PageSize"))
            put("pageNumbersConfig", pageNumbersConfig())
            put("pageBackgroundConfig", pageBackgroundConfig())
            put("pageConfig", pageConfig())
            put("templateConfig", templateConfig())
            put("fontFace", fontFace())
            put("fileAttachment", fileAttachment())
        }

    private fun typographyConfig(): JsonObject =
        schemaObject("TypographyConfig") {
            put("family", nullableString(description = "Bundled or external font family key."))
            put("size", nullableInt(min = 1, description = "Font size in points."))
            put("weight", nullableInt(description = "Numeric font weight."))
            put("align", nullableEnum(Align.entries.map { it.serializedName() }, description = "Text alignment for this typography scope."))
            put("color", nullableString(description = "CSS color value used for text."))
        }

    private fun spacingConfig(): JsonObject =
        schemaObject("SpacingConfig") {
            put("top", nullableInt(min = 0, description = "Top spacing in millimetres."))
            put("right", nullableInt(min = 0, description = "Right spacing in millimetres."))
            put("bottom", nullableInt(min = 0, description = "Bottom spacing in millimetres."))
            put("left", nullableInt(min = 0, description = "Left spacing in millimetres."))
        }

    private fun blockConfig(): JsonObject =
        schemaObject(
            "BlockConfig",
            tsType = "{ typography?: TypographyConfig; spacing?: SpacingConfig; width?: string | null; align?: Align | null }",
        ) {
            put("typography", nullableRef("typographyConfig"))
            put("spacing", nullableRef("spacingConfig"))
            put("width", nullableString(description = "CSS width for this block, such as 50%, 80mm, or auto."))
            put(
                "align",
                nullableEnum(
                    Align.entries.map { it.serializedName() },
                    description = "Horizontal placement of this block within its row cell.",
                ),
            )
        }

    private fun extendedBlockConfig(title: String, tsType: String, vararg properties: Pair<String, JsonObject>): JsonObject =
        schemaObject(title, tsType = tsType) {
            put("typography", nullableRef("typographyConfig"))
            put("spacing", nullableRef("spacingConfig"))
            put("width", nullableString(description = "CSS width for this block, such as 50%, 80mm, or auto."))
            put(
                "align",
                nullableEnum(
                    Align.entries.map { it.serializedName() },
                    description = "Horizontal placement of this block within its row cell.",
                ),
            )
            properties.forEach { (name, schema) -> put(name, schema) }
        }

    private fun block(
        title: String,
        type: String,
        requiredFields: List<String>,
        configDefinition: String,
        vararg fields: Pair<String, JsonObject>,
    ): JsonObject =
        buildJsonObject {
            put("title", title)
            put("type", "object")
            putJsonArray("required") {
                add("type")
                requiredFields.forEach(::add)
            }
            putJsonObject("properties") {
                put("type", constString(type))
                put("id", nullableString(description = "Stable block identifier used for runtime data overrides."))
                fields.forEach { (name, schema) -> put(name, schema) }
                put("config", ref(configDefinition))
            }
            put("additionalProperties", false)
        }

    private fun row(): JsonObject =
        schemaObject("Row", required = listOf("blocks")) {
            put("blocks", arrayOf(ref("block")))
        }

    private fun presetPageSize(): JsonObject =
        schemaObject("PresetPageSize") {
            put("format", ref("pageFormat"))
            put("orientation", ref("orientation"))
        }

    private fun customPageSize(): JsonObject =
        schemaObject("CustomPageSize", required = listOf("width", "height")) {
            put("width", int(min = 1))
            put("height", int(min = 1))
        }

    private fun pageNumbersConfig(): JsonObject =
        schemaObject("PageNumbersConfig", tsType = "{ enabled?: boolean; position?: Align }") {
            put("enabled", boolean(default = false))
            put("position", ref("align"))
        }

    private fun pageBackgroundConfig(): JsonObject =
        schemaObject("PageBackgroundConfig", required = listOf("src")) {
            put(
                "src",
                string(
                    minLength = 1,
                    description = "HTTP, HTTPS, or base64 data URI for an image or PDF page background.",
                ),
            )
            put("type", ref("pageBackgroundType"))
        }

    private fun pageConfig(): JsonObject =
        schemaObject(
            "PageConfig",
            tsType = "{ size?: PageSize; locale?: string; margins?: SpacingConfig; " +
                "pageNumbers?: PageNumbersConfig; background?: PageBackgroundConfig | null }",
        ) {
            put("size", ref("pageSize"))
            put("locale", string(default = "de_DE"))
            put("margins", ref("spacingConfig"))
            put("pageNumbers", ref("pageNumbersConfig"))
            put("background", nullableRef("pageBackgroundConfig"))
        }

    private fun templateConfig(): JsonObject =
        schemaObject("TemplateConfig", tsType = "{ page?: PageConfig; typography?: TypographyConfig }") {
            put("page", ref("pageConfig"))
            put("typography", ref("typographyConfig"))
        }

    private fun fontFace(): JsonObject =
        schemaObject("FontFace", required = listOf("src")) {
            put("src", string())
            put("weight", int(default = 400))
            put("style", string(default = "normal"))
        }

    private fun fileAttachment(): JsonObject =
        schemaObject("FileAttachment", required = listOf("name", "content")) {
            put("name", string())
            put("content", string(description = "Base64-encoded file content."))
            put("mimeType", string(default = "application/octet-stream"))
            put("description", nullableString())
            put("relationship", string(default = "Alternative"))
        }

    private fun fontsProperty(): JsonObject =
        buildJsonObject {
            put("type", "object")
            put("description", "External fonts keyed by font family name.")
            put("additionalProperties", ref("fontFace"))
        }

    private fun schemaObject(
        title: String,
        required: List<String> = emptyList(),
        tsType: String? = null,
        properties: JsonObjectBuilder.() -> Unit,
    ): JsonObject =
        buildJsonObject {
            put("title", title)
            put("type", "object")
            if (required.isNotEmpty()) {
                putJsonArray("required") {
                    required.forEach(::add)
                }
            }
            put("properties", buildJsonObject(properties))
            put("additionalProperties", false)
            tsType?.let { put("tsType", it) }
        }

    private fun stringEnum(title: String, values: List<String>): JsonObject =
        buildJsonObject {
            put("title", title)
            put("type", "string")
            putJsonArray("enum") {
                values.forEach(::add)
            }
        }

    private fun nullableEnum(values: List<String>, description: String? = null): JsonObject =
        buildJsonObject {
            putJsonArray("type") {
                add("string")
                add("null")
            }
            putJsonArray("enum") {
                values.forEach(::add)
                add(JsonNull)
            }
            description?.let { put("description", it) }
        }

    private fun oneOf(schemas: List<JsonObject>, title: String? = null): JsonObject =
        buildJsonObject {
            title?.let { put("title", it) }
            putJsonArray("oneOf") {
                schemas.forEach(::add)
            }
        }

    private fun arrayOf(item: JsonObject): JsonObject =
        buildJsonObject {
            put("type", "array")
            put("items", item)
        }

    private fun ref(definition: String): JsonObject =
        buildJsonObject {
            put("\$ref", "#/\$defs/$definition")
        }

    private fun nullableRef(definition: String): JsonObject =
        oneOf(
            listOf(
                ref(definition),
                buildJsonObject { put("type", "null") },
            ),
        )

    private fun constInteger(value: Int): JsonObject =
        buildJsonObject {
            put("const", value)
            put("type", "integer")
        }

    private fun constString(value: String): JsonObject =
        buildJsonObject {
            put("const", value)
            put("type", "string")
        }

    private fun string(
        pattern: String? = null,
        default: String? = null,
        minLength: Int? = null,
        description: String? = null,
    ): JsonObject =
        buildJsonObject {
            put("type", "string")
            pattern?.let { put("pattern", it) }
            default?.let { put("default", it) }
            minLength?.let { put("minLength", it) }
            description?.let { put("description", it) }
        }

    private fun nullableString(description: String? = null): JsonObject =
        buildJsonObject {
            putJsonArray("type") {
                add("string")
                add("null")
            }
            description?.let { put("description", it) }
        }

    private fun int(min: Int? = null, max: Int? = null, default: Int? = null): JsonObject =
        buildJsonObject {
            put("type", "integer")
            min?.let { put("minimum", it) }
            max?.let { put("maximum", it) }
            default?.let { put("default", it) }
        }

    private fun nullableInt(min: Int? = null, description: String? = null): JsonObject =
        buildJsonObject {
            putJsonArray("type") {
                add("integer")
                add("null")
            }
            min?.let { put("minimum", it) }
            description?.let { put("description", it) }
        }

    private fun boolean(default: Boolean? = null): JsonObject =
        buildJsonObject {
            put("type", "boolean")
            default?.let { put("default", it) }
        }

    private fun PageFormat.serializedName(): String =
        PageFormat.serializer().descriptor.getElementName(ordinal)

    private fun Orientation.serializedName(): String =
        Orientation.serializer().descriptor.getElementName(ordinal)

    private fun Align.serializedName(): String =
        Align.serializer().descriptor.getElementName(ordinal)

    private fun DividerStyle.serializedName(): String =
        DividerStyle.serializer().descriptor.getElementName(ordinal)

    private fun PageBackgroundType.serializedName(): String =
        PageBackgroundType.serializer().descriptor.getElementName(ordinal)

    private fun String.camelCase(): String =
        replace(Regex("-([a-z])")) { it.groupValues[1].uppercase() }
}

private typealias JsonObjectBuilder = kotlinx.serialization.json.JsonObjectBuilder
