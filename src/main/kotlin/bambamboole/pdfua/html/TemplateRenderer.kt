package bambamboole.pdfua.html

import bambamboole.pdfua.css.CssRegistry
import bambamboole.pdfua.css.CssRule
import bambamboole.pdfua.css.cssIdentifierLikeValue
import bambamboole.pdfua.css.cssMm
import bambamboole.pdfua.css.cssPt
import bambamboole.pdfua.css.cssQuotedString
import bambamboole.pdfua.css.cssUrlValue
import bambamboole.pdfua.css.safeCssColor
import bambamboole.pdfua.css.safeCssWidth
import bambamboole.pdfua.fonts.FontFace
import bambamboole.pdfua.pdf.BackgroundObjectDrawer
import bambamboole.pdfua.services.RenderOptions
import bambamboole.pdfua.template.Align
import bambamboole.pdfua.template.Block
import bambamboole.pdfua.template.BlockConfig
import bambamboole.pdfua.template.CustomPageSize
import bambamboole.pdfua.template.Orientation
import bambamboole.pdfua.template.PageBackgroundConfig
import bambamboole.pdfua.template.PageConfig
import bambamboole.pdfua.template.PageFooterConfig
import bambamboole.pdfua.template.PageNumbersConfig
import bambamboole.pdfua.template.PageSize
import bambamboole.pdfua.template.PresetPageSize
import bambamboole.pdfua.template.Row
import bambamboole.pdfua.template.SpacingConfig
import bambamboole.pdfua.template.Template
import bambamboole.pdfua.template.TypographyConfig
import kotlinx.serialization.json.JsonElement
import java.net.URI

private fun pageDimensionCss(value: Double): String = requireNotNull(cssMm(value)) { "Page dimensions must be positive millimetres: $value" }

private fun pageDimensionCss(value: Int): String = requireNotNull(cssMm(value)) { "Page dimensions must be positive millimetres: $value" }

private fun pageSizeCss(size: PageSize): String =
    when (size) {
        is PresetPageSize -> {
            val (w, h) =
                if (size.orientation == Orientation.LANDSCAPE) {
                    size.format.heightMm to size.format.widthMm
                } else {
                    size.format.widthMm to size.format.heightMm
                }
            "${pageDimensionCss(w)} ${pageDimensionCss(h)}"
        }

        is CustomPageSize -> {
            check(size.width > 0 && size.height > 0) { "Page dimensions must be positive millimetres: ${size.width}x${size.height}" }
            "${pageDimensionCss(size.width)} ${pageDimensionCss(size.height)}"
        }
    }

object TemplateRenderer {
    fun render(
        template: Template,
        data: Map<String, JsonElement> = emptyMap(),
        options: RenderOptions = RenderOptions(),
    ): String {
        check(template.version == 1) { "Unsupported template version: ${template.version}" }

        val css = CssRegistry()
        val page = template.config.page
        page.background?.let { validateBackground(it) }
        emitDocumentCss(css, page, template.fonts, template.config.typography)
        var counter = 0

        fun renderBlock(
            block: Block,
            widthOnCell: Boolean,
        ): Html {
            val resolved = block.id?.let { id -> data[id]?.let(block::applyData) } ?: block
            counter++
            val cssId = "block-$counter"
            emitPositioningCss(css, cssId, resolved.config, widthOnCell)
            emitTypographyCss(css, cssId, resolved.config.typography)
            resolved.renderCss(cssId).forEach(css::add)
            return html {
                tag("div", "class" to cssId) {
                    html(resolved.render())
                }
            }
        }

        fun renderRow(row: Row): Html =
            html {
                tag("table", "class" to "row", "role" to "presentation") {
                    tag("tr") {
                        row.blocks.forEach { block ->
                            val cellWidth = if (row.blocks.size > 1) safeCssWidth(block.config.width) else null
                            tag("td", "style" to cellWidth?.let { "width: $it;" }) {
                                html(renderBlock(block, widthOnCell = cellWidth != null))
                            }
                        }
                    }
                }
            }

        val footerHtml = repeatedFooterHtml(template.config.page.footer, template.config.page.pageNumbers, ::renderRow)
        val rowsHtml = template.rows.map { renderRow(it) }
        return wrapDocument(footerHtml, rowsHtml, template, css, options).serialize()
    }

    private fun emitPositioningCss(
        css: CssRegistry,
        cssId: String,
        config: BlockConfig,
        widthOnCell: Boolean,
    ) {
        css.css(".$cssId") {
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

    private fun emitTypographyCss(
        css: CssRegistry,
        cssId: String,
        typography: TypographyConfig?,
    ) {
        val rules = typographyRules(typography)
        css.css(".$cssId") {
            rules.forEach { rule(it.property, it.value) }
        }
    }

    private fun typographyRules(typography: TypographyConfig?): List<CssRule> {
        if (typography == null) return emptyList()
        return buildList {
            typography.family?.let { add(CssRule("font-family", cssQuotedString(it))) }
            cssPt(typography.size)?.let { add(CssRule("font-size", it)) }
            typography.weight?.let { add(CssRule("font-weight", it.numericValue.toString())) }
            safeCssColor(typography.color)?.let { add(CssRule("color", it)) }
            typography.align?.let { add(CssRule("text-align", it.name.lowercase())) }
        }
    }

    private fun emitFontFaceCss(
        css: CssRegistry,
        fonts: Map<String, FontFace>,
    ) {
        fonts.entries.forEach { (family, face) ->
            face.weight.trim().split(Regex("\\s+")).forEach { weight ->
                css.fontFace {
                    rule("font-family", cssQuotedString(family))
                    rule("src", cssUrlValue(face.src) + """ format("truetype")""")
                    rule("font-weight", weight)
                    rule("font-style", cssIdentifierLikeValue(face.style))
                }
            }
        }
    }

    private fun wrapDocument(
        footerHtml: Html,
        rowsHtml: List<Html>,
        template: Template,
        css: CssRegistry,
        options: RenderOptions,
    ): Html {
        val page = template.config.page
        val lang = page.locale.substringBefore('_')
        val style = css.render()
        val backgroundHtml = page.background?.let(::backgroundObjectHtml)

        return html {
            raw("<!DOCTYPE html>\n")
            tag("html", "lang" to lang) {
                raw("\n")
                tag("head") {
                    raw("\n")
                    voidTag("meta", "charset" to "UTF-8")
                    raw("\n")
                    tag("title") { text(options.title) }
                    raw("\n")
                    tag("style") { raw("\n$style\n") }
                    raw("\n")
                }
                raw("\n")
                tag("body") {
                    raw("\n")
                    if (backgroundHtml != null) {
                        html(backgroundHtml)
                        raw("\n")
                    }
                    if (footerHtml.serialize().isNotEmpty()) {
                        html(footerHtml)
                        raw("\n")
                    }
                    rowsHtml.forEach { row ->
                        html(row)
                        raw("\n")
                    }
                }
                raw("\n")
            }
        }
    }

    private fun repeatedFooterHtml(
        footer: PageFooterConfig,
        pageNumbers: PageNumbersConfig,
        renderRow: (Row) -> Html,
    ): Html {
        if (!footer.hasRepeatedRows()) return Html.EMPTY
        return html {
            tag("footer", "class" to "page-footer page-footer-repeated", "role" to "contentinfo") {
                footer.rows.forEach { html(renderRow(it)) }
                if (pageNumbers.enabled && pageNumbers.position == Align.CENTER) {
                    tag("div", "class" to "page-footer-page-numbers", "aria-hidden" to "true")
                }
            }
        }
    }

    private fun emitDocumentCss(
        css: CssRegistry,
        page: PageConfig,
        fonts: Map<String, FontFace>,
        typography: TypographyConfig?,
    ) {
        emitPageCss(css, page)
        emitFontFaceCss(css, fonts)
        emitBaseCss(css)
        val bodyTypography = typographyRules(typography)
        css.css("body") {
            bodyTypography.forEach { rule(it.property, it.value) }
        }
    }

    private fun emitPageCss(
        css: CssRegistry,
        page: PageConfig,
    ) {
        val hasRepeatedFooter = page.footer.hasRepeatedRows()
        val bottomMarginReserve = if (hasRepeatedFooter) 8 else 0
        css.css("@page") {
            rule("size", pageSizeCss(page.size))
            rule("margin", marginShorthand(page.margins, bottomMarginReserve))
        }
        page.background?.let {
            css.css(".pagebg") {
                rule("position", "running(pagebg)")
            }
            css.nestedCss("@page", "@top-left") {
                rule("content", "element(pagebg)")
            }
        }
        if (hasRepeatedFooter) {
            css.css(".page-footer") {
                rule("font-size", "8pt")
                rule("color", "#6b7280")
            }
            css.css(".page-footer .row") {
                rule("margin", "0")
            }
            css.css(".page-footer-repeated") {
                rule("position", "running(pageFooter)")
                rule("width", "100%")
            }
            css.nestedCss("@page", "@bottom-center") {
                rule("content", "element(pageFooter)")
            }
        }
        if (page.pageNumbers.enabled) {
            if (hasRepeatedFooter && page.pageNumbers.position == Align.CENTER) {
                css.css(".page-footer-page-numbers") {
                    rule("font-size", "8pt")
                    rule("color", "#9ca3af")
                    rule("text-align", "center")
                }
                css.css(".page-footer-page-numbers::after") {
                    rule("content", """counter(page) " / " counter(pages)""")
                }
            } else {
                val position =
                    page.pageNumbers.position.name
                        .lowercase()
                css.nestedCss("@page", "@bottom-$position") {
                    rule("content", """counter(page) " / " counter(pages)""")
                    rule("font-size", "8pt")
                    rule("color", "#9ca3af")
                }
            }
        }
    }

    private fun marginShorthand(
        margins: SpacingConfig,
        bottomReserve: Int = 0,
    ): String {
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

    private fun backgroundObjectHtml(background: PageBackgroundConfig): Html =
        html {
            tag("div", "class" to "pagebg") {
                tag(
                    "object",
                    "type" to BackgroundObjectDrawer.OBJECT_TYPE,
                    "data-src" to background.src,
                    "data-kind" to background.type.name.lowercase(),
                    "style" to "width:1px;height:1px",
                )
            }
        }

    private fun emitBaseCss(css: CssRegistry) {
        css.css("body") {
            rule("font-family", cssQuotedString("Liberation Sans"))
            rule("color", "#111827")
            rule("line-height", "1.35")
        }
        css.css("img, svg") {
            rule("max-width", "100%")
            rule("height", "auto")
        }
        css.css("p") {
            rule("margin", "0 0 2mm")
        }
        css.css("h1, h2, h3, h4, h5, h6") {
            rule("margin", "0 0 3mm")
            rule("line-height", "1.12")
            rule("color", "#111827")
        }
        css.css(".key-value") {
            rule("width", "100%")
            rule("border-collapse", "collapse")
            rule("margin", "0 0 2mm")
        }
        css.css(".key-value td") {
            rule("vertical-align", "top")
            rule("padding", "0 0 2mm")
        }
        css.css(".key-value td:first-child") {
            rule("font-weight", "600")
            rule("color", "#374151")
            rule("padding-right", "4mm")
        }
        css.css(".row") {
            rule("width", "100%")
            rule("border-collapse", "collapse")
            rule("margin", "0 0 4mm")
        }
        css.css(".row > tbody > tr > td, .row > tr > td") {
            rule("vertical-align", "top")
            rule("padding", "0")
        }
        css.css(".data-table") {
            rule("width", "100%")
            rule("border-collapse", "collapse")
            rule("text-align", "left")
        }
        css.css(".data-table th") {
            rule("padding", "2mm 2.4mm")
            rule("background", "#f3f4f6")
            rule("color", "#374151")
            rule("font-weight", "700")
            rule("border-bottom", "1px solid #d1d5db")
        }
        css.css(".data-table td") {
            rule("padding", "2mm 2.4mm")
            rule("border-bottom", "1px solid #e5e7eb")
            rule("vertical-align", "top")
        }
        css.css(".data-table tbody tr:last-child td") {
            rule("border-bottom", "1px solid #d1d5db")
        }
        css.css(".key-value, .data-table thead, .data-table tr") {
            rule("page-break-inside", "avoid")
            rule("break-inside", "avoid")
        }
        css.css("h1, h2, h3, h4, h5, h6") {
            rule("page-break-after", "avoid")
            rule("break-after", "avoid")
        }
    }
}
