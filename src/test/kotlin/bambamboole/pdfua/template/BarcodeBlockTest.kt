package bambamboole.pdfua.template

import bambamboole.pdfua.template.barcode.SwissAddress
import bambamboole.pdfua.template.barcode.SwissReferenceType
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BarcodeBlockTest {
    private val json = Json

    @Test
    fun rawContentPayloadAndDescribe() {
        val content = RawContent(value = "ABC-123")
        assertEquals("ABC-123", content.toPayload())
        assertEquals("ABC-123", content.describe())
    }

    @Test
    fun urlContentDecodesByTypeDiscriminator() {
        val content =
            json.decodeFromString(
                BarcodeContent.serializer(),
                """{"type":"url","url":"https://example.com"}""",
            )
        val url = assertIs<UrlContent>(content)
        assertEquals("https://example.com", url.toPayload())
    }

    @Test
    fun epcContentBuildsPayloadAndDescribes() {
        val content =
            EpcContent(
                name = "ACME GmbH",
                iban = "DE89370400440532013000",
                amount = "12.50",
            )
        assertEquals(true, content.toPayload().startsWith("BCD\n002\n1\nSCT"))
        assertEquals("SEPA payment EUR 12.50 to ACME GmbH", content.describe())
        assertEquals(true, content.supports(Symbology.QR))
        assertEquals(false, content.supports(Symbology.CODE128))
    }

    @Test
    fun epcContentRejectsBadIban() {
        val issues = EpcContent(name = "X", iban = "nope").validate(ValidationPath().child("content"))
        assertEquals(true, issues.any { it.code == ValidationCodes.INVALID_VALUE })
    }

    @Test
    fun gs1ContentSupportsBracketedAiSymbologiesOnly() {
        val content = Gs1Content(elements = listOf(Gs1Element(ai = "01", value = "09521234543213")))
        assertEquals("[01]09521234543213", content.toPayload())
        assertEquals(true, content.supports(Symbology.GS1_128))
        // Plain DataBar (DataBar14) encodes a raw GTIN, not a bracketed-AI string, so it is not supported here:
        assertEquals(false, content.supports(Symbology.GS1_DATABAR))
        assertEquals(false, content.supports(Symbology.QR))
    }

    @Test
    fun renderEmitsInlineSvgWithAutoAltText() {
        val block =
            BarcodeBlock(
                symbology = Symbology.EAN13,
                content = RawContent(value = "501234567890"),
            )
        val svg = block.render().serialize()
        assertEquals(true, svg.contains("role=\"img\""), "expected role attribute in: ${svg.take(80)}")
        assertEquals(true, svg.contains("aria-label=\"EAN-13 barcode: 501234567890\""))
        assertEquals(true, svg.contains("<rect"))
    }

    @Test
    fun renderCssConstrainsHeight() {
        val block =
            BarcodeBlock(
                symbology = Symbology.QR,
                content = TextContent(text = "HELLO"),
                height = "20mm",
            )
        val css = block.renderCss("block-1")
        assertEquals(true, css.isNotEmpty())
    }

    @Test
    fun decodesBarcodeBlockByTypeDiscriminator() {
        val block =
            json.decodeFromString(
                Block.serializer(),
                """{"type":"barcode","symbology":"qr","content":{"type":"url","url":"https://example.com"}}""",
            )
        val code = assertIs<BarcodeBlock>(block)
        assertEquals(Symbology.QR, code.symbology)
    }

    @Test
    fun epcContentApplyDataOverridesScalarFields() {
        val original = EpcContent(name = "ACME GmbH", iban = "DE89370400440532013000", amount = "12.50")
        val updated = original.applyData(json.parseToJsonElement("""{"amount":"99.00","name":"NewCo"}"""))
        val epc = assertIs<EpcContent>(updated)
        assertEquals("99.00", epc.amount)
        assertEquals("NewCo", epc.name)
        assertEquals("DE89370400440532013000", epc.iban)
    }

    @Test
    fun swissQrContentBuildsPayloadAndDescribes() {
        val content =
            SwissQrContent(
                creditorIban = "CH4431999123000889012",
                creditor = SwissAddress("Robert Schneider AG", "Rue du Lac", "1268/2/22", "2501", "Biel", "CH"),
                amount = "1949.75",
                referenceType = SwissReferenceType.QRR,
                reference = "210000000003139471430009017",
            )
        assertEquals(true, content.toPayload().startsWith("SPC\n0200\n1\nCH4431999123000889012"))
        assertEquals("Swiss QR-bill to Robert Schneider AG for CHF 1949.75", content.describe())
        assertEquals(true, content.supports(Symbology.SWISS_QR))
        assertEquals(false, content.supports(Symbology.QR))
    }

    @Test
    fun swissQrContentAcceptsValidQrIbanWithQrr() {
        val content =
            SwissQrContent(
                creditorIban = "CH4431999123000889012",
                creditor = SwissAddress("ACME", postalCode = "2501", town = "Biel", country = "CH"),
                referenceType = SwissReferenceType.QRR,
                reference = "210000000003139471430009017",
            )
        assertEquals(emptyList(), content.validate(ValidationPath().child("content")))
    }

    @Test
    fun swissQrContentRejectsQrIbanWithoutQrr() {
        val content =
            SwissQrContent(
                creditorIban = "CH4431999123000889012",
                creditor = SwissAddress("ACME", postalCode = "2501", town = "Biel", country = "CH"),
                referenceType = SwissReferenceType.NON,
            )
        assertEquals(true, content.validate(ValidationPath().child("content")).isNotEmpty())
    }

    @Test
    fun swissQrContentRejectsBadQrrChecksum() {
        val content =
            SwissQrContent(
                creditorIban = "CH4431999123000889012",
                creditor = SwissAddress("ACME", postalCode = "2501", town = "Biel", country = "CH"),
                referenceType = SwissReferenceType.QRR,
                reference = "210000000003139471430009018",
            )
        assertEquals(true, content.validate(ValidationPath().child("content")).any { it.path.endsWith("reference") })
    }

    @Test
    fun swissQrContentRejectsNonSwissIban() {
        val content =
            SwissQrContent(
                creditorIban = "DE89370400440532013000",
                creditor = SwissAddress("ACME", postalCode = "2501", town = "Biel", country = "CH"),
                referenceType = SwissReferenceType.NON,
            )
        assertEquals(true, content.validate(ValidationPath().child("content")).any { it.path.endsWith("creditorIban") })
    }
}
