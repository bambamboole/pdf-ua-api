package bambamboole.pdf.api.routes

import bambamboole.pdf.api.module
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TemplateSchemaRoutesTest {

    @Test
    fun returnsTemplateSchema() = testApplication {
        application { module() }

        val response = client.get("/schema")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())

        val schema = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("template", schema["kind"]?.jsonPrimitive?.content)
        assertEquals("1", schema["templateVersion"]?.jsonPrimitive?.content)

        val page = schema["page"]!!.jsonObject
        val formats = page["formats"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }
        assertTrue("A4" in formats)
        assertTrue("Letter" in formats)

        val fonts = schema["fonts"]!!.jsonObject
        val bundledFamilies = fonts["bundledFamilies"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertTrue("Inter" in bundledFamilies)
        assertEquals(listOf("src", "weight", "style"), fonts["externalFontFields"]!!.jsonArray.map { it.jsonPrimitive.content })

        val blockTypes = schema["blocks"]!!.jsonArray.associateBy { it.jsonObject["type"]!!.jsonPrimitive.content }
        assertTrue("text" in blockTypes.keys)
        assertTrue("html" in blockTypes.keys)
        assertEquals(listOf("id", "text", "config"), blockTypes["text"]!!.jsonObject["fields"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("id", "html", "config"), blockTypes["html"]!!.jsonObject["fields"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun schemaEndpointIsPublicWhenAuthenticationIsEnabled() = testApplication {
        environment {
            config = MapApplicationConfig("api.key" to "test-api-key")
        }
        application { module() }

        val response = client.get("/schema")

        assertEquals(HttpStatusCode.OK, response.status)
    }
}
