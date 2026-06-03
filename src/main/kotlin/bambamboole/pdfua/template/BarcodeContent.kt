package bambamboole.pdfua.template

import bambamboole.pdfua.template.barcode.WifiSecurity
import bambamboole.pdfua.template.barcode.epcPayload
import bambamboole.pdfua.template.barcode.vCardPayload
import bambamboole.pdfua.template.barcode.wifiPayload
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.net.URI

private const val DESCRIBE_MAX = 64

private val MATRIX_2D =
    setOf(Symbology.QR, Symbology.DATA_MATRIX, Symbology.AZTEC, Symbology.PDF417)

private const val EPC_NAME_MAX = 70

private val IBAN_PATTERN = Regex("^[A-Z]{2}\\d{2}[A-Z0-9]{1,30}$")
private val AMOUNT_PATTERN = Regex("^\\d+(\\.\\d{1,2})?$")
private val GS1_AI_PATTERN = Regex("^\\d{2,4}$")

// GS1 symbologies that accept a bracketed Application-Identifier element string (e.g. "[01]...[10]...").
// Plain GS1_DATABAR (DataBar14) is intentionally excluded: it encodes only a raw GTIN, not bracketed AIs.
private val GS1_AI_SYMBOLOGIES =
    setOf(Symbology.GS1_128, Symbology.GS1_DATAMATRIX, Symbology.GS1_QR, Symbology.GS1_DATABAR_EXPANDED)

@Serializable
sealed interface BarcodeContent {
    /** The string encoded into the symbol. */
    fun toPayload(): String

    /** Short human summary used to build the auto alt text. */
    fun describe(): String

    /** Whether this payload may be encoded in [symbology]. */
    fun supports(symbology: Symbology): Boolean

    fun applyData(values: JsonElement): BarcodeContent

    fun validate(path: ValidationPath): List<ValidationIssue> = emptyList()

    fun validateData(
        value: JsonElement,
        path: ValidationPath,
    ): List<ValidationIssue> = emptyList()
}

@Serializable
@SerialName("raw")
data class RawContent(
    @SchemaGroup(SchemaGroups.CONTENT) val value: String,
) : BarcodeContent {
    override fun toPayload(): String = value

    override fun describe(): String = value.take(DESCRIBE_MAX)

    override fun supports(symbology: Symbology): Boolean = true

    override fun applyData(values: JsonElement): BarcodeContent = copy(value = values.string("value") ?: value)

    override fun validate(path: ValidationPath): List<ValidationIssue> =
        if (value.isBlank()) {
            listOf(issue(path.child("value"), ValidationCodes.INVALID_VALUE, "Code value cannot be blank"))
        } else {
            emptyList()
        }

    override fun validateData(
        value: JsonElement,
        path: ValidationPath,
    ): List<ValidationIssue> {
        val (obj, errs) = requireObject(value, path)
        if (obj == null) return errs
        return allowedKeys(obj, setOf("value"), path) + optionalStringField(obj, "value", path)
    }
}

@Serializable
@SerialName("text")
data class TextContent(
    @SchemaGroup(SchemaGroups.CONTENT) val text: String,
) : BarcodeContent {
    override fun toPayload(): String = text

    override fun describe(): String = text.take(DESCRIBE_MAX)

    override fun supports(symbology: Symbology): Boolean = symbology in MATRIX_2D

    override fun applyData(values: JsonElement): BarcodeContent = copy(text = values.string("text") ?: text)

    override fun validate(path: ValidationPath): List<ValidationIssue> =
        if (text.isBlank()) {
            listOf(issue(path.child("text"), ValidationCodes.INVALID_VALUE, "Code text cannot be blank"))
        } else {
            emptyList()
        }

    override fun validateData(
        value: JsonElement,
        path: ValidationPath,
    ): List<ValidationIssue> {
        val (obj, errs) = requireObject(value, path)
        if (obj == null) return errs
        return allowedKeys(obj, setOf("text"), path) + optionalStringField(obj, "text", path)
    }
}

@Serializable
@SerialName("url")
data class UrlContent(
    @SchemaGroup(SchemaGroups.CONTENT) val url: String,
) : BarcodeContent {
    override fun toPayload(): String = url

    override fun describe(): String = url.take(DESCRIBE_MAX)

    override fun supports(symbology: Symbology): Boolean = symbology in MATRIX_2D

    override fun applyData(values: JsonElement): BarcodeContent = copy(url = values.string("url") ?: url)

    override fun validate(path: ValidationPath): List<ValidationIssue> {
        val scheme = runCatching { URI.create(url).scheme?.lowercase() }.getOrNull()
        return if (scheme == "http" || scheme == "https") {
            emptyList()
        } else {
            listOf(issue(path.child("url"), ValidationCodes.INVALID_URI, "URL must use http or https"))
        }
    }

    override fun validateData(
        value: JsonElement,
        path: ValidationPath,
    ): List<ValidationIssue> {
        val (obj, errs) = requireObject(value, path)
        if (obj == null) return errs
        return allowedKeys(obj, setOf("url"), path) + optionalStringField(obj, "url", path)
    }
}

@Serializable
@SerialName("epc")
data class EpcContent(
    @SchemaGroup(SchemaGroups.CONTENT) val name: String,
    @SchemaGroup(SchemaGroups.CONTENT) val iban: String,
    @SchemaGroup(SchemaGroups.CONTENT) val bic: String? = null,
    @SchemaDescription("Amount in euros, such as 12.50.")
    @SchemaGroup(SchemaGroups.CONTENT)
    val amount: String? = null,
    @SchemaGroup(SchemaGroups.CONTENT) val purpose: String? = null,
    @SchemaGroup(SchemaGroups.CONTENT) val reference: String? = null,
    @SchemaGroup(SchemaGroups.CONTENT) val remittance: String? = null,
) : BarcodeContent {
    override fun toPayload(): String = epcPayload(name, iban, bic, amount, purpose, reference, remittance)

    override fun describe(): String =
        buildString {
            append("SEPA payment")
            amount?.let { append(" EUR ").append(it) }
            append(" to ").append(name)
        }

    override fun supports(symbology: Symbology): Boolean = symbology == Symbology.QR

    override fun applyData(values: JsonElement): BarcodeContent =
        copy(
            name = values.string("name") ?: name,
            iban = values.string("iban") ?: iban,
            amount = values.string("amount") ?: amount,
            reference = values.string("reference") ?: reference,
            remittance = values.string("remittance") ?: remittance,
        )

    override fun validate(path: ValidationPath): List<ValidationIssue> =
        buildList {
            if (name.isBlank() || name.length > EPC_NAME_MAX) {
                add(issue(path.child("name"), ValidationCodes.INVALID_VALUE, "EPC name must be 1-70 characters"))
            }
            if (!IBAN_PATTERN.matches(iban)) {
                add(issue(path.child("iban"), ValidationCodes.INVALID_VALUE, "Invalid IBAN: $iban"))
            }
            amount?.let {
                if (!AMOUNT_PATTERN.matches(it)) {
                    add(issue(path.child("amount"), ValidationCodes.INVALID_VALUE, "Invalid amount: $it"))
                }
            }
        }

    override fun validateData(
        value: JsonElement,
        path: ValidationPath,
    ): List<ValidationIssue> {
        val (obj, errs) = requireObject(value, path)
        if (obj == null) return errs
        val keys = setOf("name", "iban", "amount", "reference", "remittance")
        return allowedKeys(obj, keys, path) + keys.flatMap { optionalStringField(obj, it, path) }
    }
}

@Serializable
@SerialName("vcard")
data class VCardContent(
    @SchemaGroup(SchemaGroups.CONTENT) val firstName: String,
    @SchemaGroup(SchemaGroups.CONTENT) val lastName: String,
    @SchemaGroup(SchemaGroups.CONTENT) val org: String? = null,
    @SchemaGroup(SchemaGroups.CONTENT) val title: String? = null,
    @SchemaGroup(SchemaGroups.CONTENT) val phone: String? = null,
    @SchemaGroup(SchemaGroups.CONTENT) val email: String? = null,
    @SchemaGroup(SchemaGroups.CONTENT) val url: String? = null,
) : BarcodeContent {
    override fun toPayload(): String = vCardPayload(firstName, lastName, org, title, phone, email, url)

    override fun describe(): String = "contact card for $firstName $lastName"

    override fun supports(symbology: Symbology): Boolean = symbology == Symbology.QR

    override fun applyData(values: JsonElement): BarcodeContent =
        copy(
            firstName = values.string("firstName") ?: firstName,
            lastName = values.string("lastName") ?: lastName,
            org = values.string("org") ?: org,
            title = values.string("title") ?: title,
            phone = values.string("phone") ?: phone,
            email = values.string("email") ?: email,
            url = values.string("url") ?: url,
        )

    override fun validate(path: ValidationPath): List<ValidationIssue> =
        buildList {
            if (firstName.isBlank() && lastName.isBlank()) {
                add(issue(path, ValidationCodes.INVALID_VALUE, "vCard requires a first or last name"))
            }
        }

    override fun validateData(
        value: JsonElement,
        path: ValidationPath,
    ): List<ValidationIssue> {
        val (obj, errs) = requireObject(value, path)
        if (obj == null) return errs
        val keys = setOf("firstName", "lastName", "org", "title", "phone", "email", "url")
        return allowedKeys(obj, keys, path) + keys.flatMap { optionalStringField(obj, it, path) }
    }
}

@Serializable
@SerialName("wifi")
data class WifiContent(
    @SchemaGroup(SchemaGroups.CONTENT) val ssid: String,
    @SchemaGroup(SchemaGroups.CONTENT) val password: String? = null,
    @SchemaEnumDefault("WPA")
    @SchemaGroup(SchemaGroups.CONTENT)
    val security: WifiSecurity = WifiSecurity.WPA,
    @SchemaBoolDefault(false)
    @SchemaGroup(SchemaGroups.CONTENT)
    val hidden: Boolean = false,
) : BarcodeContent {
    override fun toPayload(): String = wifiPayload(ssid, password, security, hidden)

    override fun describe(): String = "Wi-Fi network $ssid"

    override fun supports(symbology: Symbology): Boolean = symbology == Symbology.QR

    override fun applyData(values: JsonElement): BarcodeContent =
        copy(
            ssid = values.string("ssid") ?: ssid,
            password = values.string("password") ?: password,
        )

    override fun validate(path: ValidationPath): List<ValidationIssue> =
        if (ssid.isBlank()) {
            listOf(issue(path.child("ssid"), ValidationCodes.INVALID_VALUE, "Wi-Fi SSID cannot be blank"))
        } else {
            emptyList()
        }

    override fun validateData(
        value: JsonElement,
        path: ValidationPath,
    ): List<ValidationIssue> {
        val (obj, errs) = requireObject(value, path)
        if (obj == null) return errs
        return allowedKeys(obj, setOf("ssid", "password"), path) +
            optionalStringField(obj, "ssid", path) +
            optionalStringField(obj, "password", path)
    }
}

@Serializable
data class Gs1Element(
    @SchemaDescription("GS1 Application Identifier, such as 01 or 3103.")
    @SchemaGroup(SchemaGroups.CONTENT)
    val ai: String,
    @SchemaGroup(SchemaGroups.CONTENT) val value: String,
)

@Serializable
@SerialName("gs1")
data class Gs1Content(
    @SchemaGroup(SchemaGroups.CONTENT) val elements: List<Gs1Element>,
) : BarcodeContent {
    override fun toPayload(): String = elements.joinToString("") { "[${it.ai}]${it.value}" }

    override fun describe(): String = "GS1 data (${elements.joinToString(", ") { "(${it.ai})" }})"

    override fun supports(symbology: Symbology): Boolean = symbology in GS1_AI_SYMBOLOGIES

    override fun applyData(values: JsonElement): BarcodeContent = this

    override fun validateData(
        value: JsonElement,
        path: ValidationPath,
    ): List<ValidationIssue> {
        val (obj, errs) = requireObject(value, path)
        if (obj == null) return errs
        return allowedKeys(obj, emptySet(), path)
    }

    override fun validate(path: ValidationPath): List<ValidationIssue> =
        buildList {
            if (elements.isEmpty()) {
                add(issue(path.child("elements"), ValidationCodes.INVALID_VALUE, "GS1 content needs at least one element"))
            }
            elements.forEachIndexed { index, element ->
                val elementPath = path.child("elements").index(index)
                if (!GS1_AI_PATTERN.matches(element.ai)) {
                    add(issue(elementPath.child("ai"), ValidationCodes.INVALID_VALUE, "Invalid GS1 AI: ${element.ai}"))
                }
                if (element.value.isBlank()) {
                    add(issue(elementPath.child("value"), ValidationCodes.INVALID_VALUE, "GS1 element value cannot be blank"))
                }
            }
        }
}
