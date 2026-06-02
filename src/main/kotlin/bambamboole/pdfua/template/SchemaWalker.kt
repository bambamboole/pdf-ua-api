package bambamboole.pdfua.template

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Walks a kotlinx.serialization [SerialDescriptor] and produces a Draft 2020-12 JSON Schema
 * `$def` map keyed by the camelCased class name. The same walker is reused for property values
 * (returning a `$ref` instead of registering a new `$def`).
 *
 * Constraints and metadata come from `@Schema*` annotations attached to data class properties
 * (see [SchemaAnnotations.kt]). The walker has special-case handling for:
 *  - [PageSize] — a sealed interface with [PageSizeSerializer] (a `JsonContentPolymorphicSerializer`)
 *    whose descriptor cannot be introspected. We render it from the static [pageSizeSubtypes] list.
 *  - Polymorphic sealed types (Block) — each subtype gets a `type` const auto-injected from its
 *    `@SerialName`.
 */
@OptIn(ExperimentalSerializationApi::class)
class SchemaWalker {
    private val defs = linkedMapOf<String, JsonObject>()
    private val inProgress = mutableSetOf<String>()

    /** Final `$defs` map in traversal order. */
    val definitions: Map<String, JsonObject> get() = defs

    /**
     * Registers every type transitively reachable from [rootDescriptor]'s elements as a `$def`,
     * but does *not* register [rootDescriptor] itself. The caller (the orchestrator) builds the
     * root schema by hand because the root has special structure (`const version: 1`, the
     * `x-pdfUa` extension, etc.) that the walker doesn't generate.
     */
    fun prime(rootDescriptor: SerialDescriptor) {
        for (i in 0 until rootDescriptor.elementsCount) {
            val elementDesc = rootDescriptor.getElementDescriptor(i)
            val elementAnnotations = rootDescriptor.getElementAnnotations(i)
            if (elementAnnotations.any { it is SchemaIgnore }) continue
            // Walk for its side-effect on `defs`; the returned JsonObject is the property's inline schema
            // which the caller doesn't need here (the orchestrator hand-rolls the root's `properties` map).
            valueSchema(elementDesc, elementAnnotations)
        }
    }

    /**
     * Returns the schema fragment for [descriptor] used as a property value:
     * a `$ref` for classes/enums (which are registered in `$defs`), or an inline primitive/list/map schema.
     */
    private fun valueSchema(
        descriptor: SerialDescriptor,
        propertyAnnotations: List<Annotation>,
    ): JsonObject {
        if (descriptor.isNullable) return nullableSchema(descriptor, propertyAnnotations)
        return nonNullSchema(descriptor, propertyAnnotations)
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun nonNullSchema(
        descriptor: SerialDescriptor,
        propertyAnnotations: List<Annotation>,
    ): JsonObject =
        when (descriptor.kind) {
            PrimitiveKind.STRING -> {
                buildJsonObject {
                    put("type", "string")
                    propertyAnnotations.applyStringConstraints(this)
                }
            }

            PrimitiveKind.INT, PrimitiveKind.LONG, PrimitiveKind.SHORT, PrimitiveKind.BYTE -> {
                buildJsonObject {
                    put("type", "integer")
                    propertyAnnotations.applyIntConstraints(this)
                }
            }

            PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> {
                buildJsonObject {
                    put("type", "number")
                    propertyAnnotations.applyIntConstraints(this)
                }
            }

            PrimitiveKind.BOOLEAN -> {
                buildJsonObject {
                    put("type", "boolean")
                    propertyAnnotations.applyBoolConstraints(this)
                }
            }

            PrimitiveKind.CHAR -> {
                buildJsonObject { put("type", "string") }
            }

            SerialKind.ENUM -> {
                registerEnum(descriptor)
                val ref = refTo(defName(descriptor))
                // Namespaced (x-) so json-schema-to-typescript keeps the enum's named alias clean —
                // a standard `default` sibling of `$ref` makes it emit duplicate aliases (DividerStyle1).
                propertyAnnotations
                    .filterIsInstance<SchemaEnumDefault>()
                    .firstOrNull()
                    ?.value
                    ?.let { JsonObject(ref + ("x-pdfUaDefault" to JsonPrimitive(it))) }
                    ?: ref
            }

            StructureKind.LIST -> {
                val item = descriptor.getElementDescriptor(0)
                buildJsonObject {
                    put("type", "array")
                    put("items", valueSchema(item, emptyList()))
                }
            }

            StructureKind.MAP -> {
                mapSchema(descriptor, propertyAnnotations)
            }

            StructureKind.CLASS, StructureKind.OBJECT -> {
                if (descriptor.isPageSize()) {
                    registerPageSize()
                    refTo("pageSize")
                } else {
                    registerClass(descriptor)
                    refTo(defName(descriptor))
                }
            }

            PolymorphicKind.SEALED -> {
                if (descriptor.isPageSize()) {
                    registerPageSize()
                    refTo("pageSize")
                } else {
                    registerSealed(descriptor)
                    refTo(defName(descriptor))
                }
            }

            PolymorphicKind.OPEN -> {
                error("Open polymorphism not supported: ${descriptor.serialName}")
            }

            SerialKind.CONTEXTUAL -> {
                error("Contextual serializer not supported: ${descriptor.serialName}")
            }
        }

    private fun nullableSchema(
        descriptor: SerialDescriptor,
        annotations: List<Annotation>,
    ): JsonObject =
        when (descriptor.kind) {
            PrimitiveKind.STRING,
            PrimitiveKind.INT,
            PrimitiveKind.LONG,
            PrimitiveKind.SHORT,
            PrimitiveKind.BYTE,
            PrimitiveKind.BOOLEAN,
            -> {
                withNullType(nonNullSchema(descriptor, annotations))
            }

            SerialKind.ENUM -> {
                buildJsonObject {
                    put(
                        "type",
                        buildJsonArray {
                            add("string")
                            add("null")
                        },
                    )
                    annotations.applyStringConstraints(this)
                    put(
                        "enum",
                        buildJsonArray {
                            for (i in 0 until descriptor.elementsCount) add(descriptor.getElementName(i))
                            add(JsonNull)
                        },
                    )
                }
            }

            else -> {
                // For nullable class/list/map refs, emit oneOf [<inner>, null].
                buildJsonObject {
                    put(
                        "oneOf",
                        buildJsonArray {
                            add(nonNullSchema(descriptor, annotations))
                            add(buildJsonObject { put("type", "null") })
                        },
                    )
                }
            }
        }

    private fun mapSchema(
        descriptor: SerialDescriptor,
        propertyAnnotations: List<Annotation>,
    ): JsonObject {
        val value = descriptor.getElementDescriptor(1)
        val title = propertyAnnotations.filterIsInstance<SchemaTitle>().firstOrNull()?.value
        val propertyNamesPattern = propertyAnnotations.filterIsInstance<SchemaPropertyNames>().firstOrNull()?.pattern
        val description = propertyAnnotations.filterIsInstance<SchemaDescription>().firstOrNull()?.value
        return buildJsonObject {
            put("type", "object")
            if (title != null) put("title", title)
            if (description != null) put("description", description)
            if (propertyNamesPattern != null) {
                put(
                    "propertyNames",
                    buildJsonObject {
                        put("type", "string")
                        put("pattern", propertyNamesPattern)
                    },
                )
            }
            put("additionalProperties", valueSchema(value, emptyList()))
        }
    }

    // ----- registration ------------------------------------------------------

    private fun registerEnum(descriptor: SerialDescriptor) {
        val name = defName(descriptor)
        if (name in defs) return
        defs[name] =
            buildJsonObject {
                put("type", "string")
                put("title", titleFor(descriptor))
                put(
                    "enum",
                    buildJsonArray {
                        for (i in 0 until descriptor.elementsCount) add(descriptor.getElementName(i))
                    },
                )
            }
    }

    private fun registerClass(descriptor: SerialDescriptor) {
        val name = defName(descriptor)
        if (name in defs || name in inProgress) return
        inProgress += name

        val title = titleFor(descriptor)
        val properties = linkedMapOf<String, JsonObject>()
        val required = mutableListOf<String>()
        for (i in 0 until descriptor.elementsCount) {
            val elementName = descriptor.getElementName(i)
            val elementDesc = descriptor.getElementDescriptor(i)
            val elementAnnotations = descriptor.getElementAnnotations(i)
            if (elementAnnotations.any { it is SchemaIgnore }) continue

            properties[elementName] = applyGroup(valueSchema(elementDesc, elementAnnotations), elementAnnotations)
            // Required = no default + not nullable. We approximate with `!isElementOptional`.
            if (!descriptor.isElementOptional(i) && !elementDesc.isNullable) {
                required += elementName
            }
        }

        val tsType =
            descriptor.annotations
                .filterIsInstance<SchemaTsType>()
                .firstOrNull()
                ?.value

        defs[name] =
            buildJsonObject {
                put("type", "object")
                put("title", title)
                put("properties", JsonObject(properties))
                if (required.isNotEmpty()) {
                    put("required", buildJsonArray { required.forEach { add(it) } })
                }
                put("additionalProperties", false)
                if (tsType != null) put("tsType", tsType)
            }
        inProgress -= name
    }

    private fun registerSealed(descriptor: SerialDescriptor) {
        val name = defName(descriptor)
        if (name in defs || name in inProgress) return
        inProgress += name

        // kotlinx.serialization sealed serializer descriptor structure:
        //   elementsCount = 2
        //   element 0: discriminator (typically "type"), enum of subclass names
        //   element 1: value, with elements = subtype descriptors keyed by their @SerialName
        require(descriptor.elementsCount == 2 && descriptor.getElementName(1) == "value") {
            "Expected sealed descriptor with element[1] = 'value', got ${descriptor.elementsCount} elements " +
                "(element 1 named '${if (descriptor.elementsCount > 1) descriptor.getElementName(1) else "<none>"}') " +
                "for ${descriptor.serialName}"
        }
        val valueDesc = descriptor.getElementDescriptor(1)
        val subtypeRefs = mutableListOf<JsonObject>()
        for (i in 0 until valueDesc.elementsCount) {
            val subtypeName = valueDesc.getElementName(i)
            val subtypeDesc = valueDesc.getElementDescriptor(i)
            val subtypeDefName = sealedSubtypeDefName(subtypeName, parentDefName = name)
            registerSealedSubtype(subtypeDesc, subtypeName, subtypeDefName)
            subtypeRefs += refTo(subtypeDefName)
        }
        defs[name] = buildJsonObject { put("oneOf", buildJsonArray { subtypeRefs.forEach { add(it) } }) }
        inProgress -= name
    }

    /** Registers a sealed subtype under [defNameOverride], prepending the auto-injected `type` const. */
    private fun registerSealedSubtype(
        descriptor: SerialDescriptor,
        discriminator: String,
        defNameOverride: String,
    ) {
        val name = defNameOverride
        if (name in defs || name in inProgress) return
        inProgress += name

        val properties = linkedMapOf<String, JsonObject>()
        properties["type"] =
            buildJsonObject {
                put("type", "string")
                put("const", discriminator)
            }
        val required = mutableListOf("type")
        for (i in 0 until descriptor.elementsCount) {
            val elementName = descriptor.getElementName(i)
            val elementDesc = descriptor.getElementDescriptor(i)
            val elementAnnotations = descriptor.getElementAnnotations(i)
            val skip = elementAnnotations.any { it is SchemaIgnore } || elementName == "type"
            if (skip) continue
            properties[elementName] = applyGroup(valueSchema(elementDesc, elementAnnotations), elementAnnotations)
            if (!descriptor.isElementOptional(i) && !elementDesc.isNullable) {
                required += elementName
            }
        }

        val tsType =
            descriptor.annotations
                .filterIsInstance<SchemaTsType>()
                .firstOrNull()
                ?.value

        defs[name] =
            buildJsonObject {
                put("type", "object")
                put("title", sealedSubtypeTitle(defNameOverride))
                put("properties", JsonObject(properties))
                put("required", buildJsonArray { required.forEach { add(it) } })
                put("additionalProperties", false)
                if (tsType != null) put("tsType", tsType)
            }
        inProgress -= name
    }

    /**
     * Converts a sealed subtype's `@SerialName` discriminator (e.g. `"key-value"`) to a `$def` key
     * (e.g. `"keyValueBlock"`). Sealed subtypes don't expose their original class name through the
     * descriptor when `@SerialName` is set, so we synthesise it using a convention: kebab-case → camelCase
     * with a `"Block"` suffix when the parent is the `block` definition.
     */
    private fun sealedSubtypeDefName(
        discriminator: String,
        parentDefName: String,
    ): String {
        val camel = kebabToCamel(discriminator)
        return when (parentDefName) {
            "block" -> camel + "Block"
            else -> camel
        }
    }

    private fun sealedSubtypeTitle(defName: String): String = defName.replaceFirstChar { it.uppercase() }

    private fun kebabToCamel(value: String): String =
        value
            .split('-')
            .mapIndexed { index, segment ->
                if (index == 0) segment else segment.replaceFirstChar { it.uppercase() }
            }.joinToString("")

    /** Manual registration for `PageSize` which uses a `JsonContentPolymorphicSerializer`. */
    private fun registerPageSize() {
        if ("pageSize" in defs) return
        registerClass(kotlinx.serialization.serializer<PresetPageSize>().descriptor)
        registerClass(kotlinx.serialization.serializer<CustomPageSize>().descriptor)
        defs["pageSize"] =
            buildJsonObject {
                put(
                    "oneOf",
                    buildJsonArray {
                        add(refTo("presetPageSize"))
                        add(refTo("customPageSize"))
                    },
                )
                put("title", "PageSize")
            }
    }

    // ----- helpers -----------------------------------------------------------

    private fun refTo(defName: String): JsonObject =
        buildJsonObject {
            put("\$ref", "#/\$defs/$defName")
        }

    private fun List<Annotation>.applyStringConstraints(builder: kotlinx.serialization.json.JsonObjectBuilder) {
        filterIsInstance<SchemaDescription>().firstOrNull()?.let { builder.put("description", it.value) }
        filterIsInstance<SchemaPattern>().firstOrNull()?.let { builder.put("pattern", it.value) }
        filterIsInstance<SchemaStringDefault>().firstOrNull()?.let { builder.put("x-pdfUaDefault", it.value) }
        filterIsInstance<SchemaMinLength>().firstOrNull()?.let { builder.put("minLength", it.value) }
    }

    private fun List<Annotation>.applyIntConstraints(builder: kotlinx.serialization.json.JsonObjectBuilder) {
        filterIsInstance<SchemaDescription>().firstOrNull()?.let { builder.put("description", it.value) }
        filterIsInstance<SchemaIntDefault>().firstOrNull()?.let { builder.put("x-pdfUaDefault", it.value) }
        filterIsInstance<SchemaMin>().firstOrNull()?.let { builder.put("minimum", it.value.toDouble()) }
        filterIsInstance<SchemaMax>().firstOrNull()?.let { builder.put("maximum", it.value.toDouble()) }
    }

    private fun List<Annotation>.applyBoolConstraints(builder: kotlinx.serialization.json.JsonObjectBuilder) {
        filterIsInstance<SchemaDescription>().firstOrNull()?.let { builder.put("description", it.value) }
        filterIsInstance<SchemaBoolDefault>().firstOrNull()?.let { builder.put("x-pdfUaDefault", it.value) }
    }

    private fun titleFor(descriptor: SerialDescriptor): String = descriptor.serialName.removeSuffix("?").substringAfterLast('.')

    private fun defName(descriptor: SerialDescriptor): String {
        val short = descriptor.serialName.removeSuffix("?").substringAfterLast('.')
        return short.replaceFirstChar { it.lowercase() }
    }

    /**
     * Detects the [PageSize] custom polymorphic serializer's descriptor. It surfaces as a
     * `PolymorphicKind.SEALED` descriptor with `elementsCount == 0` (the JsonContentPolymorphicSerializer
     * marker) whose `serialName` contains `<PageSize>`.
     */
    private fun SerialDescriptor.isPageSize(): Boolean = serialName.endsWith("<PageSize>")
}

/** Adds the `x-pdfUaGroup` UI hint to a property's schema when a [SchemaGroup] annotation is present. */
private fun applyGroup(
    schema: JsonObject,
    annotations: List<Annotation>,
): JsonObject {
    val group = annotations.filterIsInstance<SchemaGroup>().firstOrNull()?.value ?: return schema
    return JsonObject(schema + ("x-pdfUaGroup" to JsonPrimitive(group)))
}

/** Rewrites a non-null primitive schema's scalar `type` to the nullable `[type, "null"]` form. */
private fun withNullType(base: JsonObject): JsonObject {
    val typeName = (base.getValue("type") as JsonPrimitive).content
    return JsonObject(
        base + (
            "type" to
                buildJsonArray {
                    add(typeName)
                    add("null")
                }
        ),
    )
}
