package bambamboole.pdfua.models.template

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CssBuilderTest {

    @Test
    fun emitsRuleWithJoinedDeclarationsAndTrailingSemicolon() {
        val css = CssBuilder()
            .rule(".block-1") {
                declaration("width", "80mm")
                declaration("margin-left", "auto")
            }
            .build()

        assertEquals(".block-1 { width: 80mm; margin-left: auto; }", css)
    }

    @Test
    fun omitsRuleWhenAllDeclarationsAreNullOrBlank() {
        val css = CssBuilder()
            .rule(".block-1") {
                declaration("width", null)
                declaration("color", "")
            }
            .build()

        assertEquals("", css)
    }

    @Test
    fun emitsNestedAtRuleForPageMarginBox() {
        val css = CssBuilder()
            .nestedRule("@page", "@bottom-right") {
                declaration("content", """counter(page) " / " counter(pages)""")
                declaration("font-size", "8pt")
                declaration("color", "#9ca3af")
            }
            .build()

        assertEquals(
            """@page { @bottom-right { content: counter(page) " / " counter(pages); font-size: 8pt; color: #9ca3af; } }""",
            css,
        )
    }

    @Test
    fun emitsOneFontFaceRulePerWeight() {
        val css = CssBuilder()
            .rule("@font-face") {
                declaration("font-family", cssQuotedString("Lobster"))
                declaration("src", cssUrlValue("https://cdn.example.com/lobster.ttf"))
                declaration("font-weight", "400")
                declaration("font-style", cssIdentifierLikeValue("normal"))
            }
            .rule("@font-face") {
                declaration("font-family", cssQuotedString("Lobster"))
                declaration("src", cssUrlValue("https://cdn.example.com/lobster.ttf"))
                declaration("font-weight", "700")
                declaration("font-style", cssIdentifierLikeValue("normal"))
            }
            .build()

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
}
