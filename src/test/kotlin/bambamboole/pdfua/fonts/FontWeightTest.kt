package bambamboole.pdfua.fonts

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FontWeightTest {
    private val json = Json

    @Test
    fun decodesEachWeightFromNumericString() {
        assertEquals(FontWeight.LIGHT, json.decodeFromString(FontWeight.serializer(), "\"300\""))
        assertEquals(FontWeight.REGULAR, json.decodeFromString(FontWeight.serializer(), "\"400\""))
        assertEquals(FontWeight.MEDIUM, json.decodeFromString(FontWeight.serializer(), "\"500\""))
        assertEquals(FontWeight.SEMI_BOLD, json.decodeFromString(FontWeight.serializer(), "\"600\""))
        assertEquals(FontWeight.BOLD, json.decodeFromString(FontWeight.serializer(), "\"700\""))
    }

    @Test
    fun encodesEachWeightAsNumericString() {
        assertEquals("\"300\"", json.encodeToString(FontWeight.serializer(), FontWeight.LIGHT))
        assertEquals("\"400\"", json.encodeToString(FontWeight.serializer(), FontWeight.REGULAR))
        assertEquals("\"700\"", json.encodeToString(FontWeight.serializer(), FontWeight.BOLD))
    }

    @Test
    fun rejectsUnknownWeights() {
        assertFailsWith<SerializationException> {
            json.decodeFromString(FontWeight.serializer(), "\"800\"")
        }
        assertFailsWith<SerializationException> {
            json.decodeFromString(FontWeight.serializer(), "\"bold\"")
        }
    }

    @Test
    fun numericValueExposesIntegerWeight() {
        assertEquals(300, FontWeight.LIGHT.numericValue)
        assertEquals(400, FontWeight.REGULAR.numericValue)
        assertEquals(500, FontWeight.MEDIUM.numericValue)
        assertEquals(600, FontWeight.SEMI_BOLD.numericValue)
        assertEquals(700, FontWeight.BOLD.numericValue)
    }
}
