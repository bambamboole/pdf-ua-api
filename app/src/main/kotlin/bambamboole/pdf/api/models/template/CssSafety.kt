package bambamboole.pdf.api.models.template

private val SAFE_CSS_WIDTH = Regex("^(auto|\\d+(\\.\\d+)?(mm|cm|in|px|pt|pc|em|rem|%|vw|vh|ch))$")

internal fun safeCssWidth(width: String?): String? = width?.takeIf { SAFE_CSS_WIDTH.matches(it) }
