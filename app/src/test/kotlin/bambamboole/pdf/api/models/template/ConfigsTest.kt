package bambamboole.pdf.api.models.template

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
    fun pageFormatExposesDimensionsAndCssSize() {
        assertEquals("A4", PageFormat.A4.cssSize)
        assertEquals(210.0, PageFormat.A4.widthMm)
        assertEquals("101.6mm 152.4mm", PageFormat.PARCEL_LABEL_4X6.cssSize)
    }

    @Test
    fun typographyConfigDefaultsAreAllNull() {
        val cfg = json.decodeFromString(TypographyConfig.serializer(), "{}")
        assertEquals(TypographyConfig(), cfg)
    }
}
