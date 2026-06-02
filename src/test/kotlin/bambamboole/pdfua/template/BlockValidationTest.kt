package bambamboole.pdfua.template

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlockValidationTest {
    private val path = ValidationPath().child("block")

    // ----- Static invariants (validate) -----

    @Test
    fun headingValidateAcceptsLevel1To6() {
        (1..6).forEach { level ->
            assertTrue(HeadingBlock(text = "x", level = level).validate(path).isEmpty())
        }
    }

    @Test
    fun headingValidateRejectsLevelOutsideRange() {
        val errs = HeadingBlock(text = "x", level = 7).validate(path)
        assertEquals(1, errs.size)
        assertEquals(ValidationCodes.OUT_OF_RANGE, errs[0].code)
        assertEquals("\$.block.level", errs[0].path)
    }

    @Test
    fun spacerValidateRejectsInvalidCssLength() {
        val errs = SpacerBlock(height = "5").validate(path)
        assertEquals(1, errs.size)
        assertEquals(ValidationCodes.INVALID_VALUE, errs[0].code)
        assertEquals("\$.block.height", errs[0].path)
    }

    @Test
    fun spacerValidateAcceptsValidCssLength() {
        assertTrue(SpacerBlock(height = "12mm").validate(path).isEmpty())
    }

    @Test
    fun imageValidateRejectsInvalidMaxHeight() {
        val errs = ImageBlock(src = "https://example.com/x.png", maxHeight = "big").validate(path)
        assertEquals(1, errs.size)
        assertEquals(ValidationCodes.INVALID_VALUE, errs[0].code)
        assertEquals("\$.block.maxHeight", errs[0].path)
    }

    @Test
    fun dividerValidateRejectsInvalidThickness() {
        val errs = DividerBlock(thickness = "2").validate(path)
        assertEquals(1, errs.size)
        assertEquals(ValidationCodes.INVALID_VALUE, errs[0].code)
        assertEquals("\$.block.thickness", errs[0].path)
    }

    @Test
    fun keyValueValidateRejectsInvalidFieldKeys() {
        val block =
            KeyValueBlock(
                fields = listOf(KeyValueField("good", "G"), KeyValueField("1bad", "B")),
            )
        val errs = block.validate(path)
        assertEquals(1, errs.size)
        assertEquals(ValidationCodes.INVALID_KEY, errs[0].code)
        assertEquals("\$.block.fields[1].key", errs[0].path)
    }

    @Test
    fun keyValueValidateRejectsInvalidLabelWidth() {
        val errs = KeyValueBlock(labelWidth = "30").validate(path)
        assertEquals(1, errs.size)
        assertEquals(ValidationCodes.INVALID_VALUE, errs[0].code)
        assertEquals("\$.block.labelWidth", errs[0].path)
    }

    @Test
    fun tableValidateRejectsInvalidColumnWidth() {
        val errs = TableBlock(columns = listOf(TableColumn("k", "L", width = "wide"))).validate(path)
        assertEquals(1, errs.size)
        assertEquals(ValidationCodes.INVALID_VALUE, errs[0].code)
        assertEquals("\$.block.columns[0].width", errs[0].path)
    }

    @Test
    fun tableValidateRejectsInvalidColumnKeys() {
        val block =
            TableBlock(
                columns = listOf(TableColumn("sku", "SKU"), TableColumn("1bad", "X")),
            )
        val errs = block.validate(path)
        assertEquals(1, errs.size)
        assertEquals(ValidationCodes.INVALID_KEY, errs[0].code)
        assertEquals("\$.block.columns[1].key", errs[0].path)
    }

    @Test
    fun blocksWithoutInvariantsReturnEmpty() {
        assertTrue(TextBlock(text = "x").validate(path).isEmpty())
        assertTrue(HtmlBlock(html = "<b>x</b>").validate(path).isEmpty())
        assertTrue(ImageBlock(src = "x.png").validate(path).isEmpty())
        assertTrue(SpacerBlock().validate(path).isEmpty())
        assertTrue(DividerBlock().validate(path).isEmpty())
    }

    // ----- Data-shape (validateData) — object-content blocks -----

    @Test
    fun textValidateDataAcceptsTextString() {
        val obj = buildJsonObject { put("text", "hello") }
        assertTrue(TextBlock(text = "").validateData(obj, path).isEmpty())
    }

    @Test
    fun textValidateDataRejectsNonObject() {
        val errs = TextBlock(text = "").validateData(JsonArray(emptyList()), path)
        assertEquals(1, errs.size)
        assertEquals(ValidationCodes.INVALID_TYPE, errs[0].code)
        assertEquals("\$.block", errs[0].path)
    }

    @Test
    fun textValidateDataRejectsNonStringText() {
        val obj = buildJsonObject { put("text", 42) }
        val errs = TextBlock(text = "").validateData(obj, path)
        assertEquals(1, errs.size)
        assertEquals(ValidationCodes.INVALID_TYPE, errs[0].code)
        assertEquals("\$.block.text", errs[0].path)
    }

    @Test
    fun textValidateDataRejectsUnknownFields() {
        val obj =
            buildJsonObject {
                put("text", "hi")
                put("extra", "x")
            }
        val errs = TextBlock(text = "").validateData(obj, path)
        assertEquals(1, errs.size)
        assertEquals(ValidationCodes.UNKNOWN_FIELD, errs[0].code)
        assertEquals("\$.block.extra", errs[0].path)
    }

    @Test
    fun htmlValidateDataAcceptsHtmlString() {
        val obj = buildJsonObject { put("html", "<b>x</b>") }
        assertTrue(HtmlBlock(html = "").validateData(obj, path).isEmpty())
    }

    @Test
    fun headingValidateDataAcceptsTextString() {
        val obj = buildJsonObject { put("text", "Title") }
        assertTrue(HeadingBlock(text = "").validateData(obj, path).isEmpty())
    }

    @Test
    fun imageValidateDataAcceptsSrcAndAlt() {
        val obj =
            buildJsonObject {
                put("src", "u.png")
                put("alt", "alt")
            }
        assertTrue(ImageBlock(src = "x.png").validateData(obj, path).isEmpty())
    }

    @Test
    fun imageValidateDataRejectsNonStringSrc() {
        val obj = buildJsonObject { put("src", 42) }
        val errs = ImageBlock(src = "x.png").validateData(obj, path)
        assertEquals("\$.block.src", errs[0].path)
        assertEquals(ValidationCodes.INVALID_TYPE, errs[0].code)
    }

    // ----- KeyValueBlock data shape -----

    @Test
    fun keyValueValidateDataAcceptsConfiguredKeysAsStringOrNull() {
        val block =
            KeyValueBlock(
                fields = listOf(KeyValueField("a", "A"), KeyValueField("b", "B")),
            )
        val obj =
            buildJsonObject {
                put("a", "1")
                put("b", JsonNull)
            }
        assertTrue(block.validateData(obj, path).isEmpty())
    }

    @Test
    fun keyValueValidateDataRejectsNonObject() {
        val block = KeyValueBlock(fields = listOf(KeyValueField("a", "A")))
        val errs = block.validateData(JsonArray(emptyList()), path)
        assertEquals(ValidationCodes.INVALID_TYPE, errs[0].code)
        assertEquals("\$.block", errs[0].path)
    }

    @Test
    fun keyValueValidateDataRejectsKeysNotInFields() {
        val block = KeyValueBlock(fields = listOf(KeyValueField("a", "A")))
        val obj =
            buildJsonObject {
                put("a", "1")
                put("nope", "x")
            }
        val errs = block.validateData(obj, path)
        assertEquals(1, errs.size)
        assertEquals(ValidationCodes.UNKNOWN_FIELD, errs[0].code)
        assertEquals("\$.block.nope", errs[0].path)
    }

    @Test
    fun keyValueValidateDataRejectsNonStringNonNullValues() {
        val block = KeyValueBlock(fields = listOf(KeyValueField("a", "A")))
        val obj = buildJsonObject { put("a", 42) }
        val errs = block.validateData(obj, path)
        assertEquals(1, errs.size)
        assertEquals(ValidationCodes.INVALID_TYPE, errs[0].code)
        assertEquals("\$.block.a", errs[0].path)
    }

    // ----- TableBlock data shape -----

    @Test
    fun tableValidateDataAcceptsArrayOfRowObjects() {
        val block =
            TableBlock(
                columns = listOf(TableColumn("sku", "SKU"), TableColumn("qty", "Qty")),
            )
        val data =
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("sku", "A-100")
                        put("qty", "2")
                    },
                )
                add(
                    buildJsonObject {
                        put("sku", "B-200")
                        put("qty", "1")
                    },
                )
            }
        assertTrue(block.validateData(data, path).isEmpty())
    }

    @Test
    fun tableValidateDataRejectsNonArray() {
        val block = TableBlock(columns = listOf(TableColumn("sku", "SKU")))
        val errs = block.validateData(JsonObject(emptyMap()), path)
        assertEquals(ValidationCodes.INVALID_TYPE, errs[0].code)
        assertEquals("\$.block", errs[0].path)
    }

    @Test
    fun tableValidateDataRejectsNonObjectRow() {
        val block = TableBlock(columns = listOf(TableColumn("sku", "SKU")))
        val data = buildJsonArray { add(JsonPrimitive("nope")) }
        val errs = block.validateData(data, path)
        assertEquals(1, errs.size)
        assertEquals(ValidationCodes.INVALID_TYPE, errs[0].code)
        assertEquals("\$.block[0]", errs[0].path)
    }

    @Test
    fun tableValidateDataRejectsUnknownRowKeys() {
        val block = TableBlock(columns = listOf(TableColumn("sku", "SKU")))
        val data =
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("sku", "A")
                        put("extra", "x")
                    },
                )
            }
        val errs = block.validateData(data, path)
        assertEquals(1, errs.size)
        assertEquals(ValidationCodes.UNKNOWN_FIELD, errs[0].code)
        assertEquals("\$.block[0].extra", errs[0].path)
    }

    // ----- Spacer / Divider reject any data -----

    @Test
    fun spacerValidateDataRejectsAnyValue() {
        val errs = SpacerBlock().validateData(buildJsonObject { put("anything", 1) }, path)
        assertEquals(1, errs.size)
        assertEquals(ValidationCodes.INVALID_VALUE, errs[0].code)
        assertEquals("\$.block", errs[0].path)
    }

    @Test
    fun dividerValidateDataRejectsAnyValue() {
        val errs = DividerBlock().validateData(buildJsonArray { }, path)
        assertEquals(1, errs.size)
        assertEquals(ValidationCodes.INVALID_VALUE, errs[0].code)
    }
}
