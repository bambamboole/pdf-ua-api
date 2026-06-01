package bambamboole.pdfua.fonts

import com.openhtmltopdf.extend.FSSupplier
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FSFontUseCase
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.EnumSet

/**
 * Registers every bundled font referenced by [html] on this renderer builder. PDF rendering passes
 * [fallbackFinal] = true so the bundled fonts act as the final glyph fallback for PDF/UA coverage;
 * image rendering does not.
 */
fun BaseRendererBuilder<*, *>.useBundledFontsFor(
    html: String,
    fallbackFinal: Boolean = false,
) {
    BundledFonts.fontBytesForHtml(html).forEach { (font, bytes) ->
        val supplier = FSSupplier<InputStream> { ByteArrayInputStream(bytes) }
        val style =
            when (font.style) {
                BundledFonts.FontStyle.Normal -> FontStyle.NORMAL
                BundledFonts.FontStyle.Italic -> FontStyle.ITALIC
            }
        if (fallbackFinal) {
            useFont(supplier, font.family, font.weight, style, true, EnumSet.of(FSFontUseCase.FALLBACK_FINAL))
        } else {
            useFont(supplier, font.family, font.weight, style, true)
        }
    }
}
