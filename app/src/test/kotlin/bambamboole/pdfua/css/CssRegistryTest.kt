package bambamboole.pdfua.css

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CssRegistryTest {
    @Test
    fun mergesRulesForTheSameSelector() {
        val css =
            CssRegistry()
                .css(".block-1") {
                    rule("width", "80mm")
                }.css(".block-1") {
                    rule("margin-left", "auto")
                }.render()

        assertEquals(".block-1 { width: 80mm; margin-left: auto; }", css)
    }

    @Test
    fun laterRuleForSamePropertyReplacesEarlierRule() {
        val css =
            CssRegistry()
                .css(".block-1") {
                    rule("color", "#111827")
                    rule("width", "60mm")
                }.css(".block-1") {
                    rule("color", "#ff0000")
                }.render()

        assertEquals(".block-1 { color: #ff0000; width: 60mm; }", css)
    }

    @Test
    fun omitsDeclarationWhenAllRulesAreNullOrBlank() {
        val css =
            CssRegistry()
                .css(".block-1") {
                    rule("width", null)
                    rule("color", "")
                }.render()

        assertEquals("", css)
    }

    @Test
    fun cssHelperReturnsDeclarationEvenWhenRulesAreEmpty() {
        val declaration =
            css(".block-1") {
                rule("width", null)
                rule("color", "")
            }

        assertEquals(CssDeclaration(CssSelector.Rule(".block-1"), emptyList()), declaration)
    }

    @Test
    fun emitsNestedAtRuleForPageMarginBox() {
        val css =
            CssRegistry()
                .nestedCss("@page", "@bottom-right") {
                    rule("content", """counter(page) " / " counter(pages)""")
                    rule("font-size", "8pt")
                }.nestedCss("@page", "@bottom-right") {
                    rule("color", "#9ca3af")
                }.render()

        assertEquals(
            """@page { @bottom-right { content: counter(page) " / " counter(pages); font-size: 8pt; color: #9ca3af; } }""",
            css,
        )
    }

    @Test
    fun preservesRepeatedFontFaceRulesWithoutMerging() {
        val css =
            CssRegistry()
                .fontFace {
                    rule("font-family", cssQuotedString("Lobster"))
                    rule("src", cssUrlValue("https://cdn.example.com/lobster.ttf"))
                    rule("font-weight", "400")
                    rule("font-style", cssIdentifierLikeValue("normal"))
                }.fontFace {
                    rule("font-family", cssQuotedString("Lobster"))
                    rule("src", cssUrlValue("https://cdn.example.com/lobster.ttf"))
                    rule("font-weight", "700")
                    rule("font-style", cssIdentifierLikeValue("normal"))
                }.render()

        assertEquals(
            """
            @font-face { font-family: 'Lobster'; src: url("https://cdn.example.com/lobster.ttf"); font-weight: 400; font-style: normal; }
            @font-face { font-family: 'Lobster'; src: url("https://cdn.example.com/lobster.ttf"); font-weight: 700; font-style: normal; }
            """.trimIndent(),
            css,
        )
    }

    @Test
    fun escapesQuotedStringAndUrlValues() {
        assertEquals("'A\\\\B\\'s Font'", cssQuotedString("A\\B's Font"))
        assertEquals("""url("https://cdn.example.com/font%28v1%29%22.ttf")""", cssUrlValue("https://cdn.example.com/font(v1)\".ttf"))
    }

    @Test
    fun keepsUnsafeValuesExplicitlyFilteredByHelpers() {
        assertNull(safeCssWidth("1mm} body{display:none"))
        assertNull(safeCssColor("red; } body{display:none"))
        assertNull(cssIdentifierLikeValue("normal; } body{display:none"))
    }

    @Test
    fun cssUnitHelpersDropNegativeValues() {
        assertNull(cssMm(-1))
        assertNull(cssMm(-1.0))
        assertNull(cssPt(-1))
        assertNull(cssPx(-1))
    }

    @Test
    fun cssUnitHelpersKeepZeroAndPositiveValues() {
        assertEquals("0mm", cssMm(0))
        assertEquals("12mm", cssMm(12))
        assertEquals("12.5mm", cssMm(12.5))
        assertEquals("0pt", cssPt(0))
        assertEquals("2pt", cssPt(2))
        assertEquals("0px", cssPx(0))
        assertEquals("72px", cssPx(72))
    }
}
