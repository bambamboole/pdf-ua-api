package bambamboole.pdfua.services

import bambamboole.pdfua.models.template.CssDeclaration
import bambamboole.pdfua.models.template.CssRegistry
import bambamboole.pdfua.models.template.CssRules

internal class RenderContext {
    private val css = CssRegistry()

    fun css(selector: String, rules: CssRules.() -> Unit) {
        css.css(selector, rules)
    }

    fun nestedCss(parent: String, child: String, rules: CssRules.() -> Unit) {
        css.nestedCss(parent, child, rules)
    }

    fun fontFace(rules: CssRules.() -> Unit) {
        css.fontFace(rules)
    }

    fun addCss(declaration: CssDeclaration) {
        css.add(declaration)
    }

    fun collectedCss(): String = css.render()
}
