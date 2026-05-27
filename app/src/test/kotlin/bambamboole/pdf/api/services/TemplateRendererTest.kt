package bambamboole.pdf.api.services

import bambamboole.pdf.api.models.RenderOptions
import bambamboole.pdf.api.models.template.Align
import bambamboole.pdf.api.models.template.BaseBlockConfig
import bambamboole.pdf.api.models.template.FontFace
import bambamboole.pdf.api.models.template.HtmlBlock
import bambamboole.pdf.api.models.template.PageConfig
import bambamboole.pdf.api.models.template.PageFormat
import bambamboole.pdf.api.models.template.PageNumbersConfig
import bambamboole.pdf.api.models.template.Row
import bambamboole.pdf.api.models.template.Template
import bambamboole.pdf.api.models.template.TemplateConfig
import bambamboole.pdf.api.models.template.TextBlock
import bambamboole.pdf.api.models.template.TypographyConfig
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TemplateRendererTest {

    private fun template(vararg blocks: bambamboole.pdf.api.models.template.Block, config: TemplateConfig = TemplateConfig()) =
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
    fun emitsPageSizeAndMargins() {
        val cfg = TemplateConfig(page = PageConfig(format = PageFormat.A4))
        val html = TemplateRenderer.render(template(TextBlock(text = "x"), config = cfg))
        assertTrue(html.contains("@page { size: A4; margin: 20mm 20mm 20mm 25mm; }"))
    }

    @Test
    fun emitsPageNumbersWhenEnabled() {
        val cfg = TemplateConfig(page = PageConfig(pageNumbers = PageNumbersConfig(enabled = true, position = Align.RIGHT)))
        val html = TemplateRenderer.render(template(TextBlock(text = "x"), config = cfg))
        assertTrue(html.contains("@bottom-right"))
        assertTrue(html.contains("counter(page)"))
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
        assertFailsWith<IllegalArgumentException> {
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
    fun externalFontEmitsFontFaceAndUsesFamily() {
        val tpl = Template(
            version = 1,
            fonts = mapOf("Lobster" to FontFace(src = "https://cdn.example.com/lobster.ttf")),
            rows = listOf(Row(listOf(TextBlock(text = "x", config = BaseBlockConfig(typography = TypographyConfig(family = "Lobster")))))),
        )
        val html = TemplateRenderer.render(tpl)
        assertTrue(
            html.contains("@font-face { font-family: 'Lobster'; src: url(\"https://cdn.example.com/lobster.ttf\") format(\"truetype\"); font-weight: 400; font-style: normal; }"),
        )
        assertTrue(html.contains(".block-1 { font-family: 'Lobster'; }"))
    }

    @Test
    fun bundledFamilyIsUsedAsIsWithoutFontFace() {
        val cfg = TemplateConfig(typography = TypographyConfig(family = "Inter"))
        val html = TemplateRenderer.render(template(TextBlock(text = "x"), config = cfg))
        assertTrue(html.contains("body { font-family: 'Inter'; }"))
        assertTrue(!html.contains("@font-face"), "bundled family must not emit @font-face")
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
