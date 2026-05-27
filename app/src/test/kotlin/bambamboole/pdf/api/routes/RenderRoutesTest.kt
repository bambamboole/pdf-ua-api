package bambamboole.pdf.api.routes

import bambamboole.pdf.api.module
import bambamboole.pdf.api.services.PdfValidationService
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RenderRoutesTest {

    @Test
    fun rendersTemplateToPdf() = testApplication {
        application { module() }

        val body = """
            {"template":{"version":1,"rows":[
              {"blocks":[{"type":"text","id":"intro","text":"Hello from a template"}]}
            ]}}
        """.trimIndent()

        val response = client.post("/render/template") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Pdf, response.contentType())
        val pdf = response.readRawBytes()
        assertTrue(pdf.take(5).toByteArray().decodeToString().startsWith("%PDF-"))
        assertNotNull(response.headers["X-Document-UUID"])
    }

    @Test
    fun appliesDataOverride() = testApplication {
        application { module() }

        val body = """
            {"template":{"version":1,"rows":[
              {"blocks":[{"type":"text","id":"intro","text":"Original"}]}
            ]},
            "data":{"intro":{"text":"Overridden"}}}
        """.trimIndent()

        val response = client.post("/render/template") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.readRawBytes().isNotEmpty())
    }

    @Test
    fun rejectsUnsupportedVersion() = testApplication {
        application { module() }

        val response = client.post("/render/template") {
            contentType(ContentType.Application.Json)
            setBody("""{"template":{"version":2,"rows":[]}}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun rejectsUnknownBlockType() = testApplication {
        application { module() }

        val response = client.post("/render/template") {
            contentType(ContentType.Application.Json)
            setBody("""{"template":{"version":1,"rows":[{"blocks":[{"type":"nope"}]}]}}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun rendersTemplateWithExternalFontEmbedded() = testApplication {
        application { module() }

        val body = """
            {"template":{"version":1,
              "fonts":{"Lobster":{"src":"https://cdn.jsdelivr.net/gh/google/fonts@main/ofl/lobster/Lobster-Regular.ttf"}},
              "rows":[{"blocks":[{"type":"text","id":"t","text":"Special Offer","config":{"typography":{"family":"Lobster","size":36}}}]}]
            }}
        """.trimIndent()

        val response = client.post("/render/template") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val pdf = response.readRawBytes()

        val validation = PdfValidationService.validatePdf(pdf)
        assertTrue(validation.isCompliant, "external-font PDF must be PDF/A-3a compliant")
        val fontNames = validation.documentInfo!!.fonts.map { it.name }
        assertTrue(
            fontNames.any { it.contains("Lobster", ignoreCase = true) },
            "Lobster font must be embedded. Found: $fontNames",
        )
    }
}
