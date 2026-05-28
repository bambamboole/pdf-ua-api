package bambamboole.pdfua.models.template

private val SAFE_CSS_WIDTH = Regex("^(auto|\\d+(\\.\\d+)?(mm|cm|in|px|pt|pc|em|rem|%|vw|vh|ch))$")
private val SAFE_CSS_COLOR = Regex("^[#a-zA-Z0-9(),.%\\s-]+$")
private val UNSAFE_CSS_IDENTIFIER = Regex("[;{}\"'\\r\\n]")

internal fun safeCssWidth(width: String?): String? = width?.takeIf { SAFE_CSS_WIDTH.matches(it) }

internal fun safeCssColor(value: String?): String? =
    value?.takeIf { it.isNotBlank() && SAFE_CSS_COLOR.matches(it) }

internal fun cssIdentifierLikeValue(value: String): String? =
    value.takeIf { it.isNotBlank() && !UNSAFE_CSS_IDENTIFIER.containsMatchIn(it) }

internal fun cssQuotedString(value: String): String =
    "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"

internal fun cssUrlValue(value: String): String =
    "url(\"" + value
        .replace("\\", "%5C")
        .replace("\"", "%22")
        .replace("(", "%28")
        .replace(")", "%29")
        .replace("\r", "")
        .replace("\n", "") + "\")"

internal fun cssMm(value: Double): String {
    val number = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
    return "${number}mm"
}

internal fun cssMm(value: Int): String = "${value}mm"

internal fun cssPt(value: Int): String = "${value}pt"

internal fun cssPx(value: Int): String = "${value}px"
