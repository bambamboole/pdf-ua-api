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
        assertEquals("https://json-schema.org/draft/2020-12/schema", schema["\$schema"]?.jsonPrimitive?.content)
        assertEquals("https://pdf-ua-api.com/schemas/template-v1.json", schema["\$id"]?.jsonPrimitive?.content)
        assertEquals("Template", schema["title"]?.jsonPrimitive?.content)
        assertEquals("object", schema["type"]?.jsonPrimitive?.content)

        val metadata = schema["x-pdfUa"]!!.jsonObject
        assertEquals("template", metadata["kind"]?.jsonPrimitive?.content)
        assertEquals("1", metadata["templateVersion"]?.jsonPrimitive?.content)
        assertEquals("/render/template", metadata["renderEndpoint"]?.jsonPrimitive?.content)
        assertEquals(
            listOf("version", "config", "fonts", "attachments", "rows"),
            metadata["templateFields"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            listOf("name", "content", "mimeType", "description", "relationship"),
            metadata["attachmentFields"]!!.jsonArray.map { it.jsonPrimitive.content },
        )

        val presets = metadata["pageFormats"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }
        assertTrue("A4" in presets)
        assertTrue("A3" in presets)
        assertTrue("Letter" in presets)
        assertTrue("ParcelLabel4x6" !in presets)

        val bundledFamilies = metadata["bundledFonts"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertTrue("Inter" in bundledFamilies)
        assertEquals(listOf("src", "weight", "style"), metadata["externalFontFields"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(
            listOf("text", "html", "heading", "image", "key-value", "spacer", "divider"),
            metadata["blockOrder"]!!.jsonArray.map { it.jsonPrimitive.content },
        )

        val definitions = schema["\$defs"]!!.jsonObject
        assertEquals(
            listOf(
                "#/\$defs/textBlock",
                "#/\$defs/htmlBlock",
                "#/\$defs/headingBlock",
                "#/\$defs/imageBlock",
                "#/\$defs/keyValueBlock",
                "#/\$defs/spacerBlock",
                "#/\$defs/dividerBlock",
            ),
            definitions["block"]!!.jsonObject["oneOf"]!!.jsonArray.map { it.jsonObject["\$ref"]!!.jsonPrimitive.content },
        )
        assertEquals(
            listOf("A3", "A4", "A5", "A6", "Letter", "Legal", "Tabloid"),
            definitions["pageFormat"]!!.jsonObject["enum"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            listOf("portrait", "landscape"),
            definitions["orientation"]!!.jsonObject["enum"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            listOf("solid", "dashed", "dotted", "double", "none"),
            definitions["dividerStyle"]!!.jsonObject["enum"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            listOf("auto", "image", "pdf"),
            definitions["pageBackgroundType"]!!.jsonObject["enum"]!!.jsonArray.map { it.jsonPrimitive.content },
        )

        val pageConfig = definitions["pageConfig"]!!.jsonObject
        assertEquals(
            "{ size?: PageSize; locale?: string; margins?: SpacingConfig; pageNumbers?: PageNumbersConfig; " +
                "background?: PageBackgroundConfig | null }",
            pageConfig["tsType"]!!.jsonPrimitive.content,
        )
        assertEquals(
            listOf("#/\$defs/pageBackgroundConfig", "null"),
            pageConfig["properties"]!!.jsonObject["background"]!!.jsonObject["oneOf"]!!.jsonArray.map { option ->
                option.jsonObject["\$ref"]?.jsonPrimitive?.content ?: option.jsonObject["type"]!!.jsonPrimitive.content
            },
        )

        val pageBackgroundConfig = definitions["pageBackgroundConfig"]!!.jsonObject
        assertEquals(listOf("src"), pageBackgroundConfig["required"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("string", pageBackgroundConfig["properties"]!!.jsonObject["src"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("1", pageBackgroundConfig["properties"]!!.jsonObject["src"]!!.jsonObject["minLength"]!!.jsonPrimitive.content)
        assertEquals(
            "#/\$defs/pageBackgroundType",
            pageBackgroundConfig["properties"]!!.jsonObject["type"]!!.jsonObject["\$ref"]!!.jsonPrimitive.content,
        )

        val blockConfig = definitions["blockConfig"]!!.jsonObject
        assertEquals(
            listOf("#/\$defs/typographyConfig", "null"),
            blockConfig["properties"]!!.jsonObject["typography"]!!.jsonObject["oneOf"]!!.jsonArray.map { option ->
                option.jsonObject["\$ref"]?.jsonPrimitive?.content ?: option.jsonObject["type"]!!.jsonPrimitive.content
            },
        )

        val textBlock = definitions["textBlock"]!!.jsonObject
        assertEquals(listOf("type", "text"), textBlock["required"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("text", textBlock["properties"]!!.jsonObject["type"]!!.jsonObject["const"]!!.jsonPrimitive.content)

        val keyValueField = definitions["keyValueField"]!!.jsonObject
        assertEquals(listOf("key", "label"), keyValueField["required"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(
            "^[A-Za-z][A-Za-z0-9_]*$",
            keyValueField["properties"]!!.jsonObject["key"]!!.jsonObject["pattern"]!!.jsonPrimitive.content,
        )

        val keyValueConfig = definitions["keyValueConfig"]!!.jsonObject
        assertEquals("30mm", keyValueConfig["properties"]!!.jsonObject["labelWidth"]!!.jsonObject["default"]!!.jsonPrimitive.content)
        assertEquals(
            "#/\$defs/keyValueField",
            keyValueConfig["properties"]!!.jsonObject["fields"]!!.jsonObject["items"]!!.jsonObject["\$ref"]!!.jsonPrimitive.content,
        )

        val keyValueBlock = definitions["keyValueBlock"]!!.jsonObject
        assertEquals(listOf("type"), keyValueBlock["required"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("key-value", keyValueBlock["properties"]!!.jsonObject["type"]!!.jsonObject["const"]!!.jsonPrimitive.content)
        val values = keyValueBlock["properties"]!!.jsonObject["values"]!!.jsonObject
        assertEquals("^[A-Za-z][A-Za-z0-9_]*$", values["propertyNames"]!!.jsonObject["pattern"]!!.jsonPrimitive.content)
        assertEquals(
            listOf("string", "null"),
            values["additionalProperties"]!!.jsonObject["type"]!!.jsonArray.map { it.jsonPrimitive.content },
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
