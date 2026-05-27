package bambamboole.pdf.api.models.template

import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class BlockDeserializationTest {
    private val json = Json

    @Test
    fun decodesBlocksByTypeDiscriminator() {
        val input = """
            [
              {"type":"text","id":"intro","text":"Hello"},
              {"type":"html","html":"<b>x</b>"},
              {"type":"spacer","config":{"height":12}},
              {"type":"divider","config":{"thickness":2,"lineColor":"#111827","style":"dashed"}}
            ]
        """.trimIndent()
        val blocks = json.decodeFromString(ListSerializer(Block.serializer()), input)

        val text = assertIs<TextBlock>(blocks[0])
        assertEquals("intro", text.id)
        assertEquals("Hello", text.text)
        assertIs<HtmlBlock>(blocks[1])
        val spacer = assertIs<SpacerBlock>(blocks[2])
        assertEquals(12, spacer.config.height)
        val divider = assertIs<DividerBlock>(blocks[3])
        assertEquals(2, divider.config.thickness)
        assertEquals("#111827", divider.config.lineColor)
        assertEquals(DividerStyle.DASHED, divider.config.style)
    }

    @Test
    fun unknownTypeFails() {
        assertFailsWith<SerializationException> {
            json.decodeFromString(Block.serializer(), """{"type":"nope"}""")
        }
    }

    @Test
    fun unknownDividerStyleFails() {
        assertFailsWith<SerializationException> {
            json.decodeFromString(Block.serializer(), """{"type":"divider","config":{"style":"groove"}}""")
        }
    }

    @Test
    fun applyDataOverridesTextContent() {
        val block = TextBlock(id = "intro", text = "Hello")
        val overridden = block.applyData(JsonObject(mapOf("text" to JsonPrimitive("World"))))
        assertEquals("World", assertIs<TextBlock>(overridden).text)
    }

    @Test
    fun textRendersParagraphsAndEscapes() {
        assertEquals("<p>a &lt;b&gt;</p><p>c</p>", TextBlock(text = "a <b>\n\nc").render())
    }

    @Test
    fun htmlRendersRaw() {
        assertEquals("<b>x</b>", HtmlBlock(html = "<b>x</b>").render())
    }

    @Test
    fun spacerAndDividerRenderTheirInnerMarkup() {
        assertEquals("", SpacerBlock().render())
        assertEquals("<hr>", DividerBlock().render())
    }
}
