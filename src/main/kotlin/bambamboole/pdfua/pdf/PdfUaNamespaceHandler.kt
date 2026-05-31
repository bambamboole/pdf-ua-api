package bambamboole.pdfua.pdf

import com.openhtmltopdf.simple.extend.XhtmlNamespaceHandler

/**
 * Replaces OpenHTMLToPDF's user-agent stylesheet with one we ship in resources.
 * See `src/main/resources/css/pdf-ua-default.css` for the substantive deltas.
 *
 * Extends [XhtmlNamespaceHandler] (not the css-only base) so HTML 4 presentational
 * attributes on legacy tags (table border, img align, etc.) still translate to inline CSS.
 *
 * Wired via [com.openhtmltopdf.pdfboxout.PdfRendererBuilder.useNamespaceHandler]. The Java2D
 * image-rendering path doesn't expose a similar hook (Java2DRenderer constructs a stock
 * XhtmlNamespaceHandler internally), so image output continues to use OHP's defaults.
 */
class PdfUaNamespaceHandler : XhtmlNamespaceHandler() {
    public override fun getPathToDefaultStylesheet(): String = DEFAULT_STYLESHEET_PATH

    companion object {
        const val DEFAULT_STYLESHEET_PATH = "/css/pdf-ua-default.css"
    }
}
