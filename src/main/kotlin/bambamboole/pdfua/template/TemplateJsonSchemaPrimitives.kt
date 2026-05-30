package bambamboole.pdfua.template

import bambamboole.pdfua.fonts.FontWeight
import kotlinx.schema.json.AdditionalPropertiesConstraint
import kotlinx.schema.json.ArrayPropertyDefinition
import kotlinx.schema.json.BooleanPropertyDefinition
import kotlinx.schema.json.GenericPropertyDefinition
import kotlinx.schema.json.NumericPropertyDefinition
import kotlinx.schema.json.ObjectPropertyDefinition
import kotlinx.schema.json.OneOfPropertyDefinition
import kotlinx.schema.json.PropertyDefinition
import kotlinx.schema.json.ReferencePropertyDefinition
import kotlinx.schema.json.StringPropertyDefinition
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

// Primitive schema constructors ---------------------------------------------

internal fun schemaObject(
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

internal fun schemaObject(
    title: String,
    required: List<String> = emptyList(),
    properties: SchemaPropertiesBuilder.() -> Unit,
): PropertyDefinition = schemaObject(title, SchemaPropertiesBuilder().apply(properties).build(), required)

internal fun stringEnum(
    title: String,
    values: List<String>,
): PropertyDefinition = StringPropertyDefinition(title = title, enum = values)

internal fun nullableEnum(
    values: List<String>,
    description: String? = null,
): PropertyDefinition =
    GenericPropertyDefinition(
        type = listOf("string", "null"),
        enum = values.map(::JsonPrimitive) + JsonNull,
        description = description,
    )

internal fun oneOf(
    schemas: List<PropertyDefinition>,
    title: String? = null,
): PropertyDefinition = OneOfPropertyDefinition(oneOf = schemas, title = title)

internal fun arrayOf(item: PropertyDefinition): PropertyDefinition = ArrayPropertyDefinition(items = item)

internal fun ref(definition: String): PropertyDefinition = ReferencePropertyDefinition(ref = "#/\$defs/$definition")

internal fun nullableRef(definition: String): PropertyDefinition = oneOf(listOf(ref(definition), nullType()))

internal fun nullType(): PropertyDefinition = GenericPropertyDefinition(type = listOf("null"))

@Suppress("MaxLineLength")
internal fun constInteger(value: Int): PropertyDefinition = NumericPropertyDefinition(type = listOf("integer"), constValue = JsonPrimitive(value))

internal fun constString(value: String): PropertyDefinition = StringPropertyDefinition(constValue = JsonPrimitive(value))

internal fun string(
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

@Suppress("MaxLineLength")
internal fun nullableString(description: String? = null): PropertyDefinition = GenericPropertyDefinition(type = listOf("string", "null"), description = description)

internal fun nullableStringValue(): PropertyDefinition = GenericPropertyDefinition(type = listOf("string", "null"))

internal fun int(
    min: Int? = null,
    max: Int? = null,
    default: Int? = null,
): PropertyDefinition =
    NumericPropertyDefinition(
        type = listOf("integer"),
        minimum = min?.toDouble(),
        maximum = max?.toDouble(),
        default = default?.let(::JsonPrimitive),
    )

internal fun nullableInt(
    min: Int? = null,
    description: String? = null,
): PropertyDefinition =
    GenericPropertyDefinition(
        type = listOf("integer", "null"),
        minimum = min?.toDouble(),
        description = description,
    )

internal fun boolean(default: Boolean? = null): PropertyDefinition = BooleanPropertyDefinition(default = default?.let(::JsonPrimitive))

// JsonObject extensions -----------------------------------------------------

internal fun JsonObject.withRootExtension(
    name: String,
    value: JsonElement,
): JsonObject =
    buildJsonObject {
        this@withRootExtension.forEach { (key, existingValue) -> put(key, existingValue) }
        put(name, value)
    }

internal fun JsonObject.withDefinitionExtensions(tsTypes: Map<String, String>): JsonObject =
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

// Enum serialized-name helpers ----------------------------------------------

internal fun PageFormat.serializedName(): String = PageFormat.serializer().descriptor.getElementName(ordinal)

internal fun Orientation.serializedName(): String = Orientation.serializer().descriptor.getElementName(ordinal)

internal fun Align.serializedName(): String = Align.serializer().descriptor.getElementName(ordinal)

internal fun DividerStyle.serializedName(): String = DividerStyle.serializer().descriptor.getElementName(ordinal)

internal fun TableStyle.serializedName(): String = TableStyle.serializer().descriptor.getElementName(ordinal)

internal fun PageBackgroundType.serializedName(): String = PageBackgroundType.serializer().descriptor.getElementName(ordinal)

internal fun FontWeight.serializedName(): String = FontWeight.serializer().descriptor.getElementName(ordinal)

internal fun String.camelCase(): String = replace(Regex("-([a-z])")) { it.groupValues[1].uppercase() }

internal class SchemaPropertiesBuilder {
    private val properties = linkedMapOf<String, PropertyDefinition>()

    infix fun String.to(schema: PropertyDefinition) {
        properties[this] = schema
    }

    fun build(): Map<String, PropertyDefinition> = properties
}
