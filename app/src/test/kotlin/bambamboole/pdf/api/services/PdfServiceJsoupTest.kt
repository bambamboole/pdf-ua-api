package bambamboole.pdf.api.services

import org.jsoup.Jsoup
import org.jsoup.helper.W3CDom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Demonstration tests showing jsoup HTML manipulation capabilities
 * before PDF conversion.
 */
class PdfServiceJsoupTest {

    @Test
    fun demonstrateJsoupManipulation() {
        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <title>Test Document</title>
                <meta name="subject" content="Testing jsoup manipulation"/>
            </head>
            <body>
                <h1>Title</h1>
                <p class="intro">Introduction paragraph</p>
                <table>
                    <tr><td>Cell 1</td><td>Cell 2</td></tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        // Parse with jsoup
        val jsoupDoc = Jsoup.parse(html)

        // Demonstrate manipulation capabilities
        // 1. Add CSS classes to elements
        jsoupDoc.select("table").addClass("pdf-table bordered")

        // 2. Add/modify attributes
        jsoupDoc.select("img").attr("loading", "eager")

        // 3. Add meta tags dynamically
        jsoupDoc.head().appendElement("meta")
            .attr("name", "keywords")
            .attr("content", "pdf, document")

        // 4. Modify text content
        val introText = jsoupDoc.select("p.intro").text()
        assertTrue(introText.isNotEmpty())

        // 5. Count elements
        val tableCount = jsoupDoc.select("table").size
        assertEquals(1, tableCount)

        // Convert to W3C Document
        val w3cDoc = W3CDom().fromJsoup(jsoupDoc)

        // Verify conversion worked
        assertTrue(w3cDoc.documentElement != null)
        assertEquals("html", w3cDoc.documentElement.tagName)
    }

    @Test
    fun demonstrateConditionalManipulation() {
        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <title>Conditional Test</title>
                <meta name="subject" content="Testing conditional manipulation"/>
            </head>
            <body>
                <div class="header">Header</div>
                <div class="content">
                    <p>Paragraph 1</p>
                    <p>Paragraph 2</p>
                    <p>Paragraph 3</p>
                </div>
            </body>
            </html>
        """.trimIndent()

        val jsoupDoc = Jsoup.parse(html)

        // Conditional manipulation based on content
        val paragraphs = jsoupDoc.select("p")
        if (paragraphs.size > 2) {
            // Add a class to indicate many paragraphs
            jsoupDoc.select("div.content").addClass("has-many-paragraphs")
        }

        // Check if manipulation worked
        assertTrue(jsoupDoc.select("div.content.has-many-paragraphs").isNotEmpty())
    }

    @Test
    fun demonstrateTextManipulation() {
        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <title>Text Manipulation</title>
                <meta name="subject" content="Testing text manipulation"/>
            </head>
            <body>
                <p id="target">Original Text</p>
            </body>
            </html>
        """.trimIndent()

        val jsoupDoc = Jsoup.parse(html)

        // Get original text
        val originalText = jsoupDoc.select("#target").text()
        assertEquals("Original Text", originalText)

        // Modify by replacing element content
        val targetElement = jsoupDoc.selectFirst("#target")
        targetElement?.text("Modified Text via jsoup")

        // Verify modification
        val modifiedText = jsoupDoc.selectFirst("#target")?.text()
        assertEquals("Modified Text via jsoup", modifiedText)
    }
}
