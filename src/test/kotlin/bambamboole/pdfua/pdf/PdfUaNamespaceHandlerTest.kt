package bambamboole.pdfua.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PdfUaNamespaceHandlerTest {
    @Test
    fun handlerPointsAtCustomStylesheetPath() {
        val handler = PdfUaNamespaceHandler()
        assertEquals("/css/pdf-ua-default.css", handler.getPathToDefaultStylesheet())
    }

    @Test
    fun customStylesheetResourceIsLoadable() {
        val stream = PdfUaNamespaceHandler::class.java.getResourceAsStream(PdfUaNamespaceHandler.DEFAULT_STYLESHEET_PATH)
        assertNotNull(stream, "user-agent CSS must be on the classpath")
        stream.use {
            val bytes = it.readBytes()
            assertTrue(bytes.isNotEmpty(), "user-agent CSS must not be empty")
        }
    }

    @Test
    fun customStylesheetDropsTheUaUnderlineAndBlueLinkColor() {
        val rules = stripComments(readCss())
        assertFalse(
            rules.contains("a:link, a:visited { text-decoration: underline }"),
            "default link underline rule must not be present — author CSS can opt back in",
        )
        assertFalse(
            rules.contains("#0000ff"),
            "default blue link colour must not be present",
        )
    }

    @Test
    fun customStylesheetClearsBodyMargin() {
        val css = readCss()
        assertTrue(
            Regex("""body\s*\{\s*margin:\s*0\s*}""").containsMatchIn(css),
            "body { margin: 0 } must be present so @page margins aren't fought by an 8px UA default",
        )
    }

    @Test
    fun customStylesheetPreservesSemanticDecorationOnUInsSStrikeDel() {
        val css = readCss()
        assertTrue(
            css.contains("u, ins") && css.contains("text-decoration: underline"),
            "u, ins must keep their semantic underline default",
        )
        assertTrue(
            css.contains("s, strike, del") && css.contains("text-decoration: line-through"),
            "s, strike, del must keep their semantic line-through default",
        )
    }

    private fun readCss(): String =
        PdfUaNamespaceHandler::class.java
            .getResourceAsStream(PdfUaNamespaceHandler.DEFAULT_STYLESHEET_PATH)!!
            .bufferedReader()
            .use { it.readText() }

    private fun stripComments(css: String): String = css.replace(Regex("(?s)/\\*.*?\\*/"), "")
}
