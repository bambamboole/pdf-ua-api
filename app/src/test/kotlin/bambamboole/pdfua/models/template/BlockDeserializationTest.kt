package bambamboole.pdfua.models.template

import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
    fun decodesTableBlockWithColumnsAndStyle() {
        val input = """
            {"type":"table","id":"items","config":{
              "numberRows":true,
              "style":"bordered",
              "columns":[
                {"key":"sku","label":"SKU","width":"20mm"},
                {"key":"description","label":"Description","align":"left"}
              ]
            }}
        """.trimIndent()
        val table = assertIs<TableBlock>(json.decodeFromString(Block.serializer(), input))

        assertEquals("items", table.id)
        assertTrue(table.config.numberRows)
        assertEquals(TableStyle.BORDERED, table.config.style)
        assertEquals(listOf("sku", "description"), table.config.columns.map { it.key })
        assertEquals("20mm", table.config.columns[0].width)
        assertEquals(Align.LEFT, table.config.columns[1].align)
        assertTrue(table.rows.isEmpty())
    }

    @Test
    fun unknownTableStyleFails() {
        assertFailsWith<SerializationException> {
            json.decodeFromString(Block.serializer(), """{"type":"table","config":{"style":"fancy"}}""")
        }
    }

    @Test
    fun tableRendersConfiguredColumnsInOrderEscapingCells() {
        val block = TableBlock(
            rows = listOf(
                row("description" to "Service & repair", "quantity" to "1", "total" to "100,00 €"),
            ),
            config = TableConfig(
                columns = listOf(
                    TableColumn(key = "description", label = "Description"),
                    TableColumn(key = "quantity", label = "<Qty>"),
                    TableColumn(key = "total", label = "Total"),
                ),
            ),
        )

        val html = block.render()

        assertTrue(html.startsWith("<table class=\"data-table\">"))
        assertTrue(html.contains("<thead><tr><th>Description</th><th>&lt;Qty&gt;</th><th>Total</th></tr></thead>"))
        assertTrue(html.contains("<td>Service &amp; repair</td><td>1</td><td>100,00 €</td>"))
    }

    @Test
    fun tableRendersMissingColumnValuesAsEmptyCells() {
        val block = TableBlock(
            rows = listOf(row("description" to "Only this")),
            config = TableConfig(
                columns = listOf(
                    TableColumn(key = "description", label = "Description"),
                    TableColumn(key = "missing", label = "Missing"),
                ),
            ),
        )

        assertTrue(block.render().contains("<td>Only this</td><td></td>"))
    }

    @Test
    fun tableEmitsPerColumnAlignmentInline() {
        val block = TableBlock(
            rows = listOf(row("first" to "1", "second" to "2")),
            config = TableConfig(
                columns = listOf(
                    TableColumn(key = "first", label = "A", align = Align.LEFT),
                    TableColumn(key = "second", label = "B", align = Align.RIGHT),
                ),
            ),
        )

        val html = block.render()

        assertTrue(html.contains("<th style=\"text-align: left;\">A</th>"))
        assertTrue(html.contains("<th style=\"text-align: right;\">B</th>"))
        assertTrue(html.contains("<td style=\"text-align: left;\">1</td>"))
        assertTrue(html.contains("<td style=\"text-align: right;\">2</td>"))
    }

    @Test
    fun tableRendersColgroupOnlyWhenAColumnHasWidth() {
        val withWidths = TableBlock(
            rows = listOf(row("sku" to "A-100", "description" to "Setup")),
            config = TableConfig(
                columns = listOf(
                    TableColumn(key = "sku", label = "SKU", width = "7%"),
                    TableColumn(key = "description", label = "Description"),
                ),
            ),
        )
        val widthHtml = withWidths.render()
        assertTrue(widthHtml.contains("<colgroup><col style=\"width: 7%;\"><col></colgroup>"))

        val noWidths = TableBlock(
            rows = listOf(row("sku" to "A-100")),
            config = TableConfig(columns = listOf(TableColumn(key = "sku", label = "SKU"))),
        )
        assertTrue(!noWidths.render().contains("<colgroup"))
    }

    @Test
    fun tableNumberRowsPrependsRightAlignedCounterColumn() {
        val block = TableBlock(
            rows = listOf(
                row("description" to "Accessible PDF setup"),
                row("description" to "Structure review"),
            ),
            config = TableConfig(
                numberRows = true,
                columns = listOf(TableColumn(key = "description", label = "Description")),
            ),
        )

        val html = block.render()

        assertTrue(html.contains("<th style=\"text-align: right;\">#</th><th>Description</th>"))
        assertTrue(html.contains("<td style=\"text-align: right;\">1</td><td>Accessible PDF setup</td>"))
        assertTrue(html.contains("<td style=\"text-align: right;\">2</td><td>Structure review</td>"))
    }

    @Test
    fun tableNumberRowsColgroupPrependsLeadingCol() {
        val block = TableBlock(
            rows = listOf(row("sku" to "A-100")),
            config = TableConfig(
                numberRows = true,
                columns = listOf(TableColumn(key = "sku", label = "SKU", width = "10mm")),
            ),
        )

        assertTrue(block.render().contains("<colgroup><col><col style=\"width: 10mm;\"></colgroup>"))
    }

    @Test
    fun tableStylePresetsEmitScopedCss() {
        assertEquals(
            listOf(".block-1 tbody tr:nth-child(even) { background-color: #f9fafb; }"),
            TableBlock(config = TableConfig(style = TableStyle.STRIPED)).renderCss("block-1"),
        )
        assertEquals(
            listOf(
                ".block-1 { border-collapse: collapse; }",
                ".block-1 th, .block-1 td { border: 1px solid #d1d5db; }",
            ),
            TableBlock(config = TableConfig(style = TableStyle.BORDERED)).renderCss("block-1"),
        )
        assertEquals(
            listOf(
                ".block-1 thead tr { border-bottom: 2px solid #1a1a2e; }",
                ".block-1 tbody tr { border-bottom: 1px solid #e5e7eb; }",
            ),
            TableBlock(config = TableConfig(style = TableStyle.MINIMAL)).renderCss("block-1"),
        )
    }

    @Test
    fun tableApplyDataReplacesRowsFromBareArray() {
        val block = TableBlock(
            id = "items",
            config = TableConfig(columns = listOf(TableColumn(key = "sku", label = "SKU"))),
        )
        val data = buildJsonArray {
            add(buildJsonObject { put("sku", "A-100") })
            add(buildJsonObject { put("sku", "B-200") })
        }

        val updated = assertIs<TableBlock>(block.applyData(data))

        assertEquals(2, updated.rows.size)
        val html = updated.render()
        assertTrue(html.contains("<td>A-100</td>"))
        assertTrue(html.contains("<td>B-200</td>"))
    }

    @Test
    fun tableApplyDataClearsRowsForNonArrayData() {
        val block = TableBlock(
            rows = listOf(row("sku" to "A-100")),
            config = TableConfig(columns = listOf(TableColumn(key = "sku", label = "SKU"))),
        )

        val updated = assertIs<TableBlock>(block.applyData(JsonObject(mapOf("rows" to JsonPrimitive("nope")))))

        assertTrue(updated.rows.isEmpty())
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
        assertFailsWith<IllegalStateException> {
            KeyValueBlock(config = KeyValueConfig(fields = listOf(KeyValueField("1bad", "Bad")))).render()
        }
    }

    private fun row(vararg cells: Pair<String, String>): JsonObject =
        buildJsonObject { cells.forEach { (key, value) -> put(key, value) } }
}
