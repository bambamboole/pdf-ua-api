package bambamboole.pdfua.css

data class CssDeclaration(
    val selector: CssSelector,
    val rules: List<CssRule>,
)

data class CssRule(
    val property: String,
    val value: String,
)

sealed interface CssSelector {
    fun render(rules: String): String

    data class Rule(
        val value: String,
    ) : CssSelector {
        override fun render(rules: String): String = "$value { $rules }"
    }

    data class Nested(
        val parent: String,
        val child: String,
    ) : CssSelector {
        override fun render(rules: String): String = "$parent { $child { $rules } }"
    }

    data class Repeated(
        val value: String,
        val index: Int,
    ) : CssSelector {
        override fun render(rules: String): String = "$value { $rules }"
    }
}

internal class CssRegistry {
    private val declarations = linkedMapOf<CssSelector, LinkedHashMap<String, CssRule>>()
    private var repeatedRuleIndex = 0

    fun css(
        selector: String,
        rules: CssRules.() -> Unit,
    ): CssRegistry =
        apply {
            add(CssSelector.Rule(selector), rules)
        }

    fun nestedCss(
        parent: String,
        child: String,
        rules: CssRules.() -> Unit,
    ): CssRegistry =
        apply {
            add(CssSelector.Nested(parent, child), rules)
        }

    fun fontFace(rules: CssRules.() -> Unit): CssRegistry =
        apply {
            add(CssSelector.Repeated("@font-face", repeatedRuleIndex++), rules)
        }

    fun add(declaration: CssDeclaration): CssRegistry =
        apply {
            if (declaration.rules.isEmpty()) return@apply
            val mergedRules = declarations.getOrPut(declaration.selector) { linkedMapOf() }
            declaration.rules.forEach { rule -> mergedRules[rule.property] = rule }
        }

    fun render(): String =
        declarations
            .mapNotNull { (selector, rules) ->
                if (rules.isEmpty()) {
                    null
                } else {
                    selector.render(rules.values.joinToString("; ", postfix = ";") { "${it.property}: ${it.value}" })
                }
            }.joinToString("\n")

    private fun add(
        selector: CssSelector,
        rules: CssRules.() -> Unit,
    ) {
        add(CssRules().apply(rules).declaration(selector))
    }
}

internal class CssRules {
    private val rules = mutableListOf<CssRule>()

    fun rule(
        property: String,
        value: String?,
    ) {
        if (!value.isNullOrBlank()) {
            rules += CssRule(property, value)
        }
    }

    fun declaration(selector: String): CssDeclaration = declaration(CssSelector.Rule(selector))

    fun declaration(selector: CssSelector): CssDeclaration = CssDeclaration(selector, rules.toList())
}

internal fun css(
    selector: String,
    rules: CssRules.() -> Unit,
): CssDeclaration = CssRules().apply(rules).declaration(selector)

internal fun nestedCss(
    parent: String,
    child: String,
    rules: CssRules.() -> Unit,
): CssDeclaration = CssRules().apply(rules).declaration(CssSelector.Nested(parent, child))

/** Shared by [safeCssWidth] (render-time sanitization), the JSON Schema `pattern`, and `validate()`. */
const val CSS_LENGTH_PATTERN = "^(auto|\\d+(\\.\\d+)?(mm|cm|in|px|pt|pc|em|rem|%|vw|vh|ch))$"
private val SAFE_CSS_WIDTH = Regex(CSS_LENGTH_PATTERN)
private val SAFE_CSS_COLOR = Regex("^[#a-zA-Z0-9(),.%\\s-]+$")
private val UNSAFE_CSS_IDENTIFIER = Regex("[;{}\"'\\r\\n]")

internal fun safeCssWidth(width: String?): String? = width?.takeIf { SAFE_CSS_WIDTH.matches(it) }

internal fun safeCssColor(value: String?): String? = value?.takeIf { it.isNotBlank() && SAFE_CSS_COLOR.matches(it) }

internal fun cssIdentifierLikeValue(value: String): String? = value.takeIf { it.isNotBlank() && !UNSAFE_CSS_IDENTIFIER.containsMatchIn(it) }

internal fun cssQuotedString(value: String): String = "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"

internal fun cssUrlValue(value: String): String =
    "url(\"" +
        value
            .replace("\\", "%5C")
            .replace("\"", "%22")
            .replace("(", "%28")
            .replace(")", "%29")
            .replace("\r", "")
            .replace("\n", "") + "\")"

internal fun cssMm(value: Double): String? {
    if (value < 0) return null
    val number = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
    return "${number}mm"
}

internal fun cssMm(value: Int): String? = value.takeIf { it >= 0 }?.let { "${it}mm" }

internal fun cssMm(value: Int?): String? = value?.let { cssMm(it) }

internal fun cssPt(value: Int): String? = value.takeIf { it >= 0 }?.let { "${it}pt" }

internal fun cssPt(value: Int?): String? = value?.let { cssPt(it) }
