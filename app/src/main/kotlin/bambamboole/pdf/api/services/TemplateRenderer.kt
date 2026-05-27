package bambamboole.pdf.api.services

import bambamboole.pdf.api.models.RenderOptions
import bambamboole.pdf.api.models.template.Align
import bambamboole.pdf.api.models.template.Block
import bambamboole.pdf.api.models.template.BlockConfig
import bambamboole.pdf.api.models.template.PageConfig
import bambamboole.pdf.api.models.template.Row
import bambamboole.pdf.api.models.template.SpacingConfig
import bambamboole.pdf.api.models.template.Template
import bambamboole.pdf.api.util.Html
import kotlinx.serialization.json.JsonObject

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
            return "<div class=\"$cssId\">${resolved.render()}</div>"
        }

        fun renderRow(row: Row): String {
            val cells = row.blocks.joinToString("") { block ->
                val widthOnCell = row.blocks.size > 1 && block.config.width != null
                val widthAttr =
                    if (widthOnCell) " style=\"width: ${Html.escape(block.config.width!!)};\"" else ""
                "<td$widthAttr>${renderBlock(block, widthOnCell)}</td>"
            }
            return "<table class=\"row\" role=\"presentation\"><tr>$cells</tr></table>"
        }

        val rowsHtml = template.rows.joinToString("") { renderRow(it) }
        return wrapDocument(rowsHtml, template, ctx, options)
    }

    private fun emitPositioningCss(ctx: RenderContext, cssId: String, config: BlockConfig, widthOnCell: Boolean) {
        val declarations = buildList {
            if (!widthOnCell && config.width != null) add("width: ${config.width}")
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

    private fun wrapDocument(bodyHtml: String, template: Template, ctx: RenderContext, options: RenderOptions): String {
        val page = template.config.page
        val lang = page.locale.substringBefore('_')
        val title = Html.escape(options.title)
        return """
<!DOCTYPE html>
<html lang="$lang">
<head>
<meta charset="UTF-8">
<title>$title</title>
<style>
${pageCss(page)}
${baseCss()}
${ctx.collectedCss()}
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
