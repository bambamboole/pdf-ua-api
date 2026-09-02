package bambamboole.pdfua.pdf

import bambamboole.pdfua.template.XmpProperty
import bambamboole.pdfua.template.XmpSchema
import org.apache.pdfbox.Loader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class XmpExtensionSchemasTest {
    private val html = "<html lang=\"de\"><head><title>Rechnung</title></head><body><h1>Rechnung</h1></body></html>"

    private fun facturX(level: String = "EN 16931") =
        XmpSchema(
            namespace = "urn:factur-x:pdfa:CrossIndustryDocument:invoice:1p0#",
            prefix = "fx",
            name = "Factur-X PDFA Extension Schema",
            properties =
                listOf(
                    XmpProperty("DocumentType", "INVOICE"),
                    XmpProperty("DocumentFileName", "factur-x.xml"),
                    XmpProperty("Version", "1.0"),
                    XmpProperty("ConformanceLevel", level),
                ),
        )

    private fun xmpOf(pdf: ByteArray): String =
        Loader.loadPDF(pdf).use { document ->
            document.documentCatalog.metadata
                .exportXMPMetadata()
                .use { it.readBytes() }
                .decodeToString()
        }

    @Test
    fun declaresTheSchemaNextToTheExistingExtensionSchemas() {
        val xmp = xmpOf(PdfRenderer.convertHtmlToPdf(html = html, xmpSchemas = listOf(facturX("XRECHNUNG"))).bytes)

        assertTrue("<pdfaSchema:namespaceURI>urn:factur-x:pdfa:CrossIndustryDocument:invoice:1p0#</pdfaSchema:namespaceURI>" in xmp)
        assertTrue("<pdfaSchema:prefix>fx</pdfaSchema:prefix>" in xmp)
        assertTrue("<pdfaProperty:name>ConformanceLevel</pdfaProperty:name>" in xmp)
        assertTrue("<pdfaProperty:valueType>Text</pdfaProperty:valueType>" in xmp)
        assertTrue("<pdfaProperty:category>external</pdfaProperty:category>" in xmp)
        assertTrue("<pdfaProperty:description>DocumentType</pdfaProperty:description>" in xmp)
        assertTrue("<fx:DocumentFileName>factur-x.xml</fx:DocumentFileName>" in xmp)
        assertTrue("<fx:ConformanceLevel>XRECHNUNG</fx:ConformanceLevel>" in xmp)
        assertTrue("pdfuaid" in xmp)
        assertEquals(1, Regex("<pdfaExtension:schemas>").findAll(xmp).count())
    }

    @Test
    fun declaresSeveralSchemasInOneDocument() {
        val custom =
            XmpSchema(
                namespace = "https://example.test/ns/",
                prefix = "ex",
                name = "Example",
                properties = listOf(XmpProperty("DocumentId", "42", valueType = "Integer", category = "internal", description = "Internal id")),
            )

        val xmp = xmpOf(PdfRenderer.convertHtmlToPdf(html = html, xmpSchemas = listOf(facturX(), custom)).bytes)

        assertTrue("<fx:DocumentType>INVOICE</fx:DocumentType>" in xmp)
        assertTrue("<ex:DocumentId>42</ex:DocumentId>" in xmp)
        assertTrue("<pdfaProperty:valueType>Integer</pdfaProperty:valueType>" in xmp)
        assertTrue("<pdfaProperty:description>Internal id</pdfaProperty:description>" in xmp)
    }

    @Test
    fun rejectsReservedOrDuplicatePrefixes() {
        assertFailsWith<IllegalArgumentException> {
            PdfRenderer.convertHtmlToPdf(html = html, xmpSchemas = listOf(facturX().copy(prefix = "dc")))
        }
        assertFailsWith<IllegalArgumentException> {
            PdfRenderer.convertHtmlToPdf(html = html, xmpSchemas = listOf(facturX(), facturX()))
        }
    }

    @Test
    fun rejectsNamespacesWithoutATerminator() {
        assertFailsWith<IllegalArgumentException> {
            PdfRenderer.convertHtmlToPdf(html = html, xmpSchemas = listOf(facturX().copy(namespace = "urn:example:invoice")))
        }
    }

    @Test
    fun rejectsUnknownValueTypes() {
        val schema = facturX().copy(properties = listOf(XmpProperty("Version", "1.0", valueType = "Number")))

        assertFailsWith<IllegalArgumentException> {
            PdfRenderer.convertHtmlToPdf(html = html, xmpSchemas = listOf(schema))
        }
    }
}
