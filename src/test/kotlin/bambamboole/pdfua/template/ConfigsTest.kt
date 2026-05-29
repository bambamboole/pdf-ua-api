package bambamboole.pdfua.template

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigsTest {
    private val json = Json

    @Test
    fun alignDeserializesFromLowercaseToken() {
        assertEquals(Align.CENTER, json.decodeFromString(Align.serializer(), "\"center\""))
    }

    @Test
    fun pageFormatExposesDimensions() {
        assertEquals(210.0, PageFormat.A4.widthMm)
        assertEquals(297.0, PageFormat.A4.heightMm)
        assertEquals(297.0, PageFormat.A3.widthMm)
        assertEquals(420.0, PageFormat.A3.heightMm)
    }

    @Test
    fun typographyConfigDefaultsAreAllNull() {
        val cfg = json.decodeFromString(TypographyConfig.serializer(), "{}")
        assertEquals(TypographyConfig(), cfg)
    }
}
