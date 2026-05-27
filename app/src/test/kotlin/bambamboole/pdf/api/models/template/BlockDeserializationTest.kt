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
        val input = """[{"type":"text","id":"intro","text":"Hello"},{"type":"html","html":"<b>x</b>"}]"""
        val blocks = json.decodeFromString(ListSerializer(Block.serializer()), input)

        val text = assertIs<TextBlock>(blocks[0])
        assertEquals("intro", text.id)
        assertEquals("Hello", text.text)
        assertIs<HtmlBlock>(blocks[1])
    }

    @Test
    fun unknownTypeFails() {
        assertFailsWith<SerializationException> {
            json.decodeFromString(Block.serializer(), """{"type":"nope"}""")
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
}
