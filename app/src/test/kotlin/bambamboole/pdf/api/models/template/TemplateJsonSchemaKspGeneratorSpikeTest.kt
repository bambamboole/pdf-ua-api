package bambamboole.pdf.api.models.template

import kotlinx.schema.Schema
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TemplateJsonSchemaKspGeneratorSpikeTest {

    @Test
    fun generatesSchemaForTemplateLikeModelWithSealedInterfacePolymorphism() {
        val schema = Json.parseToJsonElement(SpikeKspTemplate::class.jsonSchemaString).jsonObject

        assertEquals("https://json-schema.org/draft/2020-12/schema", schema["\$schema"]?.jsonPrimitive?.content)
        assertEquals("object", schema["type"]?.jsonPrimitive?.content)

        val definitions = schema["\$defs"]!!.jsonObject
        val row = definitions.getValue("bambamboole.pdf.api.models.template.SpikeKspRow").jsonObject
        val blockProperty = row["properties"]!!
            .jsonObject.getValue("blocks")
            .jsonObject.getValue("items")
            .jsonObject
        assertEquals("#/\$defs/bambamboole.pdf.api.models.template.SpikeKspBlock", blockProperty["\$ref"]!!.jsonPrimitive.content)

        val block = definitions.getValue("bambamboole.pdf.api.models.template.SpikeKspBlock").jsonObject
        assertEquals(
            listOf(
                "#/\$defs/heading",
                "#/\$defs/text",
            ),
            block["oneOf"]!!.jsonArray.map { it.jsonObject["\$ref"]!!.jsonPrimitive.content },
        )

        val heading = definitions.getValue("heading").jsonObject
        assertEquals("heading", heading["properties"]!!.jsonObject.getValue("type").jsonObject["const"]!!.jsonPrimitive.content)
        assertEquals(listOf("type", "text"), heading["required"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun addsDiscriminatorsToPageSizeSealedInterface() {
        val schema = Json.parseToJsonElement(SpikeKspTemplate::class.jsonSchemaString).jsonObject
        val definitions = schema["\$defs"]!!.jsonObject

        val pageConfig = definitions.getValue("bambamboole.pdf.api.models.template.SpikeKspPageConfig").jsonObject
        val size = pageConfig["properties"]!!.jsonObject.getValue("size").jsonObject
        assertEquals("#/\$defs/bambamboole.pdf.api.models.template.SpikeKspPageSize", size["\$ref"]!!.jsonPrimitive.content)

        val pageSize = definitions.getValue("bambamboole.pdf.api.models.template.SpikeKspPageSize").jsonObject
        assertNotNull(pageSize["oneOf"], "PageSize should be represented as oneOf")

        val pageSizeRefs = pageSize["oneOf"]!!.jsonArray.map { it.jsonObject["\$ref"]!!.jsonPrimitive.content }
        assertEquals(
            listOf(
                "#/\$defs/bambamboole.pdf.api.models.template.SpikeKspCustomPageSize",
                "#/\$defs/bambamboole.pdf.api.models.template.SpikeKspPresetPageSize",
            ),
            pageSizeRefs,
        )

        val customPageSize = definitions.getValue("bambamboole.pdf.api.models.template.SpikeKspCustomPageSize").jsonObject
        assertEquals(
            "bambamboole.pdf.api.models.template.SpikeKspCustomPageSize",
            customPageSize["properties"]!!.jsonObject.getValue("type").jsonObject["const"]!!.jsonPrimitive.content,
        )
        assertEquals(
            listOf("type", "width", "height"),
            customPageSize["required"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun marksDefaultedFieldsAsRequired() {
        val schema = Json.parseToJsonElement(SpikeKspTemplate::class.jsonSchemaString).jsonObject
        val definitions = schema["\$defs"]!!.jsonObject

        assertEquals(listOf("version", "config", "rows"), schema["required"]!!.jsonArray.map { it.jsonPrimitive.content })

        val pageConfig = definitions.getValue("bambamboole.pdf.api.models.template.SpikeKspPageConfig").jsonObject
        assertEquals(
            listOf("size", "background"),
            pageConfig["required"]!!.jsonArray.map { it.jsonPrimitive.content },
        )

        val backgroundConfig =
            definitions.getValue("bambamboole.pdf.api.models.template.SpikeKspPageBackgroundConfig").jsonObject
        assertEquals(
            listOf("src", "type"),
            backgroundConfig["required"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun generatesNullableBackgroundAsUnionTypeButWithoutCustomExtensions() {
        val schema = Json.parseToJsonElement(SpikeKspTemplate::class.jsonSchemaString).jsonObject
        val definitions = schema["\$defs"]!!.jsonObject

        val pageConfig = definitions.getValue("bambamboole.pdf.api.models.template.SpikeKspPageConfig").jsonObject
        val background = pageConfig["properties"]!!.jsonObject.getValue("background").jsonObject

        assertEquals("null", background["oneOf"]!!.jsonArray[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(
            "#/\$defs/bambamboole.pdf.api.models.template.SpikeKspPageBackgroundConfig",
            background["oneOf"]!!.jsonArray[1].jsonObject["\$ref"]!!.jsonPrimitive.content,
        )
        assertEquals(null, background["tsType"])
        assertEquals(null, schema["x-pdfUa"])
    }
}

@Serializable
@Schema
data class SpikeKspTemplate(
    val version: Int,
    val config: SpikeKspTemplateConfig = SpikeKspTemplateConfig(),
    val rows: List<SpikeKspRow> = emptyList(),
)

@Serializable
@Schema
data class SpikeKspRow(val blocks: List<SpikeKspBlock>)

@Serializable
@Schema
data class SpikeKspTemplateConfig(val page: SpikeKspPageConfig = SpikeKspPageConfig())

@Serializable
@Schema
data class SpikeKspPageConfig(
    val size: SpikeKspPageSize = SpikeKspPresetPageSize(),
    val background: SpikeKspPageBackgroundConfig? = null,
)

@Serializable
@Schema
data class SpikeKspPageBackgroundConfig(
    val src: String,
    val type: SpikeKspPageBackgroundType = SpikeKspPageBackgroundType.AUTO,
)

@Serializable
@Schema
enum class SpikeKspPageBackgroundType {
    @SerialName("auto") AUTO,
    @SerialName("image") IMAGE,
    @SerialName("pdf") PDF,
}

@Serializable
@Schema
sealed interface SpikeKspPageSize

@Serializable
@Schema
data class SpikeKspPresetPageSize(
    val format: String = "A4",
    val orientation: String = "portrait",
) : SpikeKspPageSize

@Serializable
@Schema
data class SpikeKspCustomPageSize(
    val width: Int,
    val height: Int,
) : SpikeKspPageSize

@Serializable
@Schema
sealed interface SpikeKspBlock

@Serializable
@SerialName("text")
@Schema
data class SpikeKspTextBlock(
    val text: String,
) : SpikeKspBlock

@Serializable
@SerialName("heading")
@Schema
data class SpikeKspHeadingBlock(
    val text: String,
) : SpikeKspBlock
