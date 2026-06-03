package bambamboole.pdfua.template.barcode

import bambamboole.pdfua.template.Symbology
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BarcodeRendererTest {
    // A valid sample payload for every symbology, so one test pins the whole Okapi mapping.
    private val samples =
        mapOf(
            Symbology.QR to "HELLO",
            Symbology.DATA_MATRIX to "HELLO",
            Symbology.AZTEC to "HELLO",
            Symbology.PDF417 to "HELLO",
            Symbology.CODE128 to "ABC-123",
            Symbology.CODE39 to "ABC123",
            Symbology.EAN13 to "501234567890",
            Symbology.EAN8 to "5512345",
            Symbology.UPCA to "01234567890",
            Symbology.UPCE to "012345",
            Symbology.ITF14 to "0540123456789",
            Symbology.CODABAR to "A123456A",
            Symbology.GS1_128 to "[01]09521234543213",
            Symbology.GS1_DATAMATRIX to "[01]09521234543213",
            Symbology.GS1_QR to "[01]09521234543213",
            Symbology.GS1_DATABAR to "9521234543213",
            Symbology.GS1_DATABAR_EXPANDED to "[01]09521234543213[3103]000123",
            Symbology.SWISS_QR to
                swissQrPayload(
                    iban = "CH4431999123000889012",
                    creditor = SwissAddress("Robert Schneider AG", "Rue du Lac", "1268/2/22", "2501", "Biel", "CH"),
                    amount = "1949.75",
                    currency = "CHF",
                    debtor = null,
                    referenceType = SwissReferenceType.QRR,
                    reference = "210000000003139471430009017",
                    message = null,
                ),
        )

    @Test
    fun rendersEverySymbologyToInlineSvg() {
        Symbology.entries.forEach { symbology ->
            val payload = samples.getValue(symbology)
            val svg = BarcodeRenderer.toSvg(symbology, payload)
            assertTrue(svg.trimStart().startsWith("<svg"), "$symbology should start with <svg, got: ${svg.take(40)}")
            assertTrue(svg.contains("<rect"), "$symbology SVG should contain rects")
        }
    }

    @Test
    fun invalidContentThrowsBarcodeException() {
        // EAN-13 requires digits only; letters are invalid input.
        assertFailsWith<BarcodeException> {
            BarcodeRenderer.toSvg(Symbology.EAN13, "not-a-number")
        }
    }
}
