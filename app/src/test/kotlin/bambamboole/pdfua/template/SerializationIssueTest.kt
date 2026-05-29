package bambamboole.pdfua.template

import bambamboole.pdfua.http.controller.RenderRequest
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SerializationIssueTest {

    private val json = Json { isLenient = true }

    @Test
    fun missingRequiredFieldMapsToMissingField() {
        val e = assertFailsWith<MissingFieldException> {
            json.decodeFromString(Template.serializer(), "{}")
        }
        val issue = serializationIssue(e)
        assertEquals(ValidationCodes.MISSING_FIELD, issue.code)
        assertEquals("\$", issue.path)
        assertTrue(issue.message.isNotBlank())
    }

    @Test
    fun unknownKeyMapsToUnknownField() {
        val e = assertFailsWith<SerializationException> {
            json.decodeFromString(
                RenderRequest.serializer(),
                """{"template":{"version":1},"weirdRoot":true}""",
            )
        }
        val issue = serializationIssue(e)
        assertEquals(ValidationCodes.UNKNOWN_FIELD, issue.code)
    }

    @Test
    fun malformedJsonMapsToInvalidJson() {
        val e = assertFailsWith<SerializationException> {
            json.decodeFromString(Template.serializer(), "not json {")
        }
        val issue = serializationIssue(e)
        assertEquals(ValidationCodes.INVALID_JSON, issue.code)
    }

    @Test
    fun unknownBlockTypeMapsToInvalidJson() {
        val e = assertFailsWith<SerializationException> {
            json.decodeFromString(
                RenderRequest.serializer(),
                """{"template":{"version":1,"rows":[{"blocks":[{"type":"nope"}]}]}}""",
            )
        }
        val issue = serializationIssue(e)
        assertEquals(ValidationCodes.INVALID_JSON, issue.code)
        // The message should still mention the bad name so a human can diagnose.
        assertTrue(issue.message.contains("nope") || issue.message.lowercase().contains("polymorphic"))
    }
}
