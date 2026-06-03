package bambamboole.pdfua.template.barcode

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class WifiSecurity(
    val token: String,
) {
    @SerialName("WPA")
    WPA("WPA"),

    @SerialName("WEP")
    WEP("WEP"),

    @SerialName("nopass")
    NOPASS("nopass"),
}

/**
 * Builds the EPC/Girocode payload (version 002, UTF-8) for a SEPA credit transfer.
 * Trailing empty lines after the last populated field are dropped, per the EPC guidelines.
 */
fun epcPayload(
    name: String,
    iban: String,
    bic: String?,
    amount: String?,
    purpose: String?,
    reference: String?,
    remittance: String?,
): String {
    val lines =
        listOf(
            "BCD",
            "002",
            "1",
            "SCT",
            bic.orEmpty(),
            name,
            iban,
            amount?.let { "EUR$it" }.orEmpty(),
            purpose.orEmpty(),
            reference.orEmpty(),
            remittance.orEmpty(),
        )
    val lastPopulated = lines.indexOfLast { it.isNotEmpty() }
    return lines.subList(0, lastPopulated + 1).joinToString("\n")
}

/** Builds a vCard 3.0 contact payload; optional fields are omitted when null/blank. */
fun vCardPayload(
    firstName: String,
    lastName: String,
    org: String?,
    title: String?,
    phone: String?,
    email: String?,
    url: String?,
): String =
    buildList {
        add("BEGIN:VCARD")
        add("VERSION:3.0")
        add("N:$lastName;$firstName;;;")
        add("FN:$firstName $lastName")
        org?.takeIf { it.isNotBlank() }?.let { add("ORG:$it") }
        title?.takeIf { it.isNotBlank() }?.let { add("TITLE:$it") }
        phone?.takeIf { it.isNotBlank() }?.let { add("TEL;TYPE=WORK,VOICE:$it") }
        email?.takeIf { it.isNotBlank() }?.let { add("EMAIL;TYPE=INTERNET:$it") }
        url?.takeIf { it.isNotBlank() }?.let { add("URL:$it") }
        add("END:VCARD")
    }.joinToString("\n")

/** Builds a WIFI: join payload; SSID and password are escaped per the de-facto QR WiFi format. */
fun wifiPayload(
    ssid: String,
    password: String?,
    security: WifiSecurity,
    hidden: Boolean,
): String {
    val pwd = if (security == WifiSecurity.NOPASS) "" else escapeWifi(password.orEmpty())
    return "WIFI:T:${security.token};S:${escapeWifi(ssid)};P:$pwd;H:$hidden;;"
}

private fun escapeWifi(value: String): String =
    buildString {
        value.forEach { c ->
            if (c == '\\' || c == ';' || c == ',' || c == ':' || c == '"') append('\\')
            append(c)
        }
    }

@Serializable
enum class SwissReferenceType(
    val token: String,
) {
    @SerialName("QRR")
    QRR("QRR"),

    @SerialName("SCOR")
    SCOR("SCOR"),

    @SerialName("NON")
    NON("NON"),
}

/** A structured Swiss QR-bill address (address type "S"); combined "K" addresses are no longer permitted. */
@Serializable
data class SwissAddress(
    val name: String,
    val street: String? = null,
    val buildingNumber: String? = null,
    val postalCode: String,
    val town: String,
    val country: String,
)

private val QRR_CHECK_TABLE = intArrayOf(0, 9, 4, 6, 8, 2, 7, 1, 3, 5)
private const val MOD10 = 10
private const val IBAN_IID_START = 4
private const val IBAN_IID_END = 9
private const val QR_IID_MIN = 30000
private const val QR_IID_MAX = 31999

/** Modulo-10 recursive check digit (Swiss QRR reference / ISR). [digits] must be digits only. */
fun qrrCheckDigit(digits: String): Int {
    var carry = 0
    for (c in digits) {
        carry = QRR_CHECK_TABLE[(carry + (c - '0')) % MOD10]
    }
    return (MOD10 - carry) % MOD10
}

/** A Swiss QR-IBAN carries an institution id (IID) in the range 30000-31999. */
fun isQrIban(iban: String): Boolean {
    val compact = iban.replace(" ", "").uppercase()
    if (compact.length < IBAN_IID_END) return false
    val iid = compact.substring(IBAN_IID_START, IBAN_IID_END).toIntOrNull() ?: return false
    return iid in QR_IID_MIN..QR_IID_MAX
}

/**
 * Builds the Swiss Payments Code (SPC) v0200 payload for a QR-bill. Structured addresses only.
 * Core-bill scope: omits the reserved ultimate-creditor block (7 empty lines), billing information,
 * and alternative schemes.
 */
fun swissQrPayload(
    iban: String,
    creditor: SwissAddress,
    amount: String?,
    currency: String,
    debtor: SwissAddress?,
    referenceType: SwissReferenceType,
    reference: String?,
    message: String?,
): String {
    val lines =
        buildList {
            add("SPC")
            add("0200")
            add("1")
            add(iban.replace(" ", "").uppercase())
            addAll(structuredAddressLines(creditor))
            repeat(7) { add("") } // Ultimate creditor (reserved, empty)
            add(amount.orEmpty())
            add(currency)
            addAll(debtor?.let(::structuredAddressLines) ?: List(7) { "" })
            add(referenceType.token)
            add(reference?.replace(" ", "").orEmpty())
            add(message.orEmpty())
            add("EPD")
        }
    return lines.joinToString("\n")
}

private fun structuredAddressLines(address: SwissAddress): List<String> =
    listOf(
        "S",
        address.name,
        address.street.orEmpty(),
        address.buildingNumber.orEmpty(),
        address.postalCode,
        address.town,
        address.country.uppercase(),
    )
