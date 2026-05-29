package bambamboole.pdfua.html

import bambamboole.pdfua.services.RenderOptions
import bambamboole.pdfua.template.Align
import bambamboole.pdfua.template.BaseBlockConfig
import bambamboole.pdfua.template.DividerBlock
import bambamboole.pdfua.template.DividerConfig
import bambamboole.pdfua.template.DividerStyle
import bambamboole.pdfua.fonts.FontFace
import bambamboole.pdfua.template.HeadingBlock
import bambamboole.pdfua.template.HeadingConfig
import bambamboole.pdfua.template.PageBackgroundConfig
import bambamboole.pdfua.template.PageBackgroundType
import bambamboole.pdfua.template.HtmlBlock
import bambamboole.pdfua.template.ImageBlock
import bambamboole.pdfua.template.ImageConfig
import bambamboole.pdfua.template.KeyValueBlock
import bambamboole.pdfua.template.KeyValueConfig
import bambamboole.pdfua.template.KeyValueField
import bambamboole.pdfua.template.PageConfig
import bambamboole.pdfua.template.CustomPageSize
import bambamboole.pdfua.template.Orientation
import bambamboole.pdfua.template.PageFooterConfig
import bambamboole.pdfua.template.PageFormat
import bambamboole.pdfua.template.PageNumbersConfig
import bambamboole.pdfua.template.PresetPageSize
import bambamboole.pdfua.template.Row
import bambamboole.pdfua.template.SpacerBlock
import bambamboole.pdfua.template.SpacerConfig
import bambamboole.pdfua.template.Template
import bambamboole.pdfua.template.TemplateConfig
import bambamboole.pdfua.template.TableBlock
import bambamboole.pdfua.template.TableColumn
import bambamboole.pdfua.template.TableConfig
import bambamboole.pdfua.template.TableStyle
import bambamboole.pdfua.template.TextBlock
import bambamboole.pdfua.template.TypographyConfig
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TemplateRendererTest {

    private fun template(vararg blocks: bambamboole.pdfua.template.Block, config: TemplateConfig = TemplateConfig()) =
        Template(version = 1, config = config, rows = listOf(Row(blocks.toList())))

    @Test
    fun rendersTextBlockInsideRowAndDocument() {
        val html = TemplateRenderer.render(template(TextBlock(text = "Hello")))
        assertTrue(html.startsWith("<!DOCTYPE html>"), "should be a full document")
        assertTrue(html.contains("<table class=\"row\" role=\"presentation\">"))
        assertTrue(html.contains("<div class=\"block-1\"><p>Hello</p></div>"))
    }

    @Test
    fun rendersHtmlLangFromLocale() {
        val cfg = TemplateConfig(page = PageConfig(locale = "en_US"))
        val html = TemplateRenderer.render(template(HtmlBlock(html = "<b>x</b>"), config = cfg))
        assertTrue(html.contains("<html lang=\"en\">"))
    }

    @Test
    fun rendersSpacerWithScopedHeightCss() {
        val html = TemplateRenderer.render(template(SpacerBlock(config = SpacerConfig(height = 12))))

        assertTrue(html.contains(".block-1 { height: 12mm; }"))
        assertTrue(html.contains("<div class=\"block-1\"></div>"))
    }

    @Test
    fun rendersDividerWithScopedLineCss() {
        val block = DividerBlock(config = DividerConfig(thickness = 2, lineColor = "#111827", style = DividerStyle.DASHED))
        val html = TemplateRenderer.render(template(block))

        assertTrue(html.contains("<div class=\"block-1\"><hr></div>"))
        assertTrue(html.contains("border: none"))
        assertTrue(html.contains("margin: 2.5mm 0"))
        assertTrue(html.contains("border-top-width: 2pt"))
        assertTrue(html.contains("border-top-color: #111827"))
        assertTrue(html.contains("border-top-style: dashed"))
    }

    @Test
    fun rendersHeadingWithRuntimeDataOverride() {
        val data = mapOf("title" to JsonObject(mapOf("text" to JsonPrimitive("Runtime title"))))
        val html = TemplateRenderer.render(template(HeadingBlock(id = "title", text = "Original", config = HeadingConfig(level = 1))), data)

        assertTrue(html.contains("<div class=\"block-1\"><h1>Runtime title</h1></div>"))
        assertTrue(!html.contains("Original"))
    }

    @Test
    fun rejectsInvalidHeadingLevel() {
        assertFailsWith<IllegalStateException> {
            TemplateRenderer.render(template(HeadingBlock(text = "Bad", config = HeadingConfig(level = 7))))
        }
    }

    @Test
    fun rendersImageWithRuntimeDataAndScopedMaxHeight() {
        val data = mapOf(
            "logo" to JsonObject(
                mapOf(
                    "src" to JsonPrimitive("runtime.png"),
                    "alt" to JsonPrimitive("Runtime logo"),
                ),
            ),
        )
        val html = TemplateRenderer.render(template(ImageBlock(id = "logo", src = "logo.png", alt = "Logo", config = ImageConfig(maxHeight = 72))), data)

        assertTrue(html.contains("<div class=\"block-1\"><img src=\"runtime.png\" alt=\"Runtime logo\"></div>"))
        assertTrue(html.contains(".block-1 img, .block-1 svg { max-height: 72px; }"))
        assertTrue(html.contains("img, svg { max-width: 100%; height: auto; }"))
    }

    @Test
    fun rendersKeyValueWithRuntimeDataAndScopedLabelWidth() {
        val block = KeyValueBlock(
            id = "meta",
            values = mapOf("invoice" to "Original", "customer" to "Original customer"),
            config = KeyValueConfig(
                labelWidth = "28mm",
                fields = listOf(
                    KeyValueField("customer", "Customer"),
                    KeyValueField("invoice", "Invoice <no>"),
                    KeyValueField("missing", "Missing"),
                    KeyValueField("empty", "Empty"),
                ),
            ),
        )
        val data = mapOf(
            "meta" to JsonObject(
                mapOf(
                    "invoice" to JsonPrimitive("INV-1"),
                    "customer" to JsonPrimitive("<ACME>"),
                    "empty" to JsonNull,
                ),
            ),
        )

        val html = TemplateRenderer.render(template(block), data)

        assertTrue(
            html.contains(
                "<table class=\"key-value\"><tbody>" +
                    "<tr><td>Customer</td><td>&lt;ACME&gt;</td></tr>" +
                    "<tr><td>Invoice &lt;no&gt;</td><td>INV-1</td></tr>" +
                    "<tr><td>Missing</td><td></td></tr>" +
                    "<tr><td>Empty</td><td></td></tr>" +
                    "</tbody></table>",
            ),
        )
        assertTrue(!html.contains("Original"), "runtime values should replace template values")
        assertTrue(html.contains(".block-1 .key-value td:first-child { width: 28mm; }"))
        assertTrue(html.contains(".key-value { width: 100%; border-collapse: collapse; margin: 0 0 2mm; }"))
    }

    @Test
    fun keyValueDropsUnsafeLabelWidth() {
        val block = KeyValueBlock(
            config = KeyValueConfig(
                labelWidth = "1mm} body{display:none",
                fields = listOf(KeyValueField("invoice", "Invoice")),
            ),
        )

        val html = TemplateRenderer.render(template(block))

        assertTrue(!html.contains("display:none"), "unsafe label width must not be emitted")
        assertTrue(!html.contains(".block-1 .key-value td:first-child { width:"))
    }

    @Test
    fun dropsInvalidImageMaxHeightCss() {
        val html = TemplateRenderer.render(template(ImageBlock(src = "logo.png", config = ImageConfig(maxHeight = -1))))

        assertTrue(!html.contains("max-height: -1px"))
    }

    @Test
    fun dividerDropsUnsafeLineCssValuesButKeepsEnumStyle() {
        val block = DividerBlock(config = DividerConfig(thickness = -1, lineColor = "red; } body{display:none", style = DividerStyle.DOTTED))
        val html = TemplateRenderer.render(template(block))

        assertTrue(!html.contains("display:none"), "unsafe color must not be emitted")
        assertTrue(!html.contains("border-top-width: -1pt"), "negative thickness must not be emitted")
        assertTrue(html.contains("border-top-style: dotted"), "enum style should still be emitted")
    }

    @Test
    fun emitsPresetPortraitSizeAndMargins() {
        val cfg = TemplateConfig(page = PageConfig(size = PresetPageSize(PageFormat.A4)))
        val html = TemplateRenderer.render(template(TextBlock(text = "x"), config = cfg))
        assertTrue(html.contains("@page { size: 210mm 297mm; margin: 20mm 20mm 20mm 25mm; }"))
    }

    @Test
    fun emitsSwappedSizeForLandscape() {
        val cfg = TemplateConfig(page = PageConfig(size = PresetPageSize(PageFormat.A4, Orientation.LANDSCAPE)))
        val html = TemplateRenderer.render(template(TextBlock(text = "x"), config = cfg))
        assertTrue(html.contains("@page { size: 297mm 210mm;"))
    }

    @Test
    fun emitsCustomSize() {
        val cfg = TemplateConfig(page = PageConfig(size = CustomPageSize(102, 152)))
        val html = TemplateRenderer.render(template(TextBlock(text = "x"), config = cfg))
        assertTrue(html.contains("@page { size: 102mm 152mm;"))
    }

    @Test
    fun rejectsNonPositiveCustomDimension() {
        val cfg = TemplateConfig(page = PageConfig(size = CustomPageSize(0, 297)))
        assertFailsWith<IllegalStateException> {
            TemplateRenderer.render(template(TextBlock(text = "x"), config = cfg))
        }
    }

    @Test
    fun emitsPageNumbersWhenEnabled() {
        val cfg = TemplateConfig(page = PageConfig(pageNumbers = PageNumbersConfig(enabled = true, position = Align.RIGHT)))
        val html = TemplateRenderer.render(template(TextBlock(text = "x"), config = cfg))
        assertTrue(html.contains("@bottom-right"))
        assertTrue(html.contains("counter(page)"))
    }

    @Test
    fun rendersRepeatedFooterBeforeBodyAndReservesBottomMargin() {
        val cfg = TemplateConfig(
            page = PageConfig(
                footer = PageFooterConfig(
                    rows = listOf(Row(listOf(TextBlock(text = "Repeated footer")))),
                ),
            ),
        )

        val html = TemplateRenderer.render(template(TextBlock(text = "Body"), config = cfg))

        assertTrue(html.contains("@page { size: 210mm 297mm; margin: 20mm 20mm 28mm 25mm; }"))
        assertTrue(html.contains(".page-footer-repeated { position: running(pageFooter); width: 100%; }"))
        assertTrue(html.contains("@bottom-center { content: element(pageFooter); }"))
        assertTrue(
            html.indexOf("""<footer class="page-footer page-footer-repeated" role="contentinfo">""") <
                html.indexOf("<p>Body</p>"),
            "repeated footer must be a first body child so OpenHTMLToPDF can apply it to all pages",
        )
        assertTrue(html.contains("""<footer class="page-footer page-footer-repeated" role="contentinfo"><table class="row" role="presentation"><tr><td><div class="block-1"><p>Repeated footer</p></div></td></tr></table></footer>"""))
        assertTrue(html.contains("""<div class="block-2"><p>Body</p></div>"""))
    }

    @Test
    fun appliesRuntimeDataInsideRepeatedFooter() {
        val cfg = TemplateConfig(
            page = PageConfig(
                footer = PageFooterConfig(
                    rows = listOf(Row(listOf(TextBlock(id = "footer", text = "Original footer")))),
                ),
            ),
        )
        val data = mapOf("footer" to JsonObject(mapOf("text" to JsonPrimitive("Runtime footer"))))

        val html = TemplateRenderer.render(template(TextBlock(text = "Body"), config = cfg), data)

        assertTrue(html.contains("<p>Runtime footer</p>"))
        assertTrue(!html.contains("Original footer"))
    }

    @Test
    fun rendersCenteredPageNumbersInsideRepeatedFooter() {
        val cfg = TemplateConfig(
            page = PageConfig(
                pageNumbers = PageNumbersConfig(enabled = true, position = Align.CENTER),
                footer = PageFooterConfig(
                    rows = listOf(Row(listOf(TextBlock(text = "Footer")))),
                ),
            ),
        )

        val html = TemplateRenderer.render(template(TextBlock(text = "Body"), config = cfg))

        assertTrue(html.contains("""<div class="page-footer-page-numbers" aria-hidden="true"></div>"""))
        assertTrue(html.contains(".page-footer-page-numbers::after { content: counter(page) \" / \" counter(pages); }"))
        assertTrue(!html.contains("@bottom-center { content: counter(page)"), "centered page numbers must move into repeated footer")
    }

    @Test
    fun keepsNonCenteredPageNumbersOutsideRepeatedFooter() {
        val cfg = TemplateConfig(
            page = PageConfig(
                pageNumbers = PageNumbersConfig(enabled = true, position = Align.RIGHT),
                footer = PageFooterConfig(
                    rows = listOf(Row(listOf(TextBlock(text = "Footer")))),
                ),
            ),
        )

        val html = TemplateRenderer.render(template(TextBlock(text = "Body"), config = cfg))

        assertTrue(!html.contains("page-footer-page-numbers"))
        assertTrue(html.contains("@bottom-right { content: counter(page) \" / \" counter(pages);"))
    }

    @Test
    fun emitsBackgroundObjectAndRunningPlacementForImage() {
        val cfg = TemplateConfig(page = PageConfig(background = PageBackgroundConfig(src = "https://cdn.example.com/bg.png", type = PageBackgroundType.IMAGE)))
        val html = TemplateRenderer.render(template(TextBlock(text = "x"), config = cfg))

        assertTrue(html.contains("""<object type="x-page-background" data-src="https://cdn.example.com/bg.png" data-kind="image" style="width:1px;height:1px">"""))
        assertTrue(html.contains(".pagebg { position: running(pagebg); }"))
        assertTrue(html.contains("@top-left { content: element(pagebg); }"))
        assertTrue(!html.contains("background-image"), "the broken @page background-image path must be gone")
    }

    @Test
    fun emitsBackgroundKindForPdfAndAuto() {
        val pdf = TemplateRenderer.render(template(TextBlock(text = "x"), config = TemplateConfig(page = PageConfig(background = PageBackgroundConfig(src = "https://cdn.example.com/s.pdf", type = PageBackgroundType.PDF)))))
        assertTrue(pdf.contains("""data-src="https://cdn.example.com/s.pdf" data-kind="pdf""""))

        val auto = TemplateRenderer.render(template(TextBlock(text = "x"), config = TemplateConfig(page = PageConfig(background = PageBackgroundConfig(src = "https://cdn.example.com/s.pdf")))))
        assertTrue(auto.contains("""data-kind="auto""""))
    }

    @Test
    fun noBackgroundObjectWhenUnset() {
        val html = TemplateRenderer.render(template(TextBlock(text = "x")))
        assertTrue(!html.contains("x-page-background"))
        assertTrue(!html.contains("running(pagebg)"))
    }

    @Test
    fun appliesDataOverrideByBlockId() {
        val data = mapOf("intro" to JsonObject(mapOf("text" to JsonPrimitive("Overridden"))))
        val html = TemplateRenderer.render(template(TextBlock(id = "intro", text = "Original")), data)
        assertTrue(html.contains("<p>Overridden</p>"))
        assertTrue(!html.contains("Original"))
    }

    @Test
    fun emitsBlockAlignmentCss() {
        val block = TextBlock(text = "x", config = BaseBlockConfig(align = Align.CENTER))
        val html = TemplateRenderer.render(template(block))
        assertTrue(html.contains(".block-1 { margin-left: auto; margin-right: auto; }"))
    }

    @Test
    fun rejectsUnsupportedVersion() {
        assertFailsWith<IllegalStateException> {
            TemplateRenderer.render(Template(version = 2))
        }
    }

    @Test
    fun multiBlockRowPutsSafeWidthOnCellsWithScopedIds() {
        val left = TextBlock(text = "L", config = BaseBlockConfig(width = "60%"))
        val right = TextBlock(text = "R", config = BaseBlockConfig(width = "40%"))
        val tpl = Template(version = 1, rows = listOf(Row(listOf(left, right))))
        val html = TemplateRenderer.render(tpl)
        assertTrue(html.contains("<td style=\"width: 60%;\"><div class=\"block-1\"><p>L</p></div></td>"))
        assertTrue(html.contains("<td style=\"width: 40%;\"><div class=\"block-2\"><p>R</p></div></td>"))
    }

    @Test
    fun singleBlockSafeWidthEmittedAsScopedCss() {
        val block = TextBlock(text = "x", config = BaseBlockConfig(width = "80mm"))
        val html = TemplateRenderer.render(template(block))
        assertTrue(html.contains(".block-1 { width: 80mm; }"))
    }

    @Test
    fun unsafeWidthIsDroppedNotInjected() {
        val malicious = "1mm} body{display:none"
        val block = TextBlock(text = "x", config = BaseBlockConfig(width = malicious))
        val html = TemplateRenderer.render(template(block))
        assertTrue(!html.contains("display:none"), "unsafe width must not be emitted")
        assertTrue(!html.contains("width: 1mm}"), "unsafe width must be dropped entirely")
    }

    @Test
    fun rejectsUnsafePageBackgroundSrc() {
        val cfg = TemplateConfig(page = PageConfig(background = PageBackgroundConfig(src = "file:///etc/passwd")))

        assertFailsWith<IllegalStateException> {
            TemplateRenderer.render(template(TextBlock(text = "x"), config = cfg))
        }
    }

    @Test
    fun externalFontEmitsFontFaceAndUsesFamily() {
        val tpl = Template(
            version = 1,
            fonts = mapOf("Lobster" to FontFace(src = "https://cdn.example.com/lobster.ttf", weight = "400")),
            rows = listOf(Row(listOf(TextBlock(text = "x", config = BaseBlockConfig(typography = TypographyConfig(family = "Lobster")))))),
        )
        val html = TemplateRenderer.render(tpl)
        assertTrue(
            html.contains("@font-face { font-family: 'Lobster'; src: url(\"https://cdn.example.com/lobster.ttf\") format(\"truetype\"); font-weight: 400; font-style: normal; }"),
        )
        assertTrue(html.contains(".block-1 { font-family: 'Lobster'; }"))
    }

    @Test
    fun externalFontMultiWeightEmitsOneFontFaceRulePerWeight() {
        val tpl = Template(
            version = 1,
            fonts = mapOf("Lobster" to FontFace(src = "https://cdn.example.com/lobster.ttf", weight = "400 700")),
            rows = listOf(Row(listOf(TextBlock(text = "x")))),
        )

        val html = TemplateRenderer.render(tpl)

        assertTrue(
            html.contains("@font-face { font-family: 'Lobster'; src: url(\"https://cdn.example.com/lobster.ttf\") format(\"truetype\"); font-weight: 400; font-style: normal; }"),
            "should emit a regular @font-face rule",
        )
        assertTrue(
            html.contains("@font-face { font-family: 'Lobster'; src: url(\"https://cdn.example.com/lobster.ttf\") format(\"truetype\"); font-weight: 700; font-style: normal; }"),
            "should emit a bold @font-face rule from the same src",
        )
    }

    @Test
    fun bundledFamilyIsUsedAsIsWithoutFontFace() {
        val cfg = TemplateConfig(typography = TypographyConfig(family = "Inter"))
        val html = TemplateRenderer.render(template(TextBlock(text = "x"), config = cfg))
        assertTrue(html.contains("body { font-family: 'Inter'; color: #111827; line-height: 1.35; }"))
        assertTrue(!html.contains("@font-face"), "bundled family must not emit @font-face")
    }

    @Test
    fun rendersTableWithRuntimeRowDataAsBareArray() {
        val table = TableBlock(
            id = "items",
            config = TableConfig(
                style = TableStyle.STRIPED,
                columns = listOf(
                    TableColumn(key = "sku", label = "SKU"),
                    TableColumn(key = "description", label = "Description"),
                ),
            ),
        )
        val data = mapOf(
            "items" to buildJsonArray {
                add(buildJsonObject { put("sku", "A-100"); put("description", "Accessible PDF setup") })
                add(buildJsonObject { put("sku", "B-200"); put("description", "Structure review") })
            },
        )

        val html = TemplateRenderer.render(template(table), data)

        assertTrue(html.contains("<th>SKU</th><th>Description</th>"))
        assertTrue(html.contains("<td>A-100</td><td>Accessible PDF setup</td>"))
        assertTrue(html.contains("<td>B-200</td><td>Structure review</td>"))
        assertTrue(html.contains(".block-1 tbody tr:nth-child(even) { background-color: #f9fafb; }"))
    }

    @Test
    fun emitsDataTableBaseCssOnce() {
        val html = TemplateRenderer.render(template(TableBlock(config = TableConfig(columns = listOf(TableColumn("a", "A"))))))

        assertTrue(html.contains(".data-table { width: 100%; border-collapse: collapse; text-align: left; }"))
        assertTrue(html.contains(".data-table th { padding: 2mm 2.4mm;"))
        assertTrue(html.contains(".data-table td { padding: 2mm 2.4mm;"))
    }

    @Test
    fun dropsUnsafeColorButKeepsValidOne() {
        val malicious = TextBlock(text = "x", config = BaseBlockConfig(typography = TypographyConfig(color = "red; } body{display:none")))
        val ok = TextBlock(text = "y", config = BaseBlockConfig(typography = TypographyConfig(color = "#ff0000", size = 12)))
        val html = TemplateRenderer.render(Template(version = 1, rows = listOf(Row(listOf(malicious, ok)))))
        assertTrue(!html.contains("display:none"), "unsafe color must be dropped")
        assertTrue(html.contains(".block-2 { font-size: 12pt; color: #ff0000; }"))
    }
}
