package bambamboole.pdfua.template

import bambamboole.pdfua.css.CssDeclaration
import bambamboole.pdfua.css.CssRegistry
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
        val input =
            """
            [
              {"type":"text","id":"intro","text":"Hello"},
              {"type":"html","html":"<b>x</b>"},
              {"type":"spacer","height":"12mm"},
              {"type":"divider","thickness":"2pt","lineColor":"#111827","style":"dashed"},
              {"type":"heading","id":"title","text":"Invoice","level":1},
              {"type":"image","id":"logo","src":"logo.png","alt":"Logo","maxHeight":"80px"},
              {"type":"key-value","id":"meta","values":{"invoice":"INV-1","empty":null},"labelWidth":"24mm","fields":[{"key":"invoice","label":"Invoice"},{"key":"empty","label":"Empty"}]}
            ]
            """.trimIndent()
        val blocks = json.decodeFromString(ListSerializer(Block.serializer()), input)

        val text = assertIs<TextBlock>(blocks[0])
        assertEquals("intro", text.id)
        assertEquals("Hello", text.text)
        assertIs<HtmlBlock>(blocks[1])
        val spacer = assertIs<SpacerBlock>(blocks[2])
        assertEquals("12mm", spacer.height)
        val divider = assertIs<DividerBlock>(blocks[3])
        assertEquals("2pt", divider.thickness)
        assertEquals("#111827", divider.lineColor)
        assertEquals(DividerStyle.DASHED, divider.style)
        val heading = assertIs<HeadingBlock>(blocks[4])
        assertEquals("title", heading.id)
        assertEquals("Invoice", heading.text)
        assertEquals(1, heading.level)
        val image = assertIs<ImageBlock>(blocks[5])
        assertEquals("logo.png", image.src)
        assertEquals("Logo", image.alt)
        assertEquals("80px", image.maxHeight)
        val keyValue = assertIs<KeyValueBlock>(blocks[6])
        assertEquals("meta", keyValue.id)
        assertEquals("INV-1", keyValue.values["invoice"])
        assertEquals(null, keyValue.values["empty"])
        assertEquals("24mm", keyValue.labelWidth)
        assertEquals(listOf("invoice", "empty"), keyValue.fields.map { it.key })
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
            json.decodeFromString(Block.serializer(), """{"type":"divider","style":"groove"}""")
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
        assertEquals("<p>a &lt;b&gt;</p><p>c</p>", TextBlock(text = "a <b>\n\nc").render().serialize())
    }

    @Test
    fun htmlRendersRaw() {
        assertEquals("<b>x</b>", HtmlBlock(html = "<b>x</b>").render().serialize())
    }

    @Test
    fun headingRendersEscapedTextAtConfiguredLevel() {
        assertEquals("<h2>Hello</h2>", HeadingBlock(text = "Hello").render().serialize())
        assertEquals(
            "<h1>&lt;script&gt;x&lt;/script&gt;</h1>",
            HeadingBlock(text = "<script>x</script>", level = 1).render().serialize(),
        )
    }

    @Test
    fun imageRendersEscapedImgByDefault() {
        val html = ImageBlock(src = "x\"onerror=\"alert(1)", alt = "<Logo>").render().serialize()

        assertEquals("<img src=\"x&quot;onerror=&quot;alert(1)\" alt=\"&lt;Logo&gt;\">", html)
    }

    @Test
    fun imageInlinesAndSanitizesSvgDataUrls() {
        val svg =
            """
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

        val output = ImageBlock(src = src, alt = "Logo").render().serialize()

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
        val svg =
            """
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

        val output = ImageBlock(src = src, alt = "Logo").render().serialize()

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
        val svg =
            """
            <!DOCTYPE svg [
              <!ENTITY xxe SYSTEM "file:///etc/passwd">
            ]>
            <svg xmlns="http://www.w3.org/2000/svg"><text>&xxe;</text></svg>
            """.trimIndent()
        val encoded = Base64.getEncoder().encodeToString(svg.toByteArray())
        val src = "data:image/svg+xml;base64,$encoded"

        assertEquals("", ImageBlock(src = src, alt = "Logo").render().serialize())
    }

    @Test
    fun spacerAndDividerRenderTheirInnerMarkup() {
        assertEquals("", SpacerBlock().render().serialize())
        assertEquals("<hr>", DividerBlock().render().serialize())
    }

    @Test
    fun decodesTableBlockWithColumnsAndStyle() {
        val input =
            """
            {"type":"table","id":"items",
              "numberRows":true,
              "style":"bordered",
              "columns":[
                {"key":"sku","label":"SKU","width":"20mm"},
                {"key":"description","label":"Description","align":"left"}
              ]
            }
            """.trimIndent()
        val table = assertIs<TableBlock>(json.decodeFromString(Block.serializer(), input))

        assertEquals("items", table.id)
        assertTrue(table.numberRows)
        assertEquals(TableStyle.BORDERED, table.style)
        assertEquals(listOf("sku", "description"), table.columns.map { it.key })
        assertEquals("20mm", table.columns[0].width)
        assertEquals(Align.LEFT, table.columns[1].align)
        assertTrue(table.rows.isEmpty())
    }

    @Test
    fun unknownTableStyleFails() {
        assertFailsWith<SerializationException> {
            json.decodeFromString(Block.serializer(), """{"type":"table","style":"fancy"}""")
        }
    }

    @Test
    fun tableRendersConfiguredColumnsInOrderEscapingCells() {
        val block =
            TableBlock(
                rows =
                    listOf(
                        row("description" to "Service & repair", "quantity" to "1", "total" to "100,00 €"),
                    ),
                columns =
                    listOf(
                        TableColumn(key = "description", label = "Description"),
                        TableColumn(key = "quantity", label = "<Qty>"),
                        TableColumn(key = "total", label = "Total"),
                    ),
            )

        val html = block.render().serialize()

        assertTrue(html.startsWith("<table class=\"data-table\">"))
        assertTrue(html.contains("<thead><tr><th>Description</th><th>&lt;Qty&gt;</th><th>Total</th></tr></thead>"))
        assertTrue(html.contains("<td>Service &amp; repair</td><td>1</td><td>100,00 €</td>"))
    }

    @Test
    fun tableRendersMissingColumnValuesAsEmptyCells() {
        val block =
            TableBlock(
                rows = listOf(row("description" to "Only this")),
                columns =
                    listOf(
                        TableColumn(key = "description", label = "Description"),
                        TableColumn(key = "missing", label = "Missing"),
                    ),
            )

        assertTrue(block.render().serialize().contains("<td>Only this</td><td></td>"))
    }

    @Test
    fun tableEmitsPerColumnAlignmentInline() {
        val block =
            TableBlock(
                rows = listOf(row("first" to "1", "second" to "2")),
                columns =
                    listOf(
                        TableColumn(key = "first", label = "A", align = Align.LEFT),
                        TableColumn(key = "second", label = "B", align = Align.RIGHT),
                    ),
            )

        val html = block.render().serialize()

        assertTrue(html.contains("<th style=\"text-align: left;\">A</th>"))
        assertTrue(html.contains("<th style=\"text-align: right;\">B</th>"))
        assertTrue(html.contains("<td style=\"text-align: left;\">1</td>"))
        assertTrue(html.contains("<td style=\"text-align: right;\">2</td>"))
    }

    @Test
    fun tableRendersColgroupOnlyWhenAColumnHasWidth() {
        val withWidths =
            TableBlock(
                rows = listOf(row("sku" to "A-100", "description" to "Setup")),
                columns =
                    listOf(
                        TableColumn(key = "sku", label = "SKU", width = "7%"),
                        TableColumn(key = "description", label = "Description"),
                    ),
            )
        val widthHtml = withWidths.render().serialize()
        assertTrue(widthHtml.contains("<colgroup><col style=\"width: 7%;\"><col></colgroup>"))

        val noWidths =
            TableBlock(
                rows = listOf(row("sku" to "A-100")),
                columns = listOf(TableColumn(key = "sku", label = "SKU")),
            )
        assertTrue(!noWidths.render().serialize().contains("<colgroup"))
    }

    @Test
    fun tableNumberRowsPrependsRightAlignedCounterColumn() {
        val block =
            TableBlock(
                rows =
                    listOf(
                        row("description" to "Accessible PDF setup"),
                        row("description" to "Structure review"),
                    ),
                numberRows = true,
                columns = listOf(TableColumn(key = "description", label = "Description")),
            )

        val html = block.render().serialize()

        assertTrue(html.contains("<th style=\"text-align: right;\">#</th><th>Description</th>"))
        assertTrue(html.contains("<td style=\"text-align: right;\">1</td><td>Accessible PDF setup</td>"))
        assertTrue(html.contains("<td style=\"text-align: right;\">2</td><td>Structure review</td>"))
    }

    @Test
    fun tableNumberRowsColgroupPrependsLeadingCol() {
        val block =
            TableBlock(
                rows = listOf(row("sku" to "A-100")),
                numberRows = true,
                columns = listOf(TableColumn(key = "sku", label = "SKU", width = "10mm")),
            )

        assertTrue(block.render().serialize().contains("<colgroup><col><col style=\"width: 10mm;\"></colgroup>"))
    }

    @Test
    fun tableStylePresetsEmitScopedCss() {
        assertEquals(
            ".block-1 tbody tr:nth-child(even) { background-color: #f9fafb; }",
            renderCss(TableBlock(style = TableStyle.STRIPED).renderCss("block-1")),
        )
        assertEquals(
            """
            .block-1 { border-collapse: collapse; }
            .block-1 th, .block-1 td { border: 1px solid #d1d5db; }
            """.trimIndent(),
            renderCss(TableBlock(style = TableStyle.BORDERED).renderCss("block-1")),
        )
        assertEquals(
            """
            .block-1 thead tr { border-bottom: 2px solid #1a1a2e; }
            .block-1 tbody tr { border-bottom: 1px solid #e5e7eb; }
            """.trimIndent(),
            renderCss(TableBlock(style = TableStyle.MINIMAL).renderCss("block-1")),
        )
    }

    private fun renderCss(declarations: List<CssDeclaration>): String {
        val registry = CssRegistry()
        declarations.forEach(registry::add)
        return registry.render()
    }

    @Test
    fun tableApplyDataReplacesRowsFromBareArray() {
        val block =
            TableBlock(
                id = "items",
                columns = listOf(TableColumn(key = "sku", label = "SKU")),
            )
        val data =
            buildJsonArray {
                add(buildJsonObject { put("sku", "A-100") })
                add(buildJsonObject { put("sku", "B-200") })
            }

        val updated = assertIs<TableBlock>(block.applyData(data))

        assertEquals(2, updated.rows.size)
        val html = updated.render().serialize()
        assertTrue(html.contains("<td>A-100</td>"))
        assertTrue(html.contains("<td>B-200</td>"))
    }

    @Test
    fun tableApplyDataClearsRowsForNonArrayData() {
        val block =
            TableBlock(
                rows = listOf(row("sku" to "A-100")),
                columns = listOf(TableColumn(key = "sku", label = "SKU")),
            )

        val updated = assertIs<TableBlock>(block.applyData(JsonObject(mapOf("rows" to JsonPrimitive("nope")))))

        assertTrue(updated.rows.isEmpty())
    }

    @Test
    fun keyValueRendersConfiguredFieldsInOrderAndEscapes() {
        val block =
            KeyValueBlock(
                values = mapOf("name" to "<ACME>", "invoice" to "INV-1", "ignored" to "x"),
                fields =
                    listOf(
                        KeyValueField("invoice", "Invoice"),
                        KeyValueField("missing", "Missing"),
                        KeyValueField("name", "Customer <name>"),
                    ),
            )

        assertEquals(
            "<table class=\"key-value\"><tbody>" +
                "<tr><td>Invoice</td><td>INV-1</td></tr>" +
                "<tr><td>Missing</td><td></td></tr>" +
                "<tr><td>Customer &lt;name&gt;</td><td>&lt;ACME&gt;</td></tr>" +
                "</tbody></table>",
            block.render().serialize(),
        )
    }

    @Test
    fun keyValueApplyDataReplacesValuesWithFlatStringMap() {
        val block = KeyValueBlock(values = mapOf("invoice" to "Original"))
        val overridden =
            block.applyData(
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
            KeyValueBlock(fields = listOf(KeyValueField("1bad", "Bad"))).render()
        }
    }

    @Test
    fun decodesSwissQrBarcodeBlock() {
        val input =
            """
            {"type":"barcode","symbology":"swiss-qr","content":{"type":"swiss",
             "creditorIban":"CH4431999123000889012",
             "creditor":{"name":"ACME","postalCode":"2501","town":"Biel","country":"CH"},
             "referenceType":"NON"}}
            """.trimIndent()
        val block = json.decodeFromString(Block.serializer(), input)
        val barcode = assertIs<BarcodeBlock>(block)
        assertEquals(Symbology.SWISS_QR, barcode.symbology)
        val swiss = assertIs<SwissQrContent>(barcode.content)
        assertEquals("CH4431999123000889012", swiss.creditorIban)
    }

    @Test
    fun decodesCodeBlockWithStructuredContent() {
        val input =
            """
            {"type":"barcode","id":"pay","symbology":"qr","height":"24mm",
             "content":{"type":"epc","name":"ACME GmbH","iban":"DE89370400440532013000","amount":"12.50"}}
            """.trimIndent()
        val block = json.decodeFromString(Block.serializer(), input)
        val code = assertIs<BarcodeBlock>(block)
        assertEquals("pay", code.id)
        assertEquals(Symbology.QR, code.symbology)
        val epc = assertIs<EpcContent>(code.content)
        assertEquals("ACME GmbH", epc.name)
    }

    private fun row(vararg cells: Pair<String, String>): JsonObject = buildJsonObject { cells.forEach { (key, value) -> put(key, value) } }
}
