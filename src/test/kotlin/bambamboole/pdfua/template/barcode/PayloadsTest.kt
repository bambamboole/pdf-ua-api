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

    @Test
    fun qrrCheckDigitMatchesSixExample() {
        // SIX worked example QRR reference 210000000003139471430009017; final digit 7 is the check digit.
        assertEquals(7, qrrCheckDigit("21000000000313947143000901"))
    }

    @Test
    fun isQrIbanDetectsIidRange() {
        assertEquals(true, isQrIban("CH4431999123000889012")) // IID 31999
        assertEquals(false, isQrIban("CH9300762011623852957")) // IID 00762
    }

    @Test
    fun swissQrPayloadNormalizesIbanReferenceAndCountry() {
        val payload =
            swissQrPayload(
                iban = "ch44 3199 9123 0008 89012",
                creditor = SwissAddress("ACME", postalCode = "2501", town = "Biel", country = "ch"),
                amount = null,
                currency = "CHF",
                debtor = null,
                referenceType = SwissReferenceType.QRR,
                reference = "21 00000000 03139471 43000901 7",
                message = null,
            )
        val lines = payload.split("\n")
        assertEquals("CH4431999123000889012", lines[3]) // IBAN: spaces stripped, uppercased
        assertEquals("CH", lines[10]) // creditor country uppercased
        assertEquals("210000000003139471430009017", lines[28]) // reference: spaces stripped
    }

    @Test
    fun swissQrPayloadFollowsSpcV0200Layout() {
        val payload =
            swissQrPayload(
                iban = "CH4431999123000889012",
                creditor = SwissAddress("Robert Schneider AG", "Rue du Lac", "1268/2/22", "2501", "Biel", "CH"),
                amount = "1949.75",
                currency = "CHF",
                debtor = SwissAddress("Pia-Maria Rutschmann-Schnyder", "Grosse Marktgasse", "28", "9400", "Rorschach", "CH"),
                referenceType = SwissReferenceType.QRR,
                reference = "210000000003139471430009017",
                message = "Instruction of 15.09.2019",
            )
        assertEquals(
            listOf(
                "SPC",
                "0200",
                "1",
                "CH4431999123000889012",
                "S",
                "Robert Schneider AG",
                "Rue du Lac",
                "1268/2/22",
                "2501",
                "Biel",
                "CH",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "1949.75",
                "CHF",
                "S",
                "Pia-Maria Rutschmann-Schnyder",
                "Grosse Marktgasse",
                "28",
                "9400",
                "Rorschach",
                "CH",
                "QRR",
                "210000000003139471430009017",
                "Instruction of 15.09.2019",
                "EPD",
            ).joinToString("\n"),
            payload,
        )
    }
}
