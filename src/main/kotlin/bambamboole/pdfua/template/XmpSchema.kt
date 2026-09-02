package bambamboole.pdfua.template

import kotlinx.serialization.Serializable

/**
 * A custom XMP extension schema the renderer declares in the PDF/A metadata and fills with the given
 * property values. PDF/A requires every non-standard XMP property to be declared this way; e-invoice
 * formats such as Factur-X/ZUGFeRD (`fx`) or Order-X build on exactly this mechanism.
 */
@Serializable
data class XmpSchema(
    @SchemaDescription("Schema namespace URI; must end with '#' or '/'.")
    val namespace: String,
    @SchemaDescription("Namespace prefix used for the property elements, e.g. fx.")
    val prefix: String,
    @SchemaDescription("Human-readable schema name written into the PDF/A extension declaration.")
    val name: String,
    val properties: List<XmpProperty>,
)

@Serializable
data class XmpProperty(
    val name: String,
    val value: String,
    @SchemaDescription("PDF/A value type of the property.")
    @SchemaStringDefault("Text")
    val valueType: String = "Text",
    @SchemaDescription("Whether the value is derived from the file (internal) or supplied by the author (external).")
    @SchemaStringDefault("external")
    val category: String = "external",
    @SchemaDescription("Description written into the extension declaration; defaults to the property name.")
    val description: String? = null,
)
