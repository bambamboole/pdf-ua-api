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
 * Generates the canonical JSON Schema for [Template], injected as the `Template` component of the
 * OpenAPI document (see `http/OpenApiSpec.kt`).
 *
 * The schema definitions (`$defs`) are derived directly from the `@Serializable` data classes
 * via [SchemaWalker], driven by `@Schema*` annotations placed on the data class properties
 * (see [SchemaAnnotations.kt]). Things the walker cannot derive — the schema root identity
 * (`$schema`, `$id`, `title`), the const `version: 2`, the `x-pdfUa` runtime-metadata block,
 * the UX-meaningful `block.oneOf` order, and the explicit `fontWeight` `$def` for SDK consumers
 * — live here.
 */
object TemplateJsonSchema {
    private const val TEMPLATE_VERSION = 2
    private const val RENDER_ENDPOINT = "/render/template"

    private val blockOrder = listOf("text", "html", "heading", "image", "key-value", "spacer", "divider", "table")

    fun current(): JsonObject {
        val walker = SchemaWalker()
        walker.prime(serializer<Template>().descriptor)
        val defs = walker.definitions.toMutableMap()

        // Post-process: enforce the documented `block` oneOf order (which is a UX-meaningful sequence
        // — text first, containers last — not the alphabetic order kotlinx.serialization produces).
        defs["block"] =
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
        defs["fontWeight"] = fontWeightDef()

        return buildJsonObject {
            put("\$schema", "https://json-schema.org/draft/2020-12/schema")
            put("\$id", "https://pdf-ua-api.com/schemas/template-v2.json")
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
            put("\$defs", JsonObject(defs))
            put("x-pdfUa", pdfUaMetadata())
        }
    }

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
