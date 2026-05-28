package bambamboole.pdfua.routes

import bambamboole.pdfua.models.template.ValidationCodes
import bambamboole.pdfua.module
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TemplateValidationRoutesTest {

    private val json = Json

    private suspend fun io.ktor.client.statement.HttpResponse.parseIssues(): List<Triple<String, String, String>> {
        val body = json.parseToJsonElement(bodyAsText()).jsonObject
        assertEquals("validation_failed", body["error"]?.jsonPrimitive?.content)
        return body["issues"]!!.jsonArray.map { element ->
            val obj = element.jsonObject
            Triple(
                obj["path"]!!.jsonPrimitive.content,
                obj["code"]!!.jsonPrimitive.content,
                obj["message"]!!.jsonPrimitive.content,
            )
        }
    }

    @Test
    fun unsupportedVersionReturnsStructuredIssue() = testApplication {
        application { module() }

        val response = client.post("/render/template") {
            contentType(ContentType.Application.Json)
            setBody("""{"template":{"version":2,"rows":[]}}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val issues = response.parseIssues()
        assertEquals(1, issues.size)
        assertEquals("\$.template.version", issues[0].first)
        assertEquals(ValidationCodes.UNSUPPORTED_VERSION, issues[0].second)
    }

    @Test
    fun missingRequiredFieldReturnsMissingFieldCode() = testApplication {
        application { module() }

        val response = client.post("/render/template") {
            contentType(ContentType.Application.Json)
            setBody("""{}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val issues = response.parseIssues()
        assertEquals(1, issues.size)
        assertEquals(ValidationCodes.MISSING_FIELD, issues[0].second)
    }

    @Test
    fun malformedJsonReturnsInvalidJsonCode() = testApplication {
        application { module() }

        val response = client.post("/render/template") {
            contentType(ContentType.Application.Json)
            setBody("not json {")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val issues = response.parseIssues()
        assertEquals(1, issues.size)
        assertEquals(ValidationCodes.INVALID_JSON, issues[0].second)
    }

    @Test
    fun unknownRootKeyReturnsUnknownFieldCode() = testApplication {
        application { module() }

        val response = client.post("/render/template") {
            contentType(ContentType.Application.Json)
            setBody("""{"template":{"version":1},"weirdRoot":1}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val issues = response.parseIssues()
        assertEquals(ValidationCodes.UNKNOWN_FIELD, issues[0].second)
    }

    @Test
    fun headingLevelOutOfRangeReturnsStructuredIssue() = testApplication {
        application { module() }

        val response = client.post("/render/template") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"template":{"version":1,"rows":[
                  {"blocks":[{"type":"heading","text":"x","config":{"level":9}}]}
                ]}}""",
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val issues = response.parseIssues()
        assertEquals("\$.template.rows[0].blocks[0].config.level", issues[0].first)
        assertEquals(ValidationCodes.OUT_OF_RANGE, issues[0].second)
    }

    @Test
    fun tableDataAsObjectReturnsInvalidTypeAtDataPath() = testApplication {
        application { module() }

        val response = client.post("/render/template") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"template":{"version":1,"rows":[
                  {"blocks":[{"type":"table","id":"items","config":{"columns":[{"key":"sku","label":"SKU"}]}}]}
                ]},"data":{"items":{"oops":1}}}""",
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val issues = response.parseIssues()
        assertEquals("\$.data.items", issues[0].first)
        assertEquals(ValidationCodes.INVALID_TYPE, issues[0].second)
    }

    @Test
    fun orphanDataIdReturnsStructuredIssue() = testApplication {
        application { module() }

        val response = client.post("/render/template") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"template":{"version":1,"rows":[
                  {"blocks":[{"type":"text","text":"hi"}]}
                ]},"data":{"nope":{"text":"x"}}}""",
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val issues = response.parseIssues()
        assertEquals(1, issues.size)
        assertEquals("\$.data.nope", issues[0].first)
        assertEquals(ValidationCodes.ORPHAN_DATA_ID, issues[0].second)
    }

    @Test
    fun multipleIndependentIssuesAreReturnedTogether() = testApplication {
        application { module() }

        val response = client.post("/render/template") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"template":{"version":1,"rows":[
                  {"blocks":[
                    {"type":"heading","text":"x","config":{"level":0}},
                    {"type":"key-value","id":"meta","config":{"fields":[{"key":"1bad","label":"x"}]}}
                  ]}
                ]},"data":{"orphan":{"text":"x"}}}""",
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val issues = response.parseIssues()
        val codes = issues.map { it.second }.toSet()
        assertTrue(ValidationCodes.OUT_OF_RANGE in codes)
        assertTrue(ValidationCodes.INVALID_KEY in codes)
        assertTrue(ValidationCodes.ORPHAN_DATA_ID in codes)
    }

    @Test
    fun validRequestStillReturns200Pdf() = testApplication {
        application { module() }

        val response = client.post("/render/template") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"template":{"version":1,"rows":[
                  {"blocks":[{"type":"text","id":"intro","text":"Hello"}]}
                ]},"data":{"intro":{"text":"World"}}}""",
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Pdf, response.contentType())
    }
}
