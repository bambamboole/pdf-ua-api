package bambamboole.pdf.api.models.template

import bambamboole.pdf.api.models.RenderRequest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TemplateDeserializationTest {
    private val json = Json

    @Test
    fun decodesFullRenderRequestWithDefaults() {
        val input = """
            {"template":{"version":1,"rows":[{"blocks":[{"type":"text","text":"Hi"}]}]}}
        """.trimIndent()

        val request = json.decodeFromString(RenderRequest.serializer(), input)

        assertEquals(1, request.template.version)
        assertEquals(PageFormat.A4, request.template.config.page.format)
        assertEquals("de_DE", request.template.config.page.locale)
        assertIs<TextBlock>(request.template.rows[0].blocks[0])
        assertEquals("Document", request.options.title)
        assertEquals(emptyMap(), request.data)
    }

    @Test
    fun decodesPageNumbersAndMargins() {
        val input = """
            {"template":{"version":1,"config":{"page":{"format":"Letter",
            "pageNumbers":{"enabled":true,"position":"right"},
            "margins":{"top":10,"right":10,"bottom":10,"left":10}}},"rows":[]}}
        """.trimIndent()

        val request = json.decodeFromString(RenderRequest.serializer(), input)
        val page = request.template.config.page

        assertEquals(PageFormat.LETTER, page.format)
        assertEquals(true, page.pageNumbers.enabled)
        assertEquals(Align.RIGHT, page.pageNumbers.position)
        assertEquals(10, page.margins.top)
    }
}
