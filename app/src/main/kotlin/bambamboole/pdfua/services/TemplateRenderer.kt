package bambamboole.pdfua.services

import bambamboole.pdfua.models.RenderOptions
import bambamboole.pdfua.models.template.Align
import bambamboole.pdfua.models.template.Block
import bambamboole.pdfua.models.template.BlockConfig
import bambamboole.pdfua.models.template.CssRule
import bambamboole.pdfua.models.template.CustomPageSize
import bambamboole.pdfua.models.template.FontFace
import bambamboole.pdfua.models.template.Orientation
import bambamboole.pdfua.models.template.PageBackgroundConfig
import bambamboole.pdfua.models.template.PageConfig
import bambamboole.pdfua.models.template.PageFooterConfig
import bambamboole.pdfua.models.template.PageNumbersConfig
import bambamboole.pdfua.models.template.PageSize
import bambamboole.pdfua.models.template.PresetPageSize
import bambamboole.pdfua.models.template.Row
import bambamboole.pdfua.models.template.SpacingConfig
import bambamboole.pdfua.models.template.Template
import bambamboole.pdfua.models.template.TypographyConfig
import bambamboole.pdfua.models.template.cssIdentifierLikeValue
import bambamboole.pdfua.models.template.cssMm
import bambamboole.pdfua.models.template.cssPt
import bambamboole.pdfua.models.template.cssQuotedString
import bambamboole.pdfua.models.template.cssUrlValue
import bambamboole.pdfua.models.template.safeCssColor
import bambamboole.pdfua.models.template.safeCssWidth
import bambamboole.pdfua.util.Html
import kotlinx.serialization.json.JsonElement
import java.net.URI

private fun pageSizeCss(size: PageSize): String = when (size) {
    is PresetPageSize -> {
        val (w, h) = if (size.orientation == Orientation.LANDSCAPE) {
            size.format.heightMm to size.format.widthMm
        } else {
            size.format.widthMm to size.format.heightMm
        }
        "${cssMm(w)} ${cssMm(h)}"
    }
    is CustomPageSize -> {
        check(size.width > 0 && size.height > 0) { "Page dimensions must be positive millimetres: ${size.width}x${size.height}" }
        "${cssMm(size.width)} ${cssMm(size.height)}"
    }
}

object TemplateRenderer {

    fun render(
        template: Template,
        data: Map<String, JsonElement> = emptyMap(),
        options: RenderOptions = RenderOptions(),
    ): String {
        check(template.version == 1) { "Unsupported template version: ${template.version}" }

        val ctx = RenderContext()
        val page = template.config.page
        page.background?.let { validateBackground(it) }
        emitDocumentCss(ctx, page, template.fonts, template.config.typography)
        var counter = 0

        fun renderBlock(block: Block, widthOnCell: Boolean): String {
            val resolved = block.id?.let { id -> data[id]?.let(block::applyData) } ?: block
            counter++
            val cssId = "block-$counter"
            emitPositioningCss(ctx, cssId, resolved.config, widthOnCell)
            emitTypographyCss(ctx, cssId, resolved.config.typography)
            resolved.renderCss(cssId).forEach(ctx::addCss)
            return "<div class=\"$cssId\">${resolved.render()}</div>"
        }

        fun renderRow(row: Row): String {
            val cells = row.blocks.joinToString("") { block ->
                val cellWidth = if (row.blocks.size > 1) safeCssWidth(block.config.width) else null
                val widthAttr = if (cellWidth != null) " style=\"width: $cellWidth;\"" else ""
                "<td$widthAttr>${renderBlock(block, widthOnCell = cellWidth != null)}</td>"
            }
            return "<table class=\"row\" role=\"presentation\"><tr>$cells</tr></table>"
        }

        val footerHtml = repeatedFooterHtml(template.config.page.footer, template.config.page.pageNumbers, ::renderRow)
        val rowsHtml = template.rows.joinToString("") { renderRow(it) }
        return wrapDocument(footerHtml + rowsHtml, template, ctx, options)
    }

    private fun emitPositioningCss(ctx: RenderContext, cssId: String, config: BlockConfig, widthOnCell: Boolean) {
        ctx.css(".$cssId") {
            val width = if (widthOnCell) null else safeCssWidth(config.width)
            rule("width", width)
            when (config.align) {
                Align.CENTER -> {
                    rule("margin-left", "auto")
                    rule("margin-right", "auto")
                }
                Align.RIGHT -> {
                    rule("margin-left", "auto")
                    rule("text-align", "right")
                }
                else -> {}
            }
        }
    }

    private fun emitTypographyCss(ctx: RenderContext, cssId: String, typography: TypographyConfig?) {
        val rules = typographyRules(typography)
        ctx.css(".$cssId") {
            rules.forEach { rule(it.property, it.value) }
        }
    }

    private fun typographyRules(typography: TypographyConfig?): List<CssRule> {
        if (typography == null) return emptyList()
        return buildList {
            typography.family?.let { add(CssRule("font-family", cssQuotedString(it))) }
            typography.size?.let { add(CssRule("font-size", cssPt(it))) }
            typography.weight?.let { add(CssRule("font-weight", it.numericValue.toString())) }
            safeCssColor(typography.color)?.let { add(CssRule("color", it)) }
            typography.align?.let { add(CssRule("text-align", it.name.lowercase())) }
        }
    }

    private fun emitFontFaceCss(ctx: RenderContext, fonts: Map<String, FontFace>) {
        fonts.entries.forEach { (family, face) ->
            face.weight.trim().split(Regex("\\s+")).forEach { weight ->
                ctx.fontFace {
                    rule("font-family", cssQuotedString(family))
                    rule("src", cssUrlValue(face.src) + """ format("truetype")""")
                    rule("font-weight", weight)
                    rule("font-style", cssIdentifierLikeValue(face.style))
                }
            }
        }
    }

    private fun wrapDocument(bodyHtml: String, template: Template, ctx: RenderContext, options: RenderOptions): String {
        val page = template.config.page
        val lang = page.locale.substringBefore('_')
        val title = Html.escape(options.title)
        val bodyPrefix = page.background?.let { "${backgroundObjectHtml(it)}\n" }.orEmpty()

        val style = ctx.collectedCss()

        return """
<!DOCTYPE html>
<html lang="$lang">
<head>
<meta charset="UTF-8">
<title>$title</title>
<style>
$style
</style>
</head>
<body>
$bodyPrefix$bodyHtml
</body>
</html>
""".trim()
    }

    private fun repeatedFooterHtml(
        footer: PageFooterConfig,
        pageNumbers: PageNumbersConfig,
        renderRow: (Row) -> String,
    ): String {
        if (!footer.hasRepeatedRows()) return ""
        val rows = footer.rows.joinToString("") { renderRow(it) }
        val pageNumbersHtml = if (pageNumbers.enabled && pageNumbers.position == Align.CENTER) {
            """<div class="page-footer-page-numbers" aria-hidden="true"></div>"""
        } else {
            ""
        }
        return """<footer class="page-footer page-footer-repeated" role="contentinfo">$rows$pageNumbersHtml</footer>"""
    }

    private fun emitDocumentCss(
        ctx: RenderContext,
        page: PageConfig,
        fonts: Map<String, FontFace>,
        typography: TypographyConfig?,
    ) {
        emitPageCss(ctx, page)
        emitFontFaceCss(ctx, fonts)
        emitBaseCss(ctx)
        val bodyTypography = typographyRules(typography)
        ctx.css("body") {
            bodyTypography.forEach { rule(it.property, it.value) }
        }
    }

    private fun emitPageCss(ctx: RenderContext, page: PageConfig) {
        val hasRepeatedFooter = page.footer.hasRepeatedRows()
        val bottomMarginReserve = if (hasRepeatedFooter) 8 else 0
        ctx.css("@page") {
            rule("size", pageSizeCss(page.size))
            rule("margin", marginShorthand(page.margins, bottomMarginReserve))
        }
        page.background?.let {
            ctx.css(".pagebg") {
                rule("position", "running(pagebg)")
            }
            ctx.nestedCss("@page", "@top-left") {
                rule("content", "element(pagebg)")
            }
        }
        if (hasRepeatedFooter) {
            ctx.css(".page-footer") {
                rule("font-size", "8pt")
                rule("color", "#6b7280")
            }
            ctx.css(".page-footer .row") {
                rule("margin", "0")
            }
            ctx.css(".page-footer-repeated") {
                rule("position", "running(pageFooter)")
                rule("width", "100%")
            }
            ctx.nestedCss("@page", "@bottom-center") {
                rule("content", "element(pageFooter)")
            }
        }
        if (page.pageNumbers.enabled) {
            if (hasRepeatedFooter && page.pageNumbers.position == Align.CENTER) {
                ctx.css(".page-footer-page-numbers") {
                    rule("font-size", "8pt")
                    rule("color", "#9ca3af")
                    rule("text-align", "center")
                }
                ctx.css(".page-footer-page-numbers::after") {
                    rule("content", """counter(page) " / " counter(pages)""")
                }
            } else {
                val position = page.pageNumbers.position.name.lowercase()
                ctx.nestedCss("@page", "@bottom-$position") {
                    rule("content", """counter(page) " / " counter(pages)""")
                    rule("font-size", "8pt")
                    rule("color", "#9ca3af")
                }
            }
        }
    }

    private fun marginShorthand(margins: SpacingConfig, bottomReserve: Int = 0): String {
        val top = margins.top ?: 0
        val right = margins.right ?: 0
        val bottom = (margins.bottom ?: 0) + bottomReserve
        val left = margins.left ?: 0
        return "${top}mm ${right}mm ${bottom}mm ${left}mm"
    }

    private fun PageFooterConfig.hasRepeatedRows(): Boolean = repeat && rows.isNotEmpty()

    private fun validateBackground(background: PageBackgroundConfig) {
        check(background.src.isNotBlank()) { "Page background src cannot be blank" }
        check(!background.src.any { it < ' ' || it == '\u007f' }) { "Page background src contains control characters" }

        val scheme = URI.create(background.src).scheme?.lowercase()
        check(scheme == "http" || scheme == "https" || scheme == "data") {
            "Page background src must use http, https, or data URI"
        }
        if (scheme == "data") {
            val lower = background.src.lowercase()
            check(lower.startsWith("data:image/") || lower.startsWith("data:application/pdf;base64,")) {
                "Page background data URI must be an image or application/pdf base64 URI"
            }
        }
    }

    private fun backgroundObjectHtml(background: PageBackgroundConfig): String =
        """<div class="pagebg"><object type="${BackgroundObjectDrawer.OBJECT_TYPE}" """ +
            """data-src="${Html.escape(background.src)}" data-kind="${background.type.name.lowercase()}" """ +
            """style="width:1px;height:1px"></object></div>"""

    private fun emitBaseCss(ctx: RenderContext) {
        ctx.css("body") {
            rule("font-family", cssQuotedString("Liberation Sans"))
            rule("color", "#111827")
            rule("line-height", "1.35")
        }
        ctx.css("img, svg") {
            rule("max-width", "100%")
            rule("height", "auto")
        }
        ctx.css("p") {
            rule("margin", "0 0 2mm")
        }
        ctx.css("h1, h2, h3, h4, h5, h6") {
            rule("margin", "0 0 3mm")
            rule("line-height", "1.12")
            rule("color", "#111827")
        }
        ctx.css(".key-value") {
            rule("width", "100%")
            rule("border-collapse", "collapse")
            rule("margin", "0 0 2mm")
        }
        ctx.css(".key-value td") {
            rule("vertical-align", "top")
            rule("padding", "0 0 2mm")
        }
        ctx.css(".key-value td:first-child") {
            rule("font-weight", "600")
            rule("color", "#374151")
            rule("padding-right", "4mm")
        }
        ctx.css(".row") {
            rule("width", "100%")
            rule("border-collapse", "collapse")
            rule("margin", "0 0 4mm")
        }
        ctx.css(".row > tbody > tr > td, .row > tr > td") {
            rule("vertical-align", "top")
            rule("padding", "0")
        }
        ctx.css(".data-table") {
            rule("width", "100%")
            rule("border-collapse", "collapse")
            rule("text-align", "left")
        }
        ctx.css(".data-table th") {
            rule("padding", "2mm 2.4mm")
            rule("background", "#f3f4f6")
            rule("color", "#374151")
            rule("font-weight", "700")
            rule("border-bottom", "1px solid #d1d5db")
        }
        ctx.css(".data-table td") {
            rule("padding", "2mm 2.4mm")
            rule("border-bottom", "1px solid #e5e7eb")
            rule("vertical-align", "top")
        }
        ctx.css(".data-table tbody tr:last-child td") {
            rule("border-bottom", "1px solid #d1d5db")
        }
        ctx.css(".key-value, .data-table thead, .data-table tr") {
            rule("page-break-inside", "avoid")
            rule("break-inside", "avoid")
        }
        ctx.css("h1, h2, h3, h4, h5, h6") {
            rule("page-break-after", "avoid")
            rule("break-after", "avoid")
        }
    }
}
