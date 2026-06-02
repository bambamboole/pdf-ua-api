package bambamboole.pdfua.template

import bambamboole.pdfua.css.CSS_LENGTH_PATTERN
import bambamboole.pdfua.css.CssDeclaration
import bambamboole.pdfua.css.css
import bambamboole.pdfua.css.safeCssWidth
import bambamboole.pdfua.html.Html
import bambamboole.pdfua.html.html
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
enum class TableStyle {
    @SerialName("striped")
    STRIPED,

    @SerialName("bordered")
    BORDERED,

    @SerialName("minimal")
    MINIMAL,
}

@Serializable
data class TableColumn(
    @SchemaPattern("^[A-Za-z][A-Za-z0-9_]*$")
    @SchemaDescription("Runtime data key used for this table column.")
    val key: String,
    @SchemaDescription("Header label rendered for this table column.")
    val label: String,
    @SchemaDescription("Text alignment for this table column.")
    val align: Align? = null,
    @SchemaDescription("Column width as a CSS width value, such as 20mm or 15%.")
    @SchemaPattern(CSS_LENGTH_PATTERN)
    val width: String? = null,
)

@Serializable
@SerialName("table")
data class TableBlock(
    @SchemaDescription("Stable block identifier used for runtime data overrides.")
    override val id: String? = null,
    @SchemaBoolDefault(false) @SchemaGroup(SchemaGroups.STYLE) val numberRows: Boolean = false,
    @SchemaGroup(SchemaGroups.CONTENT) val columns: List<TableColumn> = emptyList(),
    @SchemaEnumDefault("striped") @SchemaGroup(SchemaGroups.STYLE) val style: TableStyle = TableStyle.STRIPED,
    // Runtime row data; supplied via the data-override channel, not by the static template payload.
    @SchemaIgnore val rows: List<JsonObject> = emptyList(),
    override val config: BaseBlockConfig = BaseBlockConfig(),
) : Block {
    @Suppress("MaxLineLength")
    override fun applyData(values: JsonElement): Block = copy(rows = (values as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList())

    override fun render(): Html {
        val headers =
            buildList {
                if (numberRows) add("#")
                columns.forEach { add(it.label) }
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
        when (style) {
            TableStyle.STRIPED -> {
                listOf(
                    css(".$cssId tbody tr:nth-child(even)") {
                        rule("background-color", "#f9fafb")
                    },
                )
            }

            TableStyle.BORDERED -> {
                listOf(
                    css(".$cssId") {
                        rule("border-collapse", "collapse")
                    },
                    css(".$cssId th, .$cssId td") {
                        rule("border", "1px solid #d1d5db")
                    },
                )
            }

            TableStyle.MINIMAL -> {
                listOf(
                    css(".$cssId thead tr") {
                        rule("border-bottom", "2px solid #1a1a2e")
                    },
                    css(".$cssId tbody tr") {
                        rule("border-bottom", "1px solid #e5e7eb")
                    },
                )
            }
        }

    private fun cellsFor(
        row: JsonObject,
        rowIndex: Int,
    ): List<String> =
        buildList {
            if (numberRows) add((rowIndex + 1).toString())
            columns.forEach { column -> add(cellText(row[column.key])) }
        }

    private fun colgroup(): Html {
        val widths =
            buildList<String?> {
                if (numberRows) add(null)
                columns.forEach { add(safeCssWidth(it.width)) }
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
        if (numberRows) {
            if (columnIndex == 0) return "text-align: right;"
            columnIndex--
        }
        val align = columns.getOrNull(columnIndex)?.align ?: return null
        return "text-align: ${align.name.lowercase()};"
    }

    private fun cellText(value: JsonElement?): String =
        when (value) {
            null, JsonNull -> ""
            is JsonPrimitive -> value.content
            else -> value.toString()
        }

    override fun validate(path: ValidationPath): List<ValidationIssue> =
        columns.flatMapIndexed { index, column ->
            val columnPath = path.child("columns").index(index)
            val keyIssues =
                if (SAFE_KEY_VALUE_FIELD_KEY.matches(column.key)) {
                    emptyList()
                } else {
                    listOf(
                        issue(
                            columnPath.child("key"),
                            ValidationCodes.INVALID_KEY,
                            "Table column key is invalid: ${column.key}",
                        ),
                    )
                }
            keyIssues + cssLengthIssues(column.width, columnPath.child("width"))
        }

    override fun validateData(
        value: JsonElement,
        path: ValidationPath,
    ): List<ValidationIssue> {
        val (arr, errs) = requireArray(value, path)
        if (arr == null) return errs
        val allowed = columns.map { it.key }.toSet()
        return arr.flatMapIndexed { i, row ->
            val rowPath = path.index(i)
            val (obj, rowErrs) = requireObject(row, rowPath)
            if (obj == null) rowErrs else allowedKeys(obj, allowed, rowPath)
        }
    }
}
