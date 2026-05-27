package bambamboole.pdf.api.models.template

import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BlockDeserializationTest {
    private val json = Json

    @Test
    fun decodesBlocksByTypeDiscriminator() {
        val input = """
            [
              {"type":"text","id":"intro","text":"Hello"},
              {"type":"html","html":"<b>x</b>"},
              {"type":"spacer","config":{"height":12}},
              {"type":"divider","config":{"thickness":2,"lineColor":"#111827","style":"dashed"}},
              {"type":"heading","id":"title","text":"Invoice","config":{"level":1}},
              {"type":"image","id":"logo","src":"logo.png","alt":"Logo","config":{"maxHeight":80}},
              {"type":"key-value","id":"meta","values":{"invoice":"INV-1","empty":null},"config":{"labelWidth":"24mm","fields":[{"key":"invoice","label":"Invoice"},{"key":"empty","label":"Empty"}]}}
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
        val heading = assertIs<HeadingBlock>(blocks[4])
        assertEquals("title", heading.id)
        assertEquals("Invoice", heading.text)
        assertEquals(1, heading.config.level)
        val image = assertIs<ImageBlock>(blocks[5])
        assertEquals("logo.png", image.src)
        assertEquals("Logo", image.alt)
        assertEquals(80, image.config.maxHeight)
        val keyValue = assertIs<KeyValueBlock>(blocks[6])
        assertEquals("meta", keyValue.id)
        assertEquals("INV-1", keyValue.values["invoice"])
        assertEquals(null, keyValue.values["empty"])
        assertEquals("24mm", keyValue.config.labelWidth)
        assertEquals(listOf("invoice", "empty"), keyValue.config.fields.map { it.key })
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
    fun headingRendersEscapedTextAtConfiguredLevel() {
        assertEquals("<h2>Hello</h2>", HeadingBlock(text = "Hello").render())
        assertEquals("<h1>&lt;script&gt;x&lt;/script&gt;</h1>", HeadingBlock(text = "<script>x</script>", config = HeadingConfig(level = 1)).render())
    }

    @Test
    fun imageRendersEscapedImgByDefault() {
        val html = ImageBlock(src = "x\"onerror=\"alert(1)", alt = "<Logo>").render()

        assertEquals("<img src=\"x&quot;onerror=&quot;alert(1)\" alt=\"&lt;Logo&gt;\">", html)
    }

    @Test
    fun imageInlinesAndSanitizesSvgDataUrls() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" onclick="alert(1)">
              <script>alert(1)</script>
              <foreignObject><div>unsafe</div></foreignObject>
              <a href="javascript:alert(1)" xlink:href="javascript:alert(2)">
                <rect width="10" height="10"/>
              </a>
              <rect width="10" height="10"/>
            </svg>
        """.trimIndent()
        val encoded = Base64.getEncoder().encodeToString(svg.toByteArray())
        val src = "data:image/svg+xml;base64,$encoded"

        val output = ImageBlock(src = src, alt = "Logo").render()

        assertTrue(output.startsWith("<svg "))
        assertTrue(output.contains("role=\"img\""))
        assertTrue(output.contains("aria-label=\"Logo\""))
        assertTrue(output.contains("<rect"))
        assertTrue(!output.contains("<script"))
        assertTrue(!output.contains("foreignObject"))
        assertTrue(!output.contains("onclick"))
        assertTrue(!output.contains("javascript:"))
    }

    @Test
    fun imageStripsSvgExternalReferencesAndStyles() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg">
              <style>@import url(https://example.com/x.css); rect { fill: url(https://example.com/p.svg); }</style>
              <defs><path id="shape" d="M0 0h10v10z"/></defs>
              <image href="https://example.com/logo.png" width="10" height="10"/>
              <use href="#shape"/>
              <rect style="fill: url(https://example.com/p.svg)" fill="red" width="10" height="10"/>
            </svg>
        """.trimIndent()
        val encoded = Base64.getEncoder().encodeToString(svg.toByteArray())
        val src = "data:image/svg+xml;base64,$encoded"

        val output = ImageBlock(src = src, alt = "Logo").render()

        assertTrue(output.startsWith("<svg "))
        assertTrue(output.contains("<rect"))
        assertTrue(output.contains("fill=\"red\""))
        assertTrue(!output.contains("<style"))
        assertTrue(!output.contains("<image"))
        assertTrue(!output.contains("<use"))
        assertTrue(!output.contains("style="))
        assertTrue(!output.contains("https://"))
    }

    @Test
    fun imageDoesNotFallbackToImgForRejectedSvgDataUrls() {
        val svg = """
            <!DOCTYPE svg [
              <!ENTITY xxe SYSTEM "file:///etc/passwd">
            ]>
            <svg xmlns="http://www.w3.org/2000/svg"><text>&xxe;</text></svg>
        """.trimIndent()
        val encoded = Base64.getEncoder().encodeToString(svg.toByteArray())
        val src = "data:image/svg+xml;base64,$encoded"

        assertEquals("", ImageBlock(src = src, alt = "Logo").render())
    }

    @Test
    fun spacerAndDividerRenderTheirInnerMarkup() {
        assertEquals("", SpacerBlock().render())
        assertEquals("<hr>", DividerBlock().render())
    }

    @Test
    fun keyValueRendersConfiguredFieldsInOrderAndEscapes() {
        val block = KeyValueBlock(
            values = mapOf("name" to "<ACME>", "invoice" to "INV-1", "ignored" to "x"),
            config = KeyValueConfig(
                fields = listOf(
                    KeyValueField("invoice", "Invoice"),
                    KeyValueField("missing", "Missing"),
                    KeyValueField("name", "Customer <name>"),
                ),
            ),
        )

        assertEquals(
            "<table class=\"key-value\"><tbody>" +
                "<tr><td>Invoice</td><td>INV-1</td></tr>" +
                "<tr><td>Missing</td><td></td></tr>" +
                "<tr><td>Customer &lt;name&gt;</td><td>&lt;ACME&gt;</td></tr>" +
                "</tbody></table>",
            block.render(),
        )
    }

    @Test
    fun keyValueApplyDataReplacesValuesWithFlatStringMap() {
        val block = KeyValueBlock(values = mapOf("invoice" to "Original"))
        val overridden = block.applyData(
            JsonObject(
                mapOf(
                    "invoice" to JsonPrimitive("Runtime"),
                    "empty" to JsonNull,
                ),
            ),
        )

        val keyValue = assertIs<KeyValueBlock>(overridden)
        assertEquals(mapOf("invoice" to "Runtime", "empty" to null), keyValue.values)
    }

    @Test
    fun keyValueRejectsInvalidFieldKeys() {
        assertFailsWith<IllegalArgumentException> {
            KeyValueBlock(config = KeyValueConfig(fields = listOf(KeyValueField("1bad", "Bad")))).render()
        }
    }
}
