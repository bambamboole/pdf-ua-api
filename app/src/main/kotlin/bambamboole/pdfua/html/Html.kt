package bambamboole.pdfua.html

@JvmInline
value class Html internal constructor(internal val raw: String) {
    fun serialize(): String = raw

    companion object {
        val EMPTY: Html = Html("")

        fun escape(value: String): String = buildString(value.length) { appendEscaped(this, value) }

        fun raw(value: String): Html = if (value.isEmpty()) EMPTY else Html(value)

        fun text(value: String): Html = if (value.isEmpty()) EMPTY else Html(escape(value))
    }
}

@DslMarker
annotation class HtmlDsl

@HtmlDsl
class HtmlBuilder internal constructor() {
    private val sb = StringBuilder()

    fun tag(
        name: String,
        vararg attrs: Pair<String, String?>,
        content: HtmlBuilder.() -> Unit = {},
    ): HtmlBuilder = apply {
        openTag(name, attrs)
        content()
        sb.append("</").append(name).append('>')
    }

    fun voidTag(name: String, vararg attrs: Pair<String, String?>): HtmlBuilder = apply {
        openTag(name, attrs)
    }

    fun text(value: String): HtmlBuilder = apply { appendEscaped(sb, value) }

    fun raw(value: String): HtmlBuilder = apply { sb.append(value) }

    fun html(value: Html): HtmlBuilder = apply { sb.append(value.raw) }

    internal fun build(): Html = Html(sb.toString())

    private fun openTag(name: String, attrs: Array<out Pair<String, String?>>) {
        sb.append('<').append(name)
        for ((attrName, attrValue) in attrs) {
            if (attrValue != null) {
                sb.append(' ').append(attrName).append("=\"")
                appendEscaped(sb, attrValue)
                sb.append('"')
            }
        }
        sb.append('>')
    }
}

fun html(block: HtmlBuilder.() -> Unit): Html = HtmlBuilder().apply(block).build()

private fun appendEscaped(sb: StringBuilder, value: String) {
    for (c in value) {
        when (c) {
            '&' -> sb.append("&amp;")
            '<' -> sb.append("&lt;")
            '>' -> sb.append("&gt;")
            '"' -> sb.append("&quot;")
            '\'' -> sb.append("&#039;")
            else -> sb.append(c)
        }
    }
}
