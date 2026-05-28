package bambamboole.pdfua.services

class RenderContext {
    private val rules = StringBuilder()

    fun addCss(rule: String) {
        if (rule.isNotBlank()) rules.append(rule).append('\n')
    }

    fun collectedCss(): String = rules.toString()
}
