package bambamboole.pdfua.template.barcode

import kotlin.test.Test
import kotlin.test.assertEquals

class PayloadsTest {
    @Test
    fun epcPayloadFollowsTwelveLineLayoutAndDropsTrailingEmpties() {
        val payload =
            epcPayload(
                name = "ACME GmbH",
                iban = "DE89370400440532013000",
                bic = null,
                amount = "12.50",
                purpose = null,
                reference = null,
                remittance = "Invoice 2026-001",
            )
        assertEquals(
            listOf(
                "BCD",
                "002",
                "1",
                "SCT",
                "",
                "ACME GmbH",
                "DE89370400440532013000",
                "EUR12.50",
                "",
                "",
                "Invoice 2026-001",
            ).joinToString("\n"),
            payload,
        )
    }

    @Test
    fun vcardPayloadIsVcard3() {
        val payload =
            vCardPayload(
                firstName = "Jane",
                lastName = "Doe",
                org = "ACME",
                title = null,
                phone = "+49123456",
                email = "jane@example.com",
                url = null,
            )
        assertEquals(
            """
            BEGIN:VCARD
            VERSION:3.0
            N:Doe;Jane;;;
            FN:Jane Doe
            ORG:ACME
            TEL;TYPE=WORK,VOICE:+49123456
            EMAIL;TYPE=INTERNET:jane@example.com
            END:VCARD
            """.trimIndent(),
            payload,
        )
    }

    @Test
    fun wifiPayloadEscapesSpecialCharacters() {
        val payload =
            wifiPayload(
                ssid = "My;Net",
                password = "p:a\"ss",
                security = WifiSecurity.WPA,
                hidden = true,
            )
        assertEquals("""WIFI:T:WPA;S:My\;Net;P:p\:a\"ss;H:true;;""", payload)
    }
}
