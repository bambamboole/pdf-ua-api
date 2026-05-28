package bambamboole.pdfua.models.template

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ValidationHelpersTest {

    private val xPath = ValidationPath().child("x")

    @Test
    fun validationPathFormatsLikeJsonPath() {
        assertEquals("$", ValidationPath().toString())
        assertEquals("\$.template", ValidationPath().child("template").toString())
        assertEquals(
            "\$.template.rows[0].blocks[1].config.level",
            ValidationPath()
                .child("template")
                .child("rows").index(0)
                .child("blocks").index(1)
                .child("config")
                .child("level")
                .toString(),
        )
    }

    @Test
    fun requireObjectAcceptsJsonObject() {
        val obj = buildJsonObject { put("k", "v") }
        val (result, errs) = requireObject(obj, xPath)
        assertEquals(obj, result)
        assertTrue(errs.isEmpty())
    }

    @Test
    fun requireObjectRejectsArrayWithInvalidType() {
        val (result, errs) = requireObject(JsonArray(emptyList()), xPath)
        assertNull(result)
        assertEquals(1, errs.size)
        assertEquals(ValidationCodes.INVALID_TYPE, errs[0].code)
        assertEquals("\$.x", errs[0].path)
    }

    @Test
    fun requireArrayAcceptsJsonArray() {
        val arr = JsonArray(listOf(JsonPrimitive(1)))
        val (result, errs) = requireArray(arr, xPath)
        assertEquals(arr, result)
        assertTrue(errs.isEmpty())
    }

    @Test
    fun requireArrayRejectsObjectWithInvalidType() {
        val (result, errs) = requireArray(JsonObject(emptyMap()), xPath)
        assertNull(result)
        assertEquals(ValidationCodes.INVALID_TYPE, errs[0].code)
    }

    @Test
    fun allowedKeysFlagsUnknownKeysAtChildPaths() {
        val obj = buildJsonObject {
            put("a", 1)
            put("extra", 2)
            put("more", 3)
        }

        val errs = allowedKeys(obj, setOf("a"), xPath)

        assertEquals(2, errs.size)
        assertTrue(errs.all { it.code == ValidationCodes.UNKNOWN_FIELD })
        assertEquals(setOf("\$.x.extra", "\$.x.more"), errs.map { it.path }.toSet())
    }

    @Test
    fun optionalStringFieldAcceptsStringOrAbsent() {
        val obj = buildJsonObject { put("a", "hi") }
        assertTrue(optionalStringField(obj, "a", xPath).isEmpty())
        assertTrue(optionalStringField(obj, "missing", xPath).isEmpty())
    }

    @Test
    fun optionalStringFieldRejectsNonStringAndNull() {
        val obj = buildJsonObject {
            put("n", JsonNull)
            put("i", 42)
        }

        val nullErrs = optionalStringField(obj, "n", xPath)
        val intErrs = optionalStringField(obj, "i", xPath)

        assertEquals(1, nullErrs.size)
        assertEquals(ValidationCodes.INVALID_TYPE, nullErrs[0].code)
        assertEquals("\$.x.n", nullErrs[0].path)
        assertEquals(1, intErrs.size)
        assertEquals("\$.x.i", intErrs[0].path)
    }

    @Test
    fun nullableStringFieldAcceptsStringNullOrAbsent() {
        val obj = buildJsonObject {
            put("a", "hi")
            put("n", JsonNull)
        }

        assertTrue(nullableStringField(obj, "a", xPath).isEmpty())
        assertTrue(nullableStringField(obj, "n", xPath).isEmpty())
        assertTrue(nullableStringField(obj, "missing", xPath).isEmpty())
    }

    @Test
    fun nullableStringFieldRejectsNonStringPresent() {
        val obj = buildJsonObject { put("i", 42) }
        val errs = nullableStringField(obj, "i", xPath)

        assertEquals(1, errs.size)
        assertEquals(ValidationCodes.INVALID_TYPE, errs[0].code)
    }
}
