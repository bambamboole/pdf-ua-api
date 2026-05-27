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
        assertEquals(
            listOf("version", "config", "fonts", "attachments", "rows"),
            schema["templateFields"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            listOf("name", "content", "mimeType", "description", "relationship"),
            schema["attachmentFields"]!!.jsonArray.map { it.jsonPrimitive.content },
        )

        val size = schema["page"]!!.jsonObject["size"]!!.jsonObject
        val presets = size["presets"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }
        assertTrue("A4" in presets)
        assertTrue("A3" in presets)
        assertTrue("Letter" in presets)
        assertTrue("ParcelLabel4x6" !in presets)
        assertEquals(listOf("portrait", "landscape"), size["orientations"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("width", "height"), size["customFields"]!!.jsonArray.map { it.jsonPrimitive.content })

        val fonts = schema["fonts"]!!.jsonObject
        val bundledFamilies = fonts["bundledFamilies"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertTrue("Inter" in bundledFamilies)
        assertEquals(listOf("src", "weight", "style"), fonts["externalFontFields"]!!.jsonArray.map { it.jsonPrimitive.content })

        val blockTypes = schema["blocks"]!!.jsonArray.associateBy { it.jsonObject["type"]!!.jsonPrimitive.content }
        assertTrue("text" in blockTypes.keys)
        assertTrue("html" in blockTypes.keys)
        assertTrue("spacer" in blockTypes.keys)
        assertTrue("divider" in blockTypes.keys)
        assertEquals(listOf("id", "text", "config"), blockTypes["text"]!!.jsonObject["fields"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("id", "html", "config"), blockTypes["html"]!!.jsonObject["fields"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("id", "config"), blockTypes["spacer"]!!.jsonObject["fields"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("id", "config"), blockTypes["divider"]!!.jsonObject["fields"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(
            listOf("typography", "spacing", "width", "align", "height"),
            blockTypes["spacer"]!!.jsonObject["configFields"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            listOf("typography", "spacing", "width", "align", "thickness", "lineColor", "style"),
            blockTypes["divider"]!!.jsonObject["configFields"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            listOf("solid", "dashed", "dotted", "double", "none"),
            blockTypes["divider"]!!.jsonObject["configEnums"]!!.jsonObject["style"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
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
