package bambamboole.pdf.api.models.template

import kotlinx.schema.json.AdditionalPropertiesConstraint
import kotlinx.schema.json.GenericPropertyDefinition
import kotlinx.schema.json.JsonSchema
import kotlinx.schema.json.NumericPropertyDefinition
import kotlinx.schema.json.ObjectPropertyDefinition
import kotlinx.schema.json.OneOfPropertyDefinition
import kotlinx.schema.json.PropertyDefinition
import kotlinx.schema.json.ReferencePropertyDefinition
import kotlinx.schema.json.StringPropertyDefinition
import kotlinx.schema.json.encodeToJsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class TemplateJsonSchemaKotlinxSchemaSpikeTest {

    @Test
    fun expressesTemplatePolymorphismAndAppliesCustomExtensions() {
        val schema = SpikeTemplateSchema.current()

        assertEquals("https://json-schema.org/draft/2020-12/schema", schema["\$schema"]?.jsonPrimitive?.content)
        assertEquals("Template", schema["title"]?.jsonPrimitive?.content)
        assertEquals("template", schema["x-pdfUa"]!!.jsonObject["kind"]!!.jsonPrimitive.content)

        val definitions = schema["\$defs"]!!.jsonObject
        assertEquals(
            listOf(
                "#/\$defs/textBlock",
                "#/\$defs/htmlBlock",
                "#/\$defs/headingBlock",
                "#/\$defs/imageBlock",
                "#/\$defs/spacerBlock",
                "#/\$defs/dividerBlock",
            ),
            definitions["block"]!!.jsonObject["oneOf"]!!.jsonArray.map { it.jsonObject["\$ref"]!!.jsonPrimitive.content },
        )
        assertEquals(
            listOf("#/\$defs/presetPageSize", "#/\$defs/customPageSize"),
            definitions["pageSize"]!!.jsonObject["oneOf"]!!.jsonArray.map { it.jsonObject["\$ref"]!!.jsonPrimitive.content },
        )
        assertEquals(
            listOf("#/\$defs/pageBackgroundConfig", "null"),
            definitions["pageConfig"]!!.jsonObject["properties"]!!.jsonObject["background"]!!.jsonObject["oneOf"]!!.jsonArray.map { option ->
                option.jsonObject["\$ref"]?.jsonPrimitive?.content ?: option.jsonObject["type"]!!.jsonPrimitive.content
            },
        )
        assertEquals(
            "PageBackgroundConfig | null",
            definitions["pageConfig"]!!.jsonObject["properties"]!!.jsonObject["background"]!!.jsonObject["tsType"]!!.jsonPrimitive.content,
        )
    }
}

private object SpikeTemplateSchema {
    private val blockDefinitions = listOf("text", "html", "heading", "image", "spacer", "divider")

    fun current(): JsonObject {
        val schema = JsonSchema(
            schema = "https://json-schema.org/draft/2020-12/schema",
            id = "https://pdf-ua-api.com/schemas/template-v1.json",
            title = "Template",
            type = listOf("object"),
            required = listOf("version"),
            additionalProperties = AdditionalPropertiesConstraint.deny(),
            properties = mapOf(
                "version" to NumericPropertyDefinition(type = listOf("integer"), constValue = JsonPrimitive(1)),
                "config" to ref("templateConfig"),
                "rows" to arrayOf(ref("row")),
            ),
            defs = mapOf(
                "block" to oneOf(blockDefinitions.map { ref("${it}Block") }),
                "textBlock" to block("textBlock", "text", listOf("text"), "text" to string()),
                "htmlBlock" to block("htmlBlock", "html", listOf("html"), "html" to string()),
                "headingBlock" to block("headingBlock", "heading", listOf("text"), "text" to string()),
                "imageBlock" to block("imageBlock", "image", listOf("src"), "src" to string(), "alt" to string()),
                "spacerBlock" to block("spacerBlock", "spacer", emptyList()),
                "dividerBlock" to block("dividerBlock", "divider", emptyList()),
                "row" to obj(
                    required = listOf("blocks"),
                    properties = mapOf("blocks" to arrayOf(ref("block"))),
                ),
                "pageSize" to oneOf(listOf(ref("presetPageSize"), ref("customPageSize")), title = "PageSize"),
                "presetPageSize" to obj(
                    properties = mapOf(
                        "format" to stringEnum(PageFormat.entries.map { PageFormat.serializer().descriptor.getElementName(it.ordinal) }),
                        "orientation" to stringEnum(Orientation.entries.map { Orientation.serializer().descriptor.getElementName(it.ordinal) }),
                    ),
                ),
                "customPageSize" to obj(
                    required = listOf("width", "height"),
                    properties = mapOf(
                        "width" to int(minimum = 1.0),
                        "height" to int(minimum = 1.0),
                    ),
                ),
                "pageBackgroundConfig" to obj(
                    required = listOf("src"),
                    properties = mapOf(
                        "src" to string(minLength = 1),
                        "type" to stringEnum(
                            PageBackgroundType.entries.map {
                                PageBackgroundType.serializer().descriptor.getElementName(it.ordinal)
                            },
                        ),
                    ),
                ),
                "pageConfig" to obj(
                    properties = mapOf(
                        "size" to ref("pageSize"),
                        "background" to oneOf(listOf(ref("pageBackgroundConfig"), nullType())),
                    ),
                ),
                "templateConfig" to obj(properties = mapOf("page" to ref("pageConfig"))),
            ),
        ).encodeToJsonObject()

        return schema.withRootExtensions()
            .withDefinitionExtensions(
                "pageConfig",
                "properties",
                "background",
                "tsType",
                JsonPrimitive("PageBackgroundConfig | null"),
            )
    }

    private fun block(
        title: String,
        type: String,
        requiredFields: List<String>,
        vararg fields: Pair<String, PropertyDefinition>,
    ): ObjectPropertyDefinition =
        obj(
            title = title,
            required = listOf("type") + requiredFields,
            properties = mapOf(
                "type" to stringConst(type),
                "id" to nullableString(),
                *fields,
            ),
        )

    private fun obj(
        title: String? = null,
        required: List<String> = emptyList(),
        properties: Map<String, PropertyDefinition> = emptyMap(),
    ): ObjectPropertyDefinition =
        ObjectPropertyDefinition(
            title = title,
            properties = properties,
            required = required,
            additionalProperties = AdditionalPropertiesConstraint.deny(),
        )

    private fun oneOf(options: List<PropertyDefinition>, title: String? = null): OneOfPropertyDefinition =
        OneOfPropertyDefinition(oneOf = options, title = title)

    private fun ref(definition: String): ReferencePropertyDefinition =
        ReferencePropertyDefinition(ref = "#/\$defs/$definition")

    private fun string(minLength: Int? = null): StringPropertyDefinition =
        StringPropertyDefinition(minLength = minLength)

    private fun nullableString(): GenericPropertyDefinition =
        GenericPropertyDefinition(type = listOf("string", "null"))

    private fun stringConst(value: String): StringPropertyDefinition =
        StringPropertyDefinition(constValue = JsonPrimitive(value))

    private fun stringEnum(values: List<String>): StringPropertyDefinition =
        StringPropertyDefinition(enum = values)

    private fun int(minimum: Double): NumericPropertyDefinition =
        NumericPropertyDefinition(type = listOf("integer"), minimum = minimum)

    private fun arrayOf(item: PropertyDefinition) =
        kotlinx.schema.json.ArrayPropertyDefinition(items = item)

    private fun nullType(): GenericPropertyDefinition =
        GenericPropertyDefinition(type = listOf("null"))

    private fun JsonObject.withRootExtensions(): JsonObject =
        buildJsonObject {
            this@withRootExtensions.forEach { (key, value) -> put(key, value) }
            put(
                "x-pdfUa",
                buildJsonObject {
                    put("kind", "template")
                    put("templateVersion", 1)
                },
            )
        }

    private fun JsonObject.withDefinitionExtensions(
        definitionName: String,
        propertyParentName: String,
        propertyName: String,
        extensionName: String,
        extensionValue: JsonPrimitive,
    ): JsonObject =
        buildJsonObject {
            this@withDefinitionExtensions.forEach { (key, value) ->
                if (key != "\$defs") {
                    put(key, value)
                    return@forEach
                }

                put(
                    "\$defs",
                    buildJsonObject {
                        value.jsonObject.forEach { (definitionKey, definitionValue) ->
                            if (definitionKey != definitionName) {
                                put(definitionKey, definitionValue)
                                return@forEach
                            }

                            put(
                                definitionKey,
                                buildJsonObject {
                                    definitionValue.jsonObject.forEach { (propertyKey, propertyValue) ->
                                        if (propertyKey != propertyParentName) {
                                            put(propertyKey, propertyValue)
                                            return@forEach
                                        }

                                        put(
                                            propertyKey,
                                            buildJsonObject {
                                                propertyValue.jsonObject.forEach { (nestedKey, nestedValue) ->
                                                    if (nestedKey != propertyName) {
                                                        put(nestedKey, nestedValue)
                                                        return@forEach
                                                    }

                                                    put(
                                                        nestedKey,
                                                        buildJsonObject {
                                                            nestedValue.jsonObject.forEach { (finalKey, finalValue) ->
                                                                put(finalKey, finalValue)
                                                            }
                                                            put(extensionName, extensionValue)
                                                        },
                                                    )
                                                }
                                            },
                                        )
                                    }
                                },
                            )
                        }
                    },
                )
            }
        }
}
