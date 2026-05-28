package bambamboole.pdfua.template

import bambamboole.pdfua.css.CssDeclaration
import bambamboole.pdfua.html.Html
import bambamboole.pdfua.css.css
import bambamboole.pdfua.html.html
import bambamboole.pdfua.css.safeCssWidth
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
enum class TableStyle {
    @SerialName("striped") STRIPED,
    @SerialName("bordered") BORDERED,
    @SerialName("minimal") MINIMAL,
}

@Serializable
data class TableColumn(
    val key: String,
    val label: String,
    val align: Align? = null,
    val width: String? = null,
)

@Serializable
data class TableConfig(
    override val typography: TypographyConfig? = null,
    override val spacing: SpacingConfig? = null,
    override val width: String? = null,
    override val align: Align? = null,
    val numberRows: Boolean = false,
    val columns: List<TableColumn> = emptyList(),
    val style: TableStyle = TableStyle.STRIPED,
) : BlockConfig

@Serializable
@SerialName("table")
data class TableBlock(
    override val id: String? = null,
    val rows: List<JsonObject> = emptyList(),
    override val config: TableConfig = TableConfig(),
) : Block {
    override fun applyData(values: JsonElement): Block =
        copy(rows = (values as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList())

    override fun render(): Html {
        val headers = buildList {
            if (config.numberRows) add("#")
            config.columns.forEach { add(it.label) }
        }
        return html {
            tag("table", "class" to "data-table") {
                html(colgroup())
                tag("thead") {
                    tag("tr") {
                        headers.forEachIndexed { index, header ->
                            tag("th", "style" to alignStyle(index)) { text(header) }
                        }
                    }
                }
                tag("tbody") {
                    rows.forEachIndexed { rowIndex, row ->
                        tag("tr") {
                            cellsFor(row, rowIndex).forEachIndexed { index, cell ->
                                tag("td", "style" to alignStyle(index)) { text(cell) }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun renderCss(cssId: String): List<CssDeclaration> =
        when (config.style) {
            TableStyle.STRIPED -> listOf(
                css(".$cssId tbody tr:nth-child(even)") {
                    rule("background-color", "#f9fafb")
                },
            )
            TableStyle.BORDERED -> listOf(
                css(".$cssId") {
                    rule("border-collapse", "collapse")
                },
                css(".$cssId th, .$cssId td") {
                    rule("border", "1px solid #d1d5db")
                },
            )
            TableStyle.MINIMAL -> listOf(
                css(".$cssId thead tr") {
                    rule("border-bottom", "2px solid #1a1a2e")
                },
                css(".$cssId tbody tr") {
                    rule("border-bottom", "1px solid #e5e7eb")
                },
            )
        }

    private fun cellsFor(row: JsonObject, rowIndex: Int): List<String> =
        buildList {
            if (config.numberRows) add((rowIndex + 1).toString())
            config.columns.forEach { column -> add(cellText(row[column.key])) }
        }

    private fun colgroup(): Html {
        val widths = buildList<String?> {
            if (config.numberRows) add(null)
            config.columns.forEach { add(safeCssWidth(it.width)) }
        }
        if (widths.none { !it.isNullOrEmpty() }) return Html.EMPTY
        return html {
            tag("colgroup") {
                widths.forEach { width ->
                    voidTag("col", "style" to width?.takeIf { it.isNotEmpty() }?.let { "width: $it;" })
                }
            }
        }
    }

    private fun alignStyle(index: Int): String? {
        var columnIndex = index
        if (config.numberRows) {
            if (columnIndex == 0) return "text-align: right;"
            columnIndex--
        }
        val align = config.columns.getOrNull(columnIndex)?.align ?: return null
        return "text-align: ${align.name.lowercase()};"
    }

    private fun cellText(value: JsonElement?): String =
        when (value) {
            null, JsonNull -> ""
            is JsonPrimitive -> value.content
            else -> value.toString()
        }

    override fun validate(path: ValidationPath): List<ValidationIssue> =
        config.columns.flatMapIndexed { index, column ->
            if (SAFE_KEY_VALUE_FIELD_KEY.matches(column.key)) emptyList()
            else listOf(
                issue(
                    path.child("config").child("columns").index(index).child("key"),
                    ValidationCodes.INVALID_KEY,
                    "Table column key is invalid: ${column.key}",
                ),
            )
        }

    override fun validateData(value: JsonElement, path: ValidationPath): List<ValidationIssue> {
        val (arr, errs) = requireArray(value, path)
        if (arr == null) return errs
        val allowed = config.columns.map { it.key }.toSet()
        return arr.flatMapIndexed { i, row ->
            val rowPath = path.index(i)
            val (obj, rowErrs) = requireObject(row, rowPath)
            if (obj == null) rowErrs else allowedKeys(obj, allowed, rowPath)
        }
    }
}
