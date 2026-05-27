package bambamboole.pdf.api.services

import bambamboole.pdf.api.models.RenderOptions
import bambamboole.pdf.api.models.template.Align
import bambamboole.pdf.api.models.template.Block
import bambamboole.pdf.api.models.template.BlockConfig
import bambamboole.pdf.api.models.template.FontFace
import bambamboole.pdf.api.models.template.PageConfig
import bambamboole.pdf.api.models.template.Row
import bambamboole.pdf.api.models.template.SpacingConfig
import bambamboole.pdf.api.models.template.Template
import bambamboole.pdf.api.models.template.TypographyConfig
import bambamboole.pdf.api.util.Html
import kotlinx.serialization.json.JsonObject

private val SAFE_WIDTH = Regex("^(auto|\\d+(\\.\\d+)?(mm|cm|in|px|pt|pc|em|rem|%|vw|vh|ch))$")
private val UNSAFE_CSS = Regex("[;{}\"\\r\\n]")

private fun safeWidth(width: String?): String? = width?.takeIf { SAFE_WIDTH.matches(it) }

private fun safeColor(color: String?): String? = color?.takeIf { it.isNotBlank() && !UNSAFE_CSS.containsMatchIn(it) }

private fun cssString(value: String): String = value.replace("\\", "\\\\").replace("'", "\\'")

private fun cssUrl(value: String): String = value
    .replace("\\", "%5C").replace("\"", "%22").replace("(", "%28").replace(")", "%29").replace("\r", "").replace("\n", "")

object TemplateRenderer {

    fun render(
        template: Template,
        data: Map<String, JsonObject> = emptyMap(),
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
            return "<div class=\"$cssId\">${resolved.render()}</div>"
        }

        fun renderRow(row: Row): String {
            val cells = row.blocks.joinToString("") { block ->
                val cellWidth = if (row.blocks.size > 1) safeWidth(block.config.width) else null
                val widthAttr = if (cellWidth != null) " style=\"width: $cellWidth;\"" else ""
                "<td$widthAttr>${renderBlock(block, widthOnCell = cellWidth != null)}</td>"
            }
            return "<table class=\"row\" role=\"presentation\"><tr>$cells</tr></table>"
        }

        val rowsHtml = template.rows.joinToString("") { renderRow(it) }
        return wrapDocument(rowsHtml, template, ctx, options)
    }

    private fun emitPositioningCss(ctx: RenderContext, cssId: String, config: BlockConfig, widthOnCell: Boolean) {
        val declarations = buildList {
            val width = safeWidth(config.width)
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
        val lang = page.locale.substringBefore('_')
        val title = Html.escape(options.title)

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
$bodyHtml
</body>
</html>
""".trim()
    }

    private fun pageCss(page: PageConfig): String {
        val css = StringBuilder("@page { size: ${page.format.cssSize}; margin: ${marginShorthand(page.margins)}; }")
        if (page.pageNumbers.enabled) {
            val position = page.pageNumbers.position.name.lowercase()
            css.append(
                " @page { @bottom-$position { content: counter(page) \" / \" counter(pages); " +
                    "font-size: 8pt; color: #9ca3af; } }",
            )
        }
        return css.toString()
    }

    private fun marginShorthand(margins: SpacingConfig): String {
        val top = margins.top ?: 0
        val right = margins.right ?: 0
        val bottom = margins.bottom ?: 0
        val left = margins.left ?: 0
        return "${top}mm ${right}mm ${bottom}mm ${left}mm"
    }

    private fun baseCss(): String = """
body { font-family: 'Liberation Sans'; color: #111827; line-height: 1.35; }
p { margin: 0 0 2mm; }
h1, h2, h3, h4, h5, h6 { margin: 0 0 3mm; line-height: 1.12; color: #111827; }
.row { width: 100%; border-collapse: collapse; margin: 0 0 4mm; }
.row > tbody > tr > td, .row > tr > td { vertical-align: top; padding: 0; }
""".trim()
}
