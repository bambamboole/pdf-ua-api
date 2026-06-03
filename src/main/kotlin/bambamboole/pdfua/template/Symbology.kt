package bambamboole.pdfua.template

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Symbology(
    val label: String,
    val gs1: Boolean,
) {
    @SerialName("qr")
    QR("QR code", false),

    @SerialName("datamatrix")
    DATA_MATRIX("Data Matrix code", false),

    @SerialName("aztec")
    AZTEC("Aztec code", false),

    @SerialName("pdf417")
    PDF417("PDF417 code", false),

    @SerialName("code128")
    CODE128("Code 128 barcode", false),

    @SerialName("code39")
    CODE39("Code 39 barcode", false),

    @SerialName("ean13")
    EAN13("EAN-13 barcode", false),

    @SerialName("ean8")
    EAN8("EAN-8 barcode", false),

    @SerialName("upca")
    UPCA("UPC-A barcode", false),

    @SerialName("upce")
    UPCE("UPC-E barcode", false),

    @SerialName("itf14")
    ITF14("ITF-14 barcode", false),

    @SerialName("codabar")
    CODABAR("Codabar barcode", false),

    @SerialName("gs1-128")
    GS1_128("GS1-128 barcode", true),

    @SerialName("gs1-datamatrix")
    GS1_DATAMATRIX("GS1 Data Matrix code", true),

    @SerialName("gs1-qr")
    GS1_QR("GS1 QR code", true),

    @SerialName("gs1-databar")
    GS1_DATABAR("GS1 DataBar barcode", true),

    @SerialName("gs1-databar-expanded")
    GS1_DATABAR_EXPANDED("GS1 DataBar Expanded barcode", true),

    @SerialName("swiss-qr")
    SWISS_QR("Swiss QR code", false),
}
