package bambamboole.pdf.api.services

import bambamboole.pdf.api.models.RenderOptions
import bambamboole.pdf.api.models.template.Align
import bambamboole.pdf.api.models.template.Block
import bambamboole.pdf.api.models.template.BlockConfig
import bambamboole.pdf.api.models.template.CustomPageSize
import bambamboole.pdf.api.models.template.FontFace
import bambamboole.pdf.api.models.template.Orientation
import bambamboole.pdf.api.models.template.PageBackgroundConfig
import bambamboole.pdf.api.models.template.PageConfig
import bambamboole.pdf.api.models.template.PageFooterConfig
import bambamboole.pdf.api.models.template.PageNumbersConfig
import bambamboole.pdf.api.models.template.PageSize
import bambamboole.pdf.api.models.template.PresetPageSize
import bambamboole.pdf.api.models.template.Row
import bambamboole.pdf.api.models.template.SpacingConfig
import bambamboole.pdf.api.models.template.Template
import bambamboole.pdf.api.models.template.TypographyConfig
import bambamboole.pdf.api.models.template.safeCssWidth
import bambamboole.pdf.api.util.Html
import kotlinx.serialization.json.JsonElement
import java.net.URI

private val UNSAFE_CSS = Regex("[;{}\"\\r\\n]")

private fun safeColor(color: String?): String? = color?.takeIf { it.isNotBlank() && !UNSAFE_CSS.containsMatchIn(it) }

private fun cssString(value: String): String = value.replace("\\", "\\\\").replace("'", "\\'")

private fun cssUrl(value: String): String = value
    .replace("\\", "%5C").replace("\"", "%22").replace("(", "%28").replace(")", "%29").replace("\r", "").replace("\n", "")

private fun mm(value: Double): String {
    val number = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
    return "${number}mm"
}

private fun pageSizeCss(size: PageSize): String = when (size) {
    is PresetPageSize -> {
        val (w, h) = if (size.orientation == Orientation.LANDSCAPE) {
            size.format.heightMm to size.format.widthMm
        } else {
            size.format.widthMm to size.format.heightMm
        }
        "${mm(w)} ${mm(h)}"
    }
    is CustomPageSize -> {
        require(size.width > 0 && size.height > 0) { "Page dimensions must be positive millimetres: ${size.width}x${size.height}" }
        "${size.width}mm ${size.height}mm"
    }
}

object TemplateRenderer {

    fun render(
        template: Template,
        data: Map<String, JsonElement> = emptyMap(),
        options: RenderOptions = RenderOptions(),
    ): String {
        require(template.version == 1) { "Unsupported template version: ${template.version}" }

        val ctx = RenderContext()
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
        val declarations = buildList {
            val width = safeCssWidth(config.width)
            if (!widthOnCell && width != null) add("width: $width")
            when (config.align) {
                Align.CENTER -> { add("margin-left: auto"); add("margin-right: auto") }
                Align.RIGHT -> { add("margin-left: auto"); add("text-align: right") }
                else -> {}
            }
        }
        if (declarations.isNotEmpty()) {
            ctx.addCss(".$cssId { ${declarations.joinToString("; ")}; }")
        }
    }

    private fun emitTypographyCss(ctx: RenderContext, cssId: String, typography: TypographyConfig?) {
        val declarations = typographyDeclarations(typography)
        if (declarations.isNotEmpty()) {
            ctx.addCss(".$cssId { ${declarations.joinToString("; ")}; }")
        }
    }

    private fun typographyDeclarations(typography: TypographyConfig?): List<String> {
        if (typography == null) return emptyList()
        return buildList {
            typography.family?.let { add("font-family: '${cssString(it)}'") }
            typography.size?.let { add("font-size: ${it}pt") }
            typography.weight?.let { add("font-weight: $it") }
            safeColor(typography.color)?.let { add("color: $it") }
            typography.align?.let { add("text-align: ${it.name.lowercase()}") }
        }
    }

    private fun fontFaceCss(fonts: Map<String, FontFace>): String =
        fonts.entries.joinToString("\n") { (family, face) ->
            "@font-face { font-family: '${cssString(family)}'; " +
                "src: url(\"${cssUrl(face.src)}\") format(\"truetype\"); " +
                "font-weight: ${face.weight}; font-style: ${cssString(face.style)}; }"
        }

    private fun wrapDocument(bodyHtml: String, template: Template, ctx: RenderContext, options: RenderOptions): String {
        val page = template.config.page
        page.background?.let { validateBackground(it) }
        val lang = page.locale.substringBefore('_')
        val title = Html.escape(options.title)
        val bodyPrefix = page.background?.let { "${backgroundObjectHtml(it)}\n" }.orEmpty()

        val bodyTypography = typographyDeclarations(template.config.typography)
        val bodyTypographyCss = if (bodyTypography.isNotEmpty()) "body { ${bodyTypography.joinToString("; ")}; }" else ""

        val style = listOf(pageCss(page), fontFaceCss(template.fonts), baseCss(), bodyTypographyCss, ctx.collectedCss())
            .filter { it.isNotBlank() }
            .joinToString("\n")

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

    private fun pageCss(page: PageConfig): String {
        val hasRepeatedFooter = page.footer.hasRepeatedRows()
        val bottomMarginReserve = if (hasRepeatedFooter) 8 else 0
        val css = StringBuilder(
            "@page { size: ${pageSizeCss(page.size)}; margin: ${marginShorthand(page.margins, bottomMarginReserve)}; }",
        )
        page.background?.let {
            css.append(" .pagebg { position: running(pagebg); }")
            css.append(" @page { @top-left { content: element(pagebg); } }")
        }
        if (hasRepeatedFooter) {
            css.append(" .page-footer { font-size: 8pt; color: #6b7280; }")
            css.append(" .page-footer .row { margin: 0; }")
            css.append(" .page-footer-repeated { position: running(pageFooter); width: 100%; }")
            css.append(" @page { @bottom-center { content: element(pageFooter); } }")
        }
        if (page.pageNumbers.enabled) {
            if (hasRepeatedFooter && page.pageNumbers.position == Align.CENTER) {
                css.append(" .page-footer-page-numbers { font-size: 8pt; color: #9ca3af; text-align: center; }")
                css.append(" .page-footer-page-numbers::after { content: counter(page) \" / \" counter(pages); }")
            } else {
                val position = page.pageNumbers.position.name.lowercase()
                css.append(
                    " @page { @bottom-$position { content: counter(page) \" / \" counter(pages); " +
                        "font-size: 8pt; color: #9ca3af; } }",
                )
            }
        }
        return css.toString()
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
        require(background.src.isNotBlank()) { "Page background src cannot be blank" }
        require(!background.src.any { it < ' ' || it == '\u007f' }) { "Page background src contains control characters" }

        val scheme = URI.create(background.src).scheme?.lowercase()
        require(scheme == "http" || scheme == "https" || scheme == "data") {
            "Page background src must use http, https, or data URI"
        }
        if (scheme == "data") {
            val lower = background.src.lowercase()
            require(lower.startsWith("data:image/") || lower.startsWith("data:application/pdf;base64,")) {
                "Page background data URI must be an image or application/pdf base64 URI"
            }
        }
    }

    private fun backgroundObjectHtml(background: PageBackgroundConfig): String =
        """<div class="pagebg"><object type="${BackgroundObjectDrawer.OBJECT_TYPE}" """ +
            """data-src="${Html.escape(background.src)}" data-kind="${background.type.name.lowercase()}" """ +
            """style="width:1px;height:1px"></object></div>"""

    private fun baseCss(): String = """
body { font-family: 'Liberation Sans'; color: #111827; line-height: 1.35; }
img, svg { max-width: 100%; height: auto; }
p { margin: 0 0 2mm; }
h1, h2, h3, h4, h5, h6 { margin: 0 0 3mm; line-height: 1.12; color: #111827; }
.key-value { width: 100%; border-collapse: collapse; margin: 0 0 2mm; }
.key-value td { vertical-align: top; padding: 0 0 2mm; }
.key-value td:first-child { font-weight: 600; color: #374151; padding-right: 4mm; }
.row { width: 100%; border-collapse: collapse; margin: 0 0 4mm; }
.row > tbody > tr > td, .row > tr > td { vertical-align: top; padding: 0; }
.data-table { width: 100%; border-collapse: collapse; text-align: left; }
.data-table th { padding: 2mm 2.4mm; background: #f3f4f6; color: #374151; font-weight: 700; border-bottom: 1px solid #d1d5db; }
.data-table td { padding: 2mm 2.4mm; border-bottom: 1px solid #e5e7eb; vertical-align: top; }
.data-table tbody tr:last-child td { border-bottom: 1px solid #d1d5db; }
""".trim()
}
