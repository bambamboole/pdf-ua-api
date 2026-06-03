package bambamboole.pdfua.template

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BarcodeBlockValidationTest {
    private val root = ValidationPath().child("block")

    @Test
    fun rejectsIncompatibleSymbologyAndContent() {
        val block =
            BarcodeBlock(
                symbology = Symbology.CODE128,
                content = EpcContent(name = "ACME", iban = "DE89370400440532013000"),
            )
        val issues = block.validate(root)
        assertTrue(issues.any { it.path.endsWith("symbology") && it.code == ValidationCodes.INVALID_VALUE })
    }

    @Test
    fun acceptsCompatibleSymbologyAndContent() {
        val block =
            BarcodeBlock(
                symbology = Symbology.QR,
                content = EpcContent(name = "ACME", iban = "DE89370400440532013000", amount = "9.99"),
            )
        assertEquals(emptyList(), block.validate(root))
    }

    @Test
    fun rejectsInvalidHeight() {
        val block =
            BarcodeBlock(
                symbology = Symbology.QR,
                content = TextContent(text = "HI"),
                height = "20 metres",
            )
        assertTrue(block.validate(root).any { it.path.endsWith("height") })
    }
}
