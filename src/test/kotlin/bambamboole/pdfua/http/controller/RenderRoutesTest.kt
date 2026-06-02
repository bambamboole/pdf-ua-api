package bambamboole.pdfua.http.controller

import bambamboole.pdfua.module
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.apache.pdfbox.Loader
import org.apache.pdfbox.cos.COSName
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RenderRoutesTest {
    @Test
    fun rendersTemplateToPdf() =
        testApplication {
            application { module() }

            val body =
                """
                {"template":{"version":2,"rows":[
                  {"blocks":[{"type":"text","id":"intro","text":"Hello from a template"}]}
                ]}}
                """.trimIndent()

            val response =
                client.post("/render/template") {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Application.Pdf, response.contentType())
            val pdf = response.readRawBytes()
            assertTrue(
                pdf
                    .take(5)
                    .toByteArray()
                    .decodeToString()
                    .startsWith("%PDF-"),
            )
            assertNotNull(response.headers["X-Document-UUID"])
        }

    @Test
    fun rendersTemplateWithJsonAcceptReturnsValidationAndPdf() =
        testApplication {
            application { module() }

            val body =
                """
                {"template":{"version":2,"rows":[
                  {"blocks":[{"type":"text","id":"intro","text":"Hello from a template"}]}
                ]}}
                """.trimIndent()

            val response =
                client.post("/render/template") {
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Application.Json)
                    setBody(body)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
            val responseBody = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertTrue("validation" in responseBody)
            assertTrue("pdf" in responseBody)

            val pdf = Base64.getDecoder().decode(responseBody.getValue("pdf").jsonPrimitive.content)
            assertTrue(pdf.size > 5 && String(pdf, 0, 5, Charsets.US_ASCII) == "%PDF-")
        }

    @Test
    fun rendersTemplateWithSpacerAndDividerBlocks() =
        testApplication {
            application { module() }

            val body =
                """
                {"template":{"version":2,"rows":[
                  {"blocks":[{"type":"text","text":"Before"}]},
                  {"blocks":[{"type":"spacer","height":"6mm"}]},
                  {"blocks":[{"type":"divider","thickness":"2pt","lineColor":"#111827","style":"dashed"}]},
                  {"blocks":[{"type":"text","text":"After"}]}
                ]}}
                """.trimIndent()

            val response =
                client.post("/render/template") {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Application.Pdf, response.contentType())
            assertTrue(
                response
                    .readRawBytes()
                    .take(5)
                    .toByteArray()
                    .decodeToString()
                    .startsWith("%PDF-"),
            )
        }

    @Test
    fun rendersTemplateWithHeadingAndImageBlocks() =
        testApplication {
            application { module() }

            val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24"><rect width="24" height="24" fill="#2563eb"/></svg>"""
            val imageSrc = "data:image/svg+xml;base64,${Base64.getEncoder().encodeToString(svg.toByteArray())}"
            val body =
                """
                {"template":{"version":2,"rows":[
                  {"blocks":[{"type":"heading","id":"title","text":"Heading block","level":2}]},
                  {"blocks":[{"type":"image","id":"logo","src":"$imageSrc","alt":"Blue square","maxHeight":"24px"}]}
                ]}}
                """.trimIndent()

            val response =
                client.post("/render/template") {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Application.Pdf, response.contentType())
            assertTrue(
                response
                    .readRawBytes()
                    .take(5)
                    .toByteArray()
                    .decodeToString()
                    .startsWith("%PDF-"),
            )
        }

    @Test
    fun rendersTemplateWithTableBlockAndBareArrayData() =
        testApplication {
            application { module() }

            val body =
                """
                {"template":{"version":2,"rows":[
                  {"blocks":[{"type":"table","id":"lineItems",
                    "numberRows":true,
                    "style":"striped",
                    "columns":[
                      {"key":"sku","label":"SKU","width":"20mm"},
                      {"key":"description","label":"Description"},
                      {"key":"total","label":"Total","align":"right"}
                    ]
                  }]}
                ]},
                "data":{"lineItems":[
                  {"sku":"A-100","description":"Accessible PDF setup","total":"100,00 €"},
                  {"sku":"B-200","description":"Structure review","total":"50,00 €"}
                ]}}
                """.trimIndent()

            val response =
                client.post("/render/template") {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Application.Pdf, response.contentType())
            assertTrue(
                response
                    .readRawBytes()
                    .take(5)
                    .toByteArray()
                    .decodeToString()
                    .startsWith("%PDF-"),
            )
        }

    @Test
    fun rendersTemplateWithKeyValueBlock() =
        testApplication {
            application { module() }

            val body =
                """
                {"template":{"version":2,"rows":[
                  {"blocks":[{"type":"key-value","id":"meta",
                    "values":{"invoice":"Original"},
                    "labelWidth":"28mm","fields":[
                      {"key":"invoice","label":"Invoice"},
                      {"key":"customer","label":"Customer"}
                    ]
                  }]}
                ]},
                "data":{"meta":{"invoice":"INV-1","customer":"ACME GmbH"}}}
                """.trimIndent()

            val response =
                client.post("/render/template") {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Application.Pdf, response.contentType())
            assertTrue(
                response
                    .readRawBytes()
                    .take(5)
                    .toByteArray()
                    .decodeToString()
                    .startsWith("%PDF-"),
            )
        }

    @Test
    fun rendersTemplateWithRepeatedFooter() =
        testApplication {
            application { module() }

            val body =
                """
                {"template":{"version":2,
                  "config":{"page":{
                    "footer":{"repeat":true,"rows":[
                      {"blocks":[{"type":"text","id":"footer","text":"Original footer"}]}
                    ]}
                  }},
                  "rows":[
                    {"blocks":[{"type":"text","text":"First page"}]},
                    {"blocks":[{"type":"html","html":"<div style=\"page-break-before: always;\">Second page</div>"}]}
                  ]},
                  "data":{"footer":{"text":"Runtime footer"}}
                }
                """.trimIndent()

            val response =
                client.post("/render/template") {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Application.Pdf, response.contentType())
            assertTrue(
                response
                    .readRawBytes()
                    .take(5)
                    .toByteArray()
                    .decodeToString()
                    .startsWith("%PDF-"),
            )
        }

    @Test
    fun appliesDataOverride() =
        testApplication {
            application { module() }

            val body =
                """
                {"template":{"version":2,"rows":[
                  {"blocks":[{"type":"text","id":"intro","text":"Original"}]}
                ]},
                "data":{"intro":{"text":"Overridden"}}}
                """.trimIndent()

            val response =
                client.post("/render/template") {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.readRawBytes().isNotEmpty())
        }

    @Test
    fun rendersTemplateWithAttachments() =
        testApplication {
            application { module() }

            val attachmentContent = Base64.getEncoder().encodeToString("<invoice/>".toByteArray())
            val body =
                """
                {"template":{"version":2,
                  "attachments":[{
                    "name":"factur-x.xml",
                    "content":"$attachmentContent",
                    "mimeType":"text/xml",
                    "description":"Factur-X XML invoice",
                    "relationship":"Alternative"
                  }],
                  "rows":[{"blocks":[{"type":"text","text":"Invoice"}]}]
                }}
                """.trimIndent()

            val response =
                client.post("/render/template") {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val pdf = response.readRawBytes()
            Loader.loadPDF(pdf).use { document ->
                val embeddedFiles =
                    document.documentCatalog.names
                        ?.embeddedFiles
                        ?.names
                assertNotNull(embeddedFiles)
                assertTrue(embeddedFiles.containsKey("factur-x.xml"))

                val fileSpec = embeddedFiles["factur-x.xml"]!!
                assertEquals("factur-x.xml", fileSpec.file)
                assertEquals("Factur-X XML invoice", fileSpec.fileDescription)
                assertEquals("Alternative", fileSpec.cosObject.getNameAsString(COSName.AF_RELATIONSHIP))
            }
        }

    @Test
    fun rejectsUnsupportedVersion() =
        testApplication {
            application { module() }

            val response =
                client.post("/render/template") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"template":{"version":1,"rows":[]}}""")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun rejectsUnknownBlockType() =
        testApplication {
            application { module() }

            val response =
                client.post("/render/template") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"template":{"version":2,"rows":[{"blocks":[{"type":"nope"}]}]}}""")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
}
