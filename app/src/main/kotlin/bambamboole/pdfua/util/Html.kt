package bambamboole.pdfua.util

object Html {
    fun escape(value: String): String = buildString(value.length) {
        for (c in value) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#039;")
                else -> append(c)
            }
        }
    }
}
