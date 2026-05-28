package bambamboole.pdfua.models.template

import bambamboole.pdfua.services.BundledFonts
import kotlinx.schema.json.AdditionalPropertiesConstraint
import kotlinx.schema.json.AdditionalPropertiesSchema
import kotlinx.schema.json.ArrayPropertyDefinition
import kotlinx.schema.json.BooleanPropertyDefinition
import kotlinx.schema.json.GenericPropertyDefinition
import kotlinx.schema.json.JsonSchema
import kotlinx.schema.json.NumericPropertyDefinition
import kotlinx.schema.json.ObjectPropertyDefinition
import kotlinx.schema.json.OneOfPropertyDefinition
import kotlinx.schema.json.PropertyDefinition
import kotlinx.schema.json.ReferencePropertyDefinition
import kotlinx.schema.json.StringPropertyDefinition
import kotlinx.schema.json.encodeToJsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

object TemplateJsonSchema {
    private const val TEMPLATE_VERSION = 1
    private const val RENDER_ENDPOINT = "/render/template"

    private const val KEY_VALUE_FIELD_KEY_PATTERN = "^[A-Za-z][A-Za-z0-9_]*$"

    private val blockOrder = listOf("text", "html", "heading", "image", "key-value", "spacer", "divider", "table")

    private val definitionTsTypes = mapOf(
        "blockConfig" to "{ typography?: TypographyConfig; spacing?: SpacingConfig; width?: string | null; align?: Align | null }",
        "headingConfig" to "BlockConfig & { level?: number }",
        "imageConfig" to "BlockConfig & { maxHeight?: number }",
        "keyValueField" to "{ key: string; label: string }",
        "keyValueConfig" to "BlockConfig & { labelWidth?: string; fields?: KeyValueField[] }",
        "spacerConfig" to "BlockConfig & { height?: number }",
        "dividerConfig" to "BlockConfig & { thickness?: number; lineColor?: string; style?: DividerStyle }",
        "tableConfig" to "BlockConfig & { numberRows?: boolean; columns?: TableColumn[]; style?: TableStyle }",
        "pageFooterConfig" to "{ repeat?: boolean; rows?: Row[] }",
        "pageConfig" to "{ size?: PageSize; locale?: string; margins?: SpacingConfig; " +
            "pageNumbers?: PageNumbersConfig; background?: PageBackgroundConfig | null; footer?: PageFooterConfig }",
        "templateConfig" to "{ page?: PageConfig; typography?: TypographyConfig }",
    )

    fun current(): JsonObject =
        JsonSchema(
            schema = "https://json-schema.org/draft/2020-12/schema",
            id = "https://pdf-ua-api.com/schemas/template-v1.json",
            title = "Template",
            type = listOf("object"),
            required = listOf("version"),
            additionalProperties = AdditionalPropertiesConstraint.deny(),
            properties = mapOf(
                "version" to constInteger(TEMPLATE_VERSION),
                "config" to ref("templateConfig"),
                "fonts" to fontsProperty(),
                "attachments" to arrayOf(ref("fileAttachment")),
                "rows" to arrayOf(ref("row")),
            ),
            defs = definitions(),
        ).encodeToJsonObject()
            .withRootExtension("x-pdfUa", pdfUaMetadata())
            .withDefinitionExtensions(definitionTsTypes)

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

    private fun definitions(): Map<String, PropertyDefinition> =
        linkedMapOf(
            "align" to stringEnum("Align", Align.entries.map { it.serializedName() }),
            "pageFormat" to stringEnum("PageFormat", PageFormat.entries.map { it.serializedName() }),
            "orientation" to stringEnum("Orientation", Orientation.entries.map { it.serializedName() }),
            "dividerStyle" to stringEnum("DividerStyle", DividerStyle.entries.map { it.serializedName() }),
            "tableStyle" to stringEnum("TableStyle", TableStyle.entries.map { it.serializedName() }),
            "pageBackgroundType" to stringEnum("PageBackgroundType", PageBackgroundType.entries.map { it.serializedName() }),
            "typographyConfig" to typographyConfig(),
            "spacingConfig" to spacingConfig(),
            "blockConfig" to blockConfig(),
            "headingConfig" to extendedBlockConfig(
                "HeadingConfig",
                "level" to int(min = 1, max = 6, default = 2),
            ),
            "imageConfig" to extendedBlockConfig(
                "ImageConfig",
                "maxHeight" to int(min = 1, default = 60),
            ),
            "keyValueField" to keyValueField(),
            "keyValueConfig" to extendedBlockConfig(
                "KeyValueConfig",
                "labelWidth" to string(default = "30mm"),
                "fields" to arrayOf(ref("keyValueField")),
            ),
            "spacerConfig" to extendedBlockConfig(
                "SpacerConfig",
                "height" to int(min = 0, default = 5),
            ),
            "dividerConfig" to extendedBlockConfig(
                "DividerConfig",
                "thickness" to int(min = 0, default = 1),
                "lineColor" to string(pattern = "^#[0-9A-Fa-f]{3,8}$", default = "#d1d5db"),
                "style" to ref("dividerStyle"),
            ),
            "tableColumn" to tableColumn(),
            "tableConfig" to extendedBlockConfig(
                "TableConfig",
                "numberRows" to boolean(default = false),
                "columns" to arrayOf(ref("tableColumn")),
                "style" to ref("tableStyle"),
            ),
            "textBlock" to block("TextBlock", "text", listOf("text"), "blockConfig", "text" to string()),
            "htmlBlock" to block("HtmlBlock", "html", listOf("html"), "blockConfig", "html" to string()),
            "headingBlock" to block("HeadingBlock", "heading", listOf("text"), "headingConfig", "text" to string()),
            "imageBlock" to block(
                "ImageBlock",
                "image",
                listOf("src"),
                "imageConfig",
                "src" to string(description = "Public image URL, SVG markup, or uploaded image data URL."),
                "alt" to string(default = "", description = "Alternative text for screen readers and PDF accessibility."),
            ),
            "keyValueBlock" to block(
                "KeyValueBlock",
                "key-value",
                emptyList(),
                "keyValueConfig",
                "values" to keyValueValues(),
            ),
            "spacerBlock" to block("SpacerBlock", "spacer", emptyList(), "spacerConfig"),
            "dividerBlock" to block("DividerBlock", "divider", emptyList(), "dividerConfig"),
            "tableBlock" to block("TableBlock", "table", emptyList(), "tableConfig"),
            "block" to oneOf(blockOrder.map { ref("${it.camelCase()}Block") }),
            "row" to row(),
            "presetPageSize" to presetPageSize(),
            "customPageSize" to customPageSize(),
            "pageSize" to oneOf(listOf(ref("presetPageSize"), ref("customPageSize")), title = "PageSize"),
            "pageNumbersConfig" to pageNumbersConfig(),
            "pageBackgroundConfig" to pageBackgroundConfig(),
            "pageFooterConfig" to pageFooterConfig(),
            "pageConfig" to pageConfig(),
            "templateConfig" to templateConfig(),
            "fontFace" to fontFace(),
            "fileAttachment" to fileAttachment(),
        )

    private fun typographyConfig(): PropertyDefinition =
        schemaObject("TypographyConfig") {
            "family" to nullableString(description = "Bundled or external font family key.")
            "size" to nullableInt(min = 1, description = "Font size in points.")
            "weight" to nullableInt(description = "Numeric font weight.")
            "align" to nullableEnum(Align.entries.map { it.serializedName() }, description = "Text alignment for this typography scope.")
            "color" to nullableString(description = "CSS color value used for text.")
        }

    private fun spacingConfig(): PropertyDefinition =
        schemaObject("SpacingConfig") {
            "top" to nullableInt(min = 0, description = "Top spacing in millimetres.")
            "right" to nullableInt(min = 0, description = "Right spacing in millimetres.")
            "bottom" to nullableInt(min = 0, description = "Bottom spacing in millimetres.")
            "left" to nullableInt(min = 0, description = "Left spacing in millimetres.")
        }

    private fun blockConfig(): PropertyDefinition =
        schemaObject("BlockConfig", baseBlockConfigProperties())

    private fun extendedBlockConfig(title: String, vararg properties: Pair<String, PropertyDefinition>): PropertyDefinition =
        schemaObject(title, baseBlockConfigProperties() + properties)

    private fun keyValueField(): PropertyDefinition =
        schemaObject("KeyValueField", required = listOf("key", "label")) {
            "key" to string(pattern = KEY_VALUE_FIELD_KEY_PATTERN)
            "label" to string()
        }

    private fun keyValueValues(): PropertyDefinition =
        ObjectPropertyDefinition(
            title = "KeyValueValues",
            propertyNames = string(pattern = KEY_VALUE_FIELD_KEY_PATTERN),
            additionalProperties = AdditionalPropertiesSchema(nullableStringValue()),
        )

    private fun baseBlockConfigProperties(): Map<String, PropertyDefinition> =
        linkedMapOf(
            "typography" to nullableRef("typographyConfig"),
            "spacing" to nullableRef("spacingConfig"),
            "width" to nullableString(description = "CSS width for this block, such as 50%, 80mm, or auto."),
            "align" to nullableEnum(
                Align.entries.map { it.serializedName() },
                description = "Horizontal placement of this block within its row cell.",
            ),
        )

    private fun block(
        title: String,
        type: String,
        requiredFields: List<String>,
        configDefinition: String,
        vararg fields: Pair<String, PropertyDefinition>,
    ): PropertyDefinition =
        schemaObject(
            title,
            required = listOf("type") + requiredFields,
            properties = linkedMapOf(
                "type" to constString(type),
                "id" to nullableString(description = "Stable block identifier used for runtime data overrides."),
                *fields,
                "config" to ref(configDefinition),
            ),
        )

    private fun tableColumn(): PropertyDefinition =
        schemaObject("TableColumn", required = listOf("key", "label")) {
            "key" to string(
                pattern = "^[A-Za-z][A-Za-z0-9_]*$",
                description = "Runtime data key used for this table column.",
            )
            "label" to string(description = "Header label rendered for this table column.")
            "align" to nullableEnum(
                Align.entries.map { it.serializedName() },
                description = "Text alignment for this table column.",
            )
            "width" to nullableString(description = "Column width as a CSS width value, such as 20mm or 15%.")
        }

    private fun row(): PropertyDefinition =
        schemaObject("Row", required = listOf("blocks")) {
            "blocks" to arrayOf(ref("block"))
        }

    private fun presetPageSize(): PropertyDefinition =
        schemaObject("PresetPageSize") {
            "format" to ref("pageFormat")
            "orientation" to ref("orientation")
        }

    private fun customPageSize(): PropertyDefinition =
        schemaObject("CustomPageSize", required = listOf("width", "height")) {
            "width" to int(min = 1)
            "height" to int(min = 1)
        }

    private fun pageNumbersConfig(): PropertyDefinition =
        schemaObject("PageNumbersConfig") {
            "enabled" to boolean(default = false)
            "position" to ref("align")
        }

    private fun pageBackgroundConfig(): PropertyDefinition =
        schemaObject("PageBackgroundConfig", required = listOf("src")) {
            "src" to string(
                minLength = 1,
                description = "HTTP, HTTPS, or base64 data URI for an image or PDF page background.",
            )
            "type" to ref("pageBackgroundType")
        }

    private fun pageFooterConfig(): PropertyDefinition =
        schemaObject("PageFooterConfig") {
            "repeat" to boolean(default = true)
            "rows" to arrayOf(ref("row"))
        }

    private fun pageConfig(): PropertyDefinition =
        schemaObject("PageConfig") {
            "size" to ref("pageSize")
            "locale" to string(default = "de_DE")
            "margins" to ref("spacingConfig")
            "pageNumbers" to ref("pageNumbersConfig")
            "background" to nullableRef("pageBackgroundConfig")
            "footer" to ref("pageFooterConfig")
        }

    private fun templateConfig(): PropertyDefinition =
        schemaObject("TemplateConfig") {
            "page" to ref("pageConfig")
            "typography" to ref("typographyConfig")
        }

    private fun fontFace(): PropertyDefinition =
        schemaObject("FontFace", required = listOf("src")) {
            "src" to string()
            "weight" to int(default = 400)
            "style" to string(default = "normal")
        }

    private fun fileAttachment(): PropertyDefinition =
        schemaObject("FileAttachment", required = listOf("name", "content")) {
            "name" to string()
            "content" to string(description = "Base64-encoded file content.")
            "mimeType" to string(default = "application/octet-stream")
            "description" to nullableString()
            "relationship" to string(default = "Alternative")
        }

    private fun fontsProperty(): PropertyDefinition =
        ObjectPropertyDefinition(
            description = "External fonts keyed by font family name.",
            additionalProperties = AdditionalPropertiesSchema(ref("fontFace")),
        )

    private fun schemaObject(
        title: String,
        properties: Map<String, PropertyDefinition>,
        required: List<String> = emptyList(),
    ): PropertyDefinition =
        ObjectPropertyDefinition(
            title = title,
            properties = properties,
            required = required.ifEmpty { null },
            additionalProperties = AdditionalPropertiesConstraint.deny(),
        )

    private fun schemaObject(
        title: String,
        required: List<String> = emptyList(),
        properties: SchemaPropertiesBuilder.() -> Unit,
    ): PropertyDefinition =
        schemaObject(title, SchemaPropertiesBuilder().apply(properties).build(), required)

    private fun stringEnum(title: String, values: List<String>): PropertyDefinition =
        StringPropertyDefinition(title = title, enum = values)

    private fun nullableEnum(values: List<String>, description: String? = null): PropertyDefinition =
        GenericPropertyDefinition(
            type = listOf("string", "null"),
            enum = values.map(::JsonPrimitive) + JsonNull,
            description = description,
        )

    private fun oneOf(schemas: List<PropertyDefinition>, title: String? = null): PropertyDefinition =
        OneOfPropertyDefinition(oneOf = schemas, title = title)

    private fun arrayOf(item: PropertyDefinition): PropertyDefinition =
        ArrayPropertyDefinition(items = item)

    private fun ref(definition: String): PropertyDefinition =
        ReferencePropertyDefinition(ref = "#/\$defs/$definition")

    private fun nullableRef(definition: String): PropertyDefinition =
        oneOf(listOf(ref(definition), nullType()))

    private fun nullType(): PropertyDefinition =
        GenericPropertyDefinition(type = listOf("null"))

    private fun constInteger(value: Int): PropertyDefinition =
        NumericPropertyDefinition(type = listOf("integer"), constValue = JsonPrimitive(value))

    private fun constString(value: String): PropertyDefinition =
        StringPropertyDefinition(constValue = JsonPrimitive(value))

    private fun string(
        pattern: String? = null,
        default: String? = null,
        minLength: Int? = null,
        description: String? = null,
    ): PropertyDefinition =
        StringPropertyDefinition(
            pattern = pattern,
            default = default?.let(::JsonPrimitive),
            minLength = minLength,
            description = description,
        )

    private fun nullableString(description: String? = null): PropertyDefinition =
        GenericPropertyDefinition(type = listOf("string", "null"), description = description)

    private fun nullableStringValue(): PropertyDefinition =
        GenericPropertyDefinition(type = listOf("string", "null"))

    private fun int(min: Int? = null, max: Int? = null, default: Int? = null): PropertyDefinition =
        NumericPropertyDefinition(
            type = listOf("integer"),
            minimum = min?.toDouble(),
            maximum = max?.toDouble(),
            default = default?.let(::JsonPrimitive),
        )

    private fun nullableInt(min: Int? = null, description: String? = null): PropertyDefinition =
        GenericPropertyDefinition(
            type = listOf("integer", "null"),
            minimum = min?.toDouble(),
            description = description,
        )

    private fun boolean(default: Boolean? = null): PropertyDefinition =
        BooleanPropertyDefinition(default = default?.let(::JsonPrimitive))

    private fun JsonObject.withRootExtension(name: String, value: JsonElement): JsonObject =
        buildJsonObject {
            this@withRootExtension.forEach { (key, existingValue) -> put(key, existingValue) }
            put(name, value)
        }

    private fun JsonObject.withDefinitionExtensions(tsTypes: Map<String, String>): JsonObject =
        buildJsonObject {
            this@withDefinitionExtensions.forEach { (key, value) ->
                if (key == "\$defs") {
                    put(key, value.jsonObject.withTsTypes(tsTypes))
                } else {
                    put(key, value)
                }
            }
        }

    private fun JsonObject.withTsTypes(tsTypes: Map<String, String>): JsonObject =
        buildJsonObject {
            this@withTsTypes.forEach { (name, definition) ->
                val tsType = tsTypes[name]
                if (tsType == null) {
                    put(name, definition)
                } else {
                    put(
                        name,
                        buildJsonObject {
                            definition.jsonObject.forEach { (key, value) -> put(key, value) }
                            put("tsType", tsType)
                        },
                    )
                }
            }
        }

    private fun PageFormat.serializedName(): String =
        PageFormat.serializer().descriptor.getElementName(ordinal)

    private fun Orientation.serializedName(): String =
        Orientation.serializer().descriptor.getElementName(ordinal)

    private fun Align.serializedName(): String =
        Align.serializer().descriptor.getElementName(ordinal)

    private fun DividerStyle.serializedName(): String =
        DividerStyle.serializer().descriptor.getElementName(ordinal)

    private fun TableStyle.serializedName(): String =
        TableStyle.serializer().descriptor.getElementName(ordinal)

    private fun PageBackgroundType.serializedName(): String =
        PageBackgroundType.serializer().descriptor.getElementName(ordinal)

    private fun String.camelCase(): String =
        replace(Regex("-([a-z])")) { it.groupValues[1].uppercase() }
}

private class SchemaPropertiesBuilder {
    private val properties = linkedMapOf<String, PropertyDefinition>()

    infix fun String.to(schema: PropertyDefinition) {
        properties[this] = schema
    }

    fun build(): Map<String, PropertyDefinition> = properties
}
