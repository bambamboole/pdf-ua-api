package bambamboole.pdfua.template.barcode

import bambamboole.pdfua.template.Symbology
import uk.org.okapibarcode.backend.AztecCode
import uk.org.okapibarcode.backend.Codabar
import uk.org.okapibarcode.backend.Code128
import uk.org.okapibarcode.backend.Code2Of5
import uk.org.okapibarcode.backend.Code3Of9
import uk.org.okapibarcode.backend.DataBar14
import uk.org.okapibarcode.backend.DataBarExpanded
import uk.org.okapibarcode.backend.DataMatrix
import uk.org.okapibarcode.backend.Ean
import uk.org.okapibarcode.backend.OkapiException
import uk.org.okapibarcode.backend.Pdf417
import uk.org.okapibarcode.backend.QrCode
import uk.org.okapibarcode.backend.SwissQrCode
import uk.org.okapibarcode.backend.Symbol
import uk.org.okapibarcode.backend.Upc
import uk.org.okapibarcode.graphics.Color
import uk.org.okapibarcode.output.SvgRenderer
import java.io.ByteArrayOutputStream

/** Thrown when OkapiBarcode rejects content; mapped to a 4xx validation error upstream. */
class BarcodeException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

object BarcodeRenderer {
    private const val MAGNIFICATION = 2.0
    private val PAPER = Color(255, 255, 255)
    private val INK = Color(0, 0, 0)

    /** Renders [payload] in [symbology] to an inline SVG string (no XML prolog). */
    fun toSvg(
        symbology: Symbology,
        payload: String,
    ): String {
        val symbol = createSymbol(symbology)
        if (symbology.gs1) {
            symbol.dataType = Symbol.DataType.GS1
        }
        try {
            symbol.content = payload
        } catch (e: OkapiException) {
            throw BarcodeException("Invalid ${symbology.label} content: ${e.message}", e)
        }
        val out = ByteArrayOutputStream()
        SvgRenderer(out, MAGNIFICATION, PAPER, INK, false).render(symbol)
        return out.toString(Charsets.UTF_8)
    }

    @Suppress("CyclomaticComplexMethod")
    private fun createSymbol(symbology: Symbology): Symbol =
        when (symbology) {
            Symbology.QR, Symbology.GS1_QR -> QrCode()
            Symbology.DATA_MATRIX, Symbology.GS1_DATAMATRIX -> DataMatrix()
            Symbology.AZTEC -> AztecCode()
            Symbology.PDF417 -> Pdf417()
            Symbology.CODE128, Symbology.GS1_128 -> Code128()
            Symbology.CODE39 -> Code3Of9()
            Symbology.EAN13 -> Ean().apply { mode = Ean.Mode.EAN13 }
            Symbology.EAN8 -> Ean().apply { mode = Ean.Mode.EAN8 }
            Symbology.UPCA -> Upc().apply { mode = Upc.Mode.UPCA }
            Symbology.UPCE -> Upc().apply { mode = Upc.Mode.UPCE }
            Symbology.ITF14 -> Code2Of5().apply { mode = Code2Of5.ToFMode.ITF14 }
            Symbology.CODABAR -> Codabar()
            Symbology.GS1_DATABAR -> DataBar14()
            Symbology.GS1_DATABAR_EXPANDED -> DataBarExpanded()
            Symbology.SWISS_QR -> SwissQrCode()
        }
}
