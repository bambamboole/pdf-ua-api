package bambamboole.pdfua.models.template

internal class CssBuilder {
    private val rules = mutableListOf<String>()

    fun rule(selector: String, declarations: CssDeclarations.() -> Unit): CssBuilder = apply {
        val block = CssDeclarations().apply(declarations).build()
        if (block.isNotBlank()) {
            rules += "$selector { $block }"
        }
    }

    fun nestedRule(selector: String, nestedSelector: String, declarations: CssDeclarations.() -> Unit): CssBuilder = apply {
        val block = CssDeclarations().apply(declarations).build()
        if (block.isNotBlank()) {
            rules += "$selector { $nestedSelector { $block } }"
        }
    }

    fun raw(rule: String): CssBuilder = apply {
        if (rule.isNotBlank()) {
            rules += rule
        }
    }

    fun build(): String = rules.joinToString("\n")

    fun buildRules(): List<String> = rules.toList()
}

internal class CssDeclarations {
    private val declarations = mutableListOf<String>()

    fun declaration(property: String, value: String?) {
        if (!value.isNullOrBlank()) {
            declarations += "$property: $value"
        }
    }

    fun build(): String = declarations.joinToString("; ", postfix = ";").takeIf { declarations.isNotEmpty() }.orEmpty()
}
