package bambamboole.pdfua.template

import bambamboole.pdfua.fonts.FontFace
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TemplateValidationTest {
    @Test
    fun validTemplateAndDataReturnNoIssues() {
        val template =
            Template(
                version = 1,
                rows = listOf(Row(listOf(TextBlock(id = "intro", text = "Hello")))),
            )
        val data =
            mapOf<String, JsonElement>(
                "intro" to buildJsonObject { put("text", "Hi") },
            )

        assertTrue(template.validate(data).isEmpty())
    }

    @Test
    fun unsupportedVersionFlagged() {
        val template = Template(version = 2)
        val errs = template.validate(emptyMap())
        assertEquals(1, errs.size)
        assertEquals(ValidationCodes.UNSUPPORTED_VERSION, errs[0].code)
        assertEquals("\$.template.version", errs[0].path)
    }

    @Test
    fun headingLevelOutOfRangeFlaggedAtBlockPath() {
        val template =
            Template(
                version = 1,
                rows =
                    listOf(
                        Row(
                            listOf(
                                TextBlock(text = "x"),
                                HeadingBlock(text = "y", config = HeadingConfig(level = 9)),
                            ),
                        ),
                    ),
            )
        val errs = template.validate(emptyMap())
        assertEquals(1, errs.size)
        assertEquals(ValidationCodes.OUT_OF_RANGE, errs[0].code)
        assertEquals("\$.template.rows[0].blocks[1].config.level", errs[0].path)
    }

    @Test
    fun duplicateBlockIdAcrossBodyAndFooterFlagged() {
        val template =
            Template(
                version = 1,
                rows = listOf(Row(listOf(TextBlock(id = "shared", text = "a")))),
                config =
                    TemplateConfig(
                        page =
                            PageConfig(
                                footer =
                                    PageFooterConfig(
                                        rows = listOf(Row(listOf(TextBlock(id = "shared", text = "b")))),
                                    ),
                            ),
                    ),
            )

        val errs = template.validate(emptyMap()).filter { it.code == ValidationCodes.DUPLICATE_BLOCK_ID }

        assertEquals(1, errs.size)
        assertEquals(
            "\$.template.config.page.footer.rows[0].blocks[0].id",
            errs[0].path,
        )
    }

    @Test
    fun blocksWithNullIdsDoNotConflict() {
        val template =
            Template(
                version = 1,
                rows =
                    listOf(
                        Row(listOf(TextBlock(text = "a"))),
                        Row(listOf(TextBlock(text = "b"))),
                    ),
            )

        assertTrue(template.validate(emptyMap()).isEmpty())
    }

    @Test
    fun orphanDataIdsFlagged() {
        val template =
            Template(
                version = 1,
                rows = listOf(Row(listOf(TextBlock(id = "intro", text = "x")))),
            )
        val data =
            mapOf<String, JsonElement>(
                "intro" to buildJsonObject { put("text", "ok") },
                "nope" to buildJsonObject { put("text", "x") },
            )

        val errs = template.validate(data)

        assertEquals(1, errs.size)
        assertEquals(ValidationCodes.ORPHAN_DATA_ID, errs[0].code)
        assertEquals("\$.data.nope", errs[0].path)
    }

    @Test
    fun blockValidateDataIsCalledAtDataPath() {
        val template =
            Template(
                version = 1,
                rows =
                    listOf(
                        Row(
                            listOf(
                                TableBlock(
                                    id = "items",
                                    config = TableConfig(columns = listOf(TableColumn("sku", "SKU"))),
                                ),
                            ),
                        ),
                    ),
            )
        val data =
            mapOf<String, JsonElement>(
                "items" to JsonObject(emptyMap()),
            )

        val errs = template.validate(data)

        assertEquals(1, errs.size)
        assertEquals(ValidationCodes.INVALID_TYPE, errs[0].code)
        assertEquals("\$.data.items", errs[0].path)
    }

    @Test
    fun customPageSizeWithZeroDimensionFlagged() {
        val template =
            Template(
                version = 1,
                config = TemplateConfig(page = PageConfig(size = CustomPageSize(0, 297))),
            )

        val errs = template.validate(emptyMap()).filter { it.code == ValidationCodes.OUT_OF_RANGE }

        assertEquals(1, errs.size)
        assertEquals("\$.template.config.page.size", errs[0].path)
    }

    @Test
    fun pageBackgroundBlankSrcFlagged() {
        val template =
            Template(
                version = 1,
                config = TemplateConfig(page = PageConfig(background = PageBackgroundConfig(src = ""))),
            )

        val errs = template.validate(emptyMap()).filter { it.code == ValidationCodes.INVALID_URI }

        assertEquals(1, errs.size)
        assertEquals("\$.template.config.page.background.src", errs[0].path)
    }

    @Test
    fun pageBackgroundUnsupportedSchemeFlagged() {
        val template =
            Template(
                version = 1,
                config = TemplateConfig(page = PageConfig(background = PageBackgroundConfig(src = "ftp://x"))),
            )

        val errs = template.validate(emptyMap()).filter { it.code == ValidationCodes.INVALID_URI }

        assertEquals(1, errs.size)
        assertEquals("\$.template.config.page.background.src", errs[0].path)
    }

    @Test
    fun fontsWithValidWeightsValidate() {
        val template =
            Template(
                version = 1,
                fonts =
                    mapOf(
                        "Lobster" to FontFace(src = "https://cdn.example.com/lobster.ttf", weight = "400"),
                        "Heavy" to FontFace(src = "https://cdn.example.com/heavy.ttf", weight = "400 700"),
                    ),
            )
        assertTrue(template.validate(emptyMap()).isEmpty())
    }

    @Test
    fun fontWithUnknownWeightTokenIsFlagged() {
        val template =
            Template(
                version = 1,
                fonts =
                    mapOf(
                        "Lobster" to FontFace(src = "https://cdn.example.com/lobster.ttf", weight = "400 999"),
                    ),
            )

        val errs = template.validate(emptyMap()).filter { it.code == ValidationCodes.INVALID_VALUE }

        assertEquals(1, errs.size)
        assertEquals("\$.template.fonts.Lobster.weight", errs[0].path)
    }

    @Test
    fun fontWithBlankWeightIsFlagged() {
        val template =
            Template(
                version = 1,
                fonts = mapOf("X" to FontFace(src = "https://cdn.example.com/x.ttf", weight = "")),
            )

        val errs = template.validate(emptyMap()).filter { it.code == ValidationCodes.INVALID_VALUE }

        assertEquals(1, errs.size)
        assertEquals("\$.template.fonts.X.weight", errs[0].path)
    }

    @Test
    fun multipleIndependentIssuesAreCollected() {
        val template =
            Template(
                version = 1,
                rows =
                    listOf(
                        Row(
                            listOf(
                                HeadingBlock(id = "h", text = "x", config = HeadingConfig(level = 0)),
                                TableBlock(
                                    id = "t",
                                    config = TableConfig(columns = listOf(TableColumn("1bad", "X"))),
                                ),
                            ),
                        ),
                    ),
            )
        val data =
            mapOf<String, JsonElement>(
                "orphan" to JsonPrimitive("x"),
            )

        val codes = template.validate(data).map { it.code }.toSet()

        assertTrue(ValidationCodes.OUT_OF_RANGE in codes)
        assertTrue(ValidationCodes.INVALID_KEY in codes)
        assertTrue(ValidationCodes.ORPHAN_DATA_ID in codes)
    }
}
