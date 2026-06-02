package bambamboole.pdfua.template

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialInfo

/**
 * Project-local annotations that ride along on `SerialDescriptor` (via `@SerialInfo`)
 * to drive the custom JSON Schema generator in [SchemaWalker].
 *
 * They are deliberately small in number: anything more elaborate stays in [TemplateJsonSchema]
 * (the x-pdfUa runtime metadata, tsType overrides, schema-root identity).
 */

@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class SchemaDescription(
    val value: String,
)

@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class SchemaPattern(
    val value: String,
)

@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class SchemaMin(
    val value: Int,
)

@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class SchemaMax(
    val value: Int,
)

@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class SchemaMinLength(
    val value: Int,
)

@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class SchemaIntDefault(
    val value: Int,
)

@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class SchemaStringDefault(
    val value: String,
)

@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class SchemaBoolDefault(
    val value: Boolean,
)

/** Skip this property entirely from the generated schema. */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class SchemaIgnore

/** For `Map<String, T>` properties: constrain the allowed key shape via a regex. */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class SchemaPropertyNames(
    val pattern: String,
)

/** Override the title attached to the property's value schema (e.g. for inline map types). */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class SchemaTitle(
    val value: String,
)

/** Class-level: emit the value verbatim as a `tsType` extension on this `$def`. */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class SchemaTsType(
    val value: String,
)

/**
 * UI grouping hint for builders: emits `x-pdfUaGroup` on the property's schema so a builder can
 * place the field in a Content / Layout / Style / Data panel without hardcoding per-field rules.
 * Use the [SchemaGroups] constants.
 */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class SchemaGroup(
    val value: String,
)

/** Default for an enum-typed property; emitted as `x-pdfUaDefault` alongside the enum `$ref`. */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class SchemaEnumDefault(
    val value: String,
)

/** Allowed values for [SchemaGroup]. */
object SchemaGroups {
    const val CONTENT = "content"
    const val LAYOUT = "layout"
    const val STYLE = "style"
    const val DATA = "data"
}
