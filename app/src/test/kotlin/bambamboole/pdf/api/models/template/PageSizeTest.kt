package bambamboole.pdf.api.models.template

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PageSizeTest {
    private val json = Json

    @Test
    fun decodesPresetWithDefaults() {
        assertEquals(
            PresetPageSize(PageFormat.A4, Orientation.PORTRAIT),
            json.decodeFromString(PageSize.serializer(), "{}"),
        )
    }

    @Test
    fun decodesPresetWithFormatAndOrientation() {
        val size = json.decodeFromString(PageSize.serializer(), """{"format":"Letter","orientation":"landscape"}""")
        val preset = assertIs<PresetPageSize>(size)
        assertEquals(PageFormat.LETTER, preset.format)
        assertEquals(Orientation.LANDSCAPE, preset.orientation)
    }

    @Test
    fun decodesCustomWhenWidthOrHeightPresent() {
        val size = json.decodeFromString(PageSize.serializer(), """{"width":"210mm","height":"297mm"}""")
        val custom = assertIs<CustomPageSize>(size)
        assertEquals("210mm", custom.width)
        assertEquals("297mm", custom.height)
    }

    @Test
    fun roundTripsPresetWithoutTypeField() {
        val encoded = json.encodeToString(PageSize.serializer(), PresetPageSize(PageFormat.LETTER, Orientation.LANDSCAPE))
        assertEquals(false, encoded.contains("type"), "no discriminator field: $encoded")
        assertEquals(
            PresetPageSize(PageFormat.LETTER, Orientation.LANDSCAPE),
            json.decodeFromString(PageSize.serializer(), encoded),
        )
    }
}
