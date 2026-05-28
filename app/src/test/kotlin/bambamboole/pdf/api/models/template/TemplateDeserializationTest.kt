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
        assertEquals(PresetPageSize(PageFormat.A4, Orientation.PORTRAIT), request.template.config.page.size)
        assertEquals("de_DE", request.template.config.page.locale)
        assertIs<TextBlock>(request.template.rows[0].blocks[0])
        assertEquals("Document", request.options.title)
        assertEquals(emptyMap(), request.data)
    }

    @Test
    fun decodesPageNumbersAndMargins() {
        val input = """
            {"template":{"version":1,"config":{"page":{"size":{"format":"Letter"},
            "pageNumbers":{"enabled":true,"position":"right"},
            "margins":{"top":10,"right":10,"bottom":10,"left":10}}},"rows":[]}}
        """.trimIndent()

        val request = json.decodeFromString(RenderRequest.serializer(), input)
        val page = request.template.config.page

        assertEquals(PresetPageSize(PageFormat.LETTER, Orientation.PORTRAIT), page.size)
        assertEquals(true, page.pageNumbers.enabled)
        assertEquals(Align.RIGHT, page.pageNumbers.position)
        assertEquals(10, page.margins.top)
    }

    @Test
    fun decodesPageBackground() {
        val input = """
            {"template":{"version":1,"config":{"page":{
            "background":{"src":"https://cdn.example.com/stationary","type":"pdf"}
            }},"rows":[]}}
        """.trimIndent()

        val request = json.decodeFromString(RenderRequest.serializer(), input)
        val background = request.template.config.page.background

        assertEquals("https://cdn.example.com/stationary", background?.src)
        assertEquals(PageBackgroundType.PDF, background?.type)
    }

    @Test
    fun decodesPageFooter() {
        val input = """
            {"template":{"version":1,"config":{"page":{
            "footer":{"repeat":true,"rows":[{"blocks":[{"type":"text","id":"footerText","text":"Footer"}]}]}
            }},"rows":[]}}
        """.trimIndent()

        val request = json.decodeFromString(RenderRequest.serializer(), input)
        val footer = request.template.config.page.footer

        assertEquals(true, footer.repeat)
        assertEquals(1, footer.rows.size)
        val block = footer.rows.single().blocks.single()
        assertIs<TextBlock>(block)
        assertEquals("footerText", block.id)
    }
}
