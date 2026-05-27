package bambamboole.pdf.api.models.template

import bambamboole.pdf.api.services.BundledFonts
import kotlinx.serialization.Serializable

@Serializable
data class TemplateSchemaResponse(
    val kind: String,
    val templateVersion: Int,
    val endpoint: String,
    val templateFields: List<String>,
    val attachmentFields: List<String>,
    val page: PageSchema,
    val typography: TypographySchema,
    val fonts: FontsSchema,
    val blocks: List<BlockSchema>,
)

@Serializable
data class PageSchema(
    val size: PageSizeSchema,
    val locales: String,
    val margins: List<String>,
    val pageNumbers: PageNumbersSchema,
)

@Serializable
data class PageSizeSchema(
    val presets: List<PageFormatSchema>,
    val orientations: List<String>,
    val customFields: List<String>,
    val customUnits: List<String>,
)

@Serializable
data class PageFormatSchema(
    val name: String,
    val widthMm: Double,
    val heightMm: Double,
)

@Serializable
data class PageNumbersSchema(
    val fields: List<String>,
    val positions: List<String>,
)

@Serializable
data class TypographySchema(
    val fields: List<String>,
    val alignments: List<String>,
)

@Serializable
data class FontsSchema(
    val bundledFamilies: List<String>,
    val externalFontFields: List<String>,
)

@Serializable
data class BlockSchema(
    val type: String,
    val fields: List<String>,
    val configFields: List<String>,
    val configEnums: Map<String, List<String>> = emptyMap(),
)

object TemplateSchema {
    fun current(): TemplateSchemaResponse =
        TemplateSchemaResponse(
            kind = "template",
            templateVersion = 1,
            endpoint = "/render/template",
            templateFields = listOf("version", "config", "fonts", "attachments", "rows"),
            attachmentFields = listOf("name", "content", "mimeType", "description", "relationship"),
            page = PageSchema(
                size = PageSizeSchema(
                    presets = PageFormat.entries.map {
                        PageFormatSchema(
                            name = PageFormat.serializer().descriptor.getElementName(it.ordinal),
                            widthMm = it.widthMm,
                            heightMm = it.heightMm,
                        )
                    },
                    orientations = Orientation.entries.map { Orientation.serializer().descriptor.getElementName(it.ordinal) },
                    customFields = listOf("width", "height"),
                    customUnits = listOf("mm"),
                ),
                locales = "ISO 639-1 language code and ISO 3166-1 alpha-2 country code, separated by an underscore e.g. de_DE or en_US",
                margins = listOf("top", "right", "bottom", "left"),
                pageNumbers = PageNumbersSchema(
                    fields = listOf("enabled", "position"),
                    positions = Align.entries.map { it.serializedName() },
                ),
            ),
            typography = TypographySchema(
                fields = listOf("family", "size", "weight", "align", "color"),
                alignments = Align.entries.map { it.serializedName() },
            ),
            fonts = FontsSchema(
                bundledFamilies = BundledFonts.families.sorted(),
                externalFontFields = listOf("src", "weight", "style"),
            ),
            blocks = listOf(
                BlockSchema(
                    type = "text",
                    fields = listOf("id", "text", "config"),
                    configFields = listOf("typography", "spacing", "width", "align"),
                ),
                BlockSchema(
                    type = "html",
                    fields = listOf("id", "html", "config"),
                    configFields = listOf("typography", "spacing", "width", "align"),
                ),
                BlockSchema(
                    type = "spacer",
                    fields = listOf("id", "config"),
                    configFields = listOf("typography", "spacing", "width", "align", "height"),
                ),
                BlockSchema(
                    type = "divider",
                    fields = listOf("id", "config"),
                    configFields = listOf("typography", "spacing", "width", "align", "thickness", "lineColor", "style"),
                    configEnums = mapOf("style" to DividerStyle.entries.map { it.serializedName() }),
                ),
            ),
        )

    private fun Align.serializedName(): String =
        when (this) {
            Align.LEFT -> "left"
            Align.CENTER -> "center"
            Align.RIGHT -> "right"
        }

    private fun DividerStyle.serializedName(): String =
        when (this) {
            DividerStyle.SOLID -> "solid"
            DividerStyle.DASHED -> "dashed"
            DividerStyle.DOTTED -> "dotted"
            DividerStyle.DOUBLE -> "double"
            DividerStyle.NONE -> "none"
        }
}
