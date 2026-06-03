package bambamboole.pdfua.template

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CodeBlockTest {
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
                CodeContent.serializer(),
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
}
