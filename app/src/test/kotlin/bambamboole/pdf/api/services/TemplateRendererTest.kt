package bambamboole.pdf.api.services

import bambamboole.pdf.api.models.RenderOptions
import bambamboole.pdf.api.models.template.Align
import bambamboole.pdf.api.models.template.BaseBlockConfig
import bambamboole.pdf.api.models.template.HtmlBlock
import bambamboole.pdf.api.models.template.PageConfig
import bambamboole.pdf.api.models.template.PageFormat
import bambamboole.pdf.api.models.template.PageNumbersConfig
import bambamboole.pdf.api.models.template.Row
import bambamboole.pdf.api.models.template.Template
import bambamboole.pdf.api.models.template.TemplateConfig
import bambamboole.pdf.api.models.template.TextBlock
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
}
