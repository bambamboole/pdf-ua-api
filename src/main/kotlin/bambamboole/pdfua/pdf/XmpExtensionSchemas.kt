package bambamboole.pdfua.pdf

import bambamboole.pdfua.template.XmpProperty
import bambamboole.pdfua.template.XmpSchema
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.common.PDMetadata
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayOutputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Writes caller-supplied XMP extension schemas into a document's metadata: each schema gets its
 * declaration under `pdfaExtension:schemas` (next to the pdfuaid schema openhtmltopdf already
 * declares) and an `rdf:Description` carrying the property values under the schema's prefix.
 */
object XmpExtensionSchemas {
    private const val RDF_NS = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
    private const val PDFA_EXTENSION_NS = "http://www.aiim.org/pdfa/ns/extension/"
    private const val PDFA_SCHEMA_NS = "http://www.aiim.org/pdfa/ns/schema#"
    private const val PDFA_PROPERTY_NS = "http://www.aiim.org/pdfa/ns/property#"
    private const val DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl"
    private const val EXTERNAL_GENERAL_ENTITIES = "http://xml.org/sax/features/external-general-entities"
    private const val EXTERNAL_PARAMETER_ENTITIES = "http://xml.org/sax/features/external-parameter-entities"

    fun apply(
        document: PDDocument,
        schemas: List<XmpSchema>,
    ) {
        val metadata =
            requireNotNull(document.documentCatalog.metadata) { "The document has no XMP metadata to extend" }
        val xmp = parse(metadata.exportXMPMetadata().use { it.readBytes() })
        val rdf =
            xmp.getElementsByTagNameNS(RDF_NS, "RDF").item(0) as? Element
                ?: throw IllegalStateException("The XMP metadata has no rdf:RDF element")
        val bag = schemasBag(xmp, rdf)

        for (schema in schemas) {
            bag.appendChild(declaration(xmp, schema))
            rdf.appendChild(values(xmp, schema))
        }

        document.documentCatalog.metadata =
            PDMetadata(document).apply { importXMPMetadata(serialize(xmp)) }
    }

    private fun declaration(
        xmp: Document,
        schema: XmpSchema,
    ): Element {
        val declaration = xmp.resource()
        declaration.appendChild(xmp.element(PDFA_SCHEMA_NS, "pdfaSchema:schema", schema.name))
        declaration.appendChild(xmp.element(PDFA_SCHEMA_NS, "pdfaSchema:namespaceURI", schema.namespace))
        declaration.appendChild(xmp.element(PDFA_SCHEMA_NS, "pdfaSchema:prefix", schema.prefix))
        val seq = xmp.createElementNS(RDF_NS, "rdf:Seq")
        for (property in schema.properties) {
            seq.appendChild(propertyDeclaration(xmp, property))
        }
        declaration.appendChild(xmp.createElementNS(PDFA_SCHEMA_NS, "pdfaSchema:property").apply { appendChild(seq) })
        return declaration
    }

    private fun propertyDeclaration(
        xmp: Document,
        property: XmpProperty,
    ): Element =
        xmp.resource().apply {
            appendChild(xmp.element(PDFA_PROPERTY_NS, "pdfaProperty:name", property.name))
            appendChild(xmp.element(PDFA_PROPERTY_NS, "pdfaProperty:valueType", property.valueType))
            appendChild(xmp.element(PDFA_PROPERTY_NS, "pdfaProperty:category", property.category))
            appendChild(xmp.element(PDFA_PROPERTY_NS, "pdfaProperty:description", property.description ?: property.name))
        }

    private fun values(
        xmp: Document,
        schema: XmpSchema,
    ): Element {
        val description = xmp.description()
        description.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:${schema.prefix}", schema.namespace)
        for (property in schema.properties) {
            description.appendChild(xmp.element(schema.namespace, "${schema.prefix}:${property.name}", property.value))
        }
        return description
    }

    private fun schemasBag(
        xmp: Document,
        rdf: Element,
    ): Element {
        val existing = xmp.getElementsByTagNameNS(PDFA_EXTENSION_NS, "schemas").item(0) as? Element
        val existingBag = existing?.getElementsByTagNameNS(RDF_NS, "Bag")?.item(0) as? Element
        if (existingBag != null) {
            return existingBag
        }

        val bag = xmp.createElementNS(RDF_NS, "rdf:Bag")
        val schemas = xmp.createElementNS(PDFA_EXTENSION_NS, "pdfaExtension:schemas").apply { appendChild(bag) }
        val description = xmp.description()
        description.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:pdfaExtension", PDFA_EXTENSION_NS)
        description.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:pdfaSchema", PDFA_SCHEMA_NS)
        description.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:pdfaProperty", PDFA_PROPERTY_NS)
        description.appendChild(schemas)
        rdf.appendChild(description)
        return bag
    }

    private fun Document.description(): Element {
        val description = createElementNS(RDF_NS, "rdf:Description")
        description.setAttributeNS(RDF_NS, "rdf:about", "")
        return description
    }

    private fun Document.resource(): Element {
        val item = createElementNS(RDF_NS, "rdf:li")
        item.setAttributeNS(RDF_NS, "rdf:parseType", "Resource")
        return item
    }

    private fun Document.element(
        namespace: String,
        qualifiedName: String,
        text: String,
    ): Element = createElementNS(namespace, qualifiedName).apply { textContent = text }

    private fun parse(bytes: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        factory.setFeature(DISALLOW_DOCTYPE, true)
        factory.setFeature(EXTERNAL_GENERAL_ENTITIES, false)
        factory.setFeature(EXTERNAL_PARAMETER_ENTITIES, false)
        factory.isXIncludeAware = false
        factory.isExpandEntityReferences = false
        return factory.newDocumentBuilder().parse(bytes.inputStream())
    }

    private fun serialize(xmp: Document): ByteArray {
        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        return ByteArrayOutputStream().use { output ->
            transformer.transform(DOMSource(xmp), StreamResult(output))
            output.toByteArray()
        }
    }
}
