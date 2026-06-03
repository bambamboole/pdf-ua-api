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
