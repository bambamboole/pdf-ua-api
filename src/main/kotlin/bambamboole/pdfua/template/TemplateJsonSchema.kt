package bambamboole.pdfua.template

import bambamboole.pdfua.fonts.BundledFonts
import bambamboole.pdfua.fonts.FontWeight
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.serializer

/**
 * Generates the JSON Schema served at `/schema` for [Template].
 *
 * The schema definitions (`$defs`) are derived directly from the `@Serializable` data classes
 * via [SchemaWalker], driven by `@Schema*` annotations placed on the data class properties
 * (see [SchemaAnnotations.kt]). Things the walker cannot derive — the schema root identity
 * (`$schema`, `$id`, `title`), the const `version: 1`, the `x-pdfUa` runtime-metadata block,
 * and the legacy `tsType` extension on a handful of `$defs` — live here.
 */
object TemplateJsonSchema {
    private const val TEMPLATE_VERSION = 1
    private const val RENDER_ENDPOINT = "/render/template"

    /** TypeScript type aliases attached to specific `$defs` via the `tsType` extension. */
    private val definitionTsTypes =
        mapOf(
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

    private val blockOrder = listOf("text", "html", "heading", "image", "key-value", "spacer", "divider", "table")

    fun current(): JsonObject {
        val walker = SchemaWalker()
        walker.walkRoot(serializer<Template>().descriptor)
        val rawDefs = walker.definitions.toMutableMap()

        // Post-process: enforce the documented `block` oneOf order (which is a UX-meaningful sequence
        // — text first, containers last — not the alphabetic order kotlinx.serialization produces).
        rawDefs["block"] =
            buildJsonObject {
                put(
                    "oneOf",
                    buildJsonArray {
                        blockOrder.forEach { discriminator ->
                            val defName = kebabToCamel(discriminator) + "Block"
                            add(buildJsonObject { put("\$ref", "#/\$defs/$defName") })
                        }
                    },
                )
            }
        // Register `fontWeight` as a top-level enum $def for SDK consumers that key off it,
        // even though the typographyConfig.weight field inlines the nullable enum directly.
        rawDefs["fontWeight"] = fontWeightDef()

        val defs = applyTsTypeOverrides(rawDefs, definitionTsTypes)
        return buildJsonObject {
            put("\$schema", "https://json-schema.org/draft/2020-12/schema")
            put("\$id", "https://pdf-ua-api.com/schemas/template-v1.json")
            put("title", "Template")
            put("type", "object")
            put(
                "properties",
                buildJsonObject {
                    put(
                        "version",
                        buildJsonObject {
                            put("type", "integer")
                            put("const", TEMPLATE_VERSION)
                        },
                    )
                    put("config", refTo("templateConfig"))
                    put(
                        "fonts",
                        buildJsonObject {
                            put("type", "object")
                            put("description", "External fonts keyed by font family name.")
                            put("additionalProperties", refTo("fontFace"))
                        },
                    )
                    put(
                        "attachments",
                        buildJsonObject {
                            put("type", "array")
                            put("items", refTo("fileAttachment"))
                        },
                    )
                    put(
                        "rows",
                        buildJsonObject {
                            put("type", "array")
                            put("items", refTo("row"))
                        },
                    )
                },
            )
            put("additionalProperties", false)
            put("required", buildJsonArray { add("version") })
            put("\$defs", JsonObject(defs.filterKeys { it != "template" }))
            put("x-pdfUa", pdfUaMetadata())
        }
    }

    private fun kebabToCamel(value: String): String =
        value
            .split('-')
            .mapIndexed { index, segment ->
                if (index == 0) segment else segment.replaceFirstChar { it.uppercase() }
            }.joinToString("")

    @OptIn(ExperimentalSerializationApi::class)
    private fun fontWeightDef(): JsonObject {
        val descriptor = FontWeight.serializer().descriptor
        return buildJsonObject {
            put("type", "string")
            put("title", "FontWeight")
            put(
                "enum",
                buildJsonArray {
                    for (i in 0 until descriptor.elementsCount) add(descriptor.getElementName(i))
                },
            )
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun pageFormatSerialName(format: PageFormat): String = PageFormat.serializer().descriptor.getElementName(format.ordinal)

    private fun applyTsTypeOverrides(
        defs: Map<String, JsonObject>,
        tsTypes: Map<String, String>,
    ): Map<String, JsonObject> {
        if (tsTypes.isEmpty()) return defs
        val result = linkedMapOf<String, JsonObject>()
        for ((name, def) in defs) {
            val ts = tsTypes[name]
            if (ts == null) {
                result[name] = def
            } else {
                result[name] =
                    buildJsonObject {
                        def.forEach { (k, v) -> put(k, v) }
                        put("tsType", ts)
                    }
            }
        }
        return result
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
                listOf("text", "html", "heading", "image", "key-value", "spacer", "divider", "table").forEach(::add)
            }
            putJsonArray("pageFormats") {
                PageFormat.entries.forEach { format ->
                    add(
                        buildJsonObject {
                            put("name", pageFormatSerialName(format))
                            put("widthMm", format.widthMm)
                            put("heightMm", format.heightMm)
                        },
                    )
                }
            }
        }

    private fun refTo(defName: String): JsonObject =
        buildJsonObject {
            put("\$ref", "#/\$defs/$defName")
        }
}
