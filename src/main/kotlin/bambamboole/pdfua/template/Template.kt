package bambamboole.pdfua.template

import bambamboole.pdfua.css.CSS_LENGTH_PATTERN
import bambamboole.pdfua.fonts.FontFace
import bambamboole.pdfua.fonts.FontWeight
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Template(
    val version: Int,
    val config: TemplateConfig = TemplateConfig(),
    val fonts: Map<String, FontFace> = emptyMap(),
    val attachments: List<FileAttachment> = emptyList(),
    val rows: List<Row> = emptyList(),
)

@Serializable
@SchemaTsType("{ title?: string; page?: PageConfig; typography?: TypographyConfig; embedColorProfile?: boolean }")
data class TemplateConfig(
    @SchemaDescription("Document title. Used as the PDF document title, which PDF/UA requires.")
    @SchemaStringDefault("Document")
    val title: String = "Document",
    val page: PageConfig = PageConfig(),
    val typography: TypographyConfig = TypographyConfig(),
    @SchemaDescription("Embed the PDF/A ICC color profile. Disable only for PDF/UA-without-PDF/A output.")
    @SchemaBoolDefault(true)
    val embedColorProfile: Boolean = true,
)

@Serializable
data class Row(
    val blocks: List<Block>,
)

@Serializable
@SchemaTsType(
    "{ size?: PageSize; locale?: string; margins?: SpacingConfig; pageNumbers?: PageNumbersConfig; " +
        "background?: PageBackgroundConfig | null; footer?: PageFooterConfig }",
)
data class PageConfig(
    val size: PageSize = PresetPageSize(),
    @SchemaStringDefault("de_DE") val locale: String = "de_DE",
    val margins: SpacingConfig = SpacingConfig(SIDE_MARGIN_MM, SIDE_MARGIN_MM, SIDE_MARGIN_MM, BOTTOM_MARGIN_MM),
    val pageNumbers: PageNumbersConfig = PageNumbersConfig(),
    val background: PageBackgroundConfig? = null,
    val footer: PageFooterConfig = PageFooterConfig(),
) {
    companion object {
        const val SIDE_MARGIN_MM = 20
        const val BOTTOM_MARGIN_MM = 25
    }
}

@Serializable
data class PageNumbersConfig(
    @SchemaBoolDefault(false) val enabled: Boolean = false,
    @SchemaEnumDefault("center") val position: Align = Align.CENTER,
)

@Serializable
enum class PageBackgroundType {
    @SerialName("auto")
    AUTO,

    @SerialName("image")
    IMAGE,

    @SerialName("pdf")
    PDF,
}

@Serializable
data class PageBackgroundConfig(
    @SchemaDescription("HTTP, HTTPS, or base64 data URI for an image or PDF page background.")
    @SchemaMinLength(1)
    val src: String,
    @SchemaEnumDefault("auto") val type: PageBackgroundType = PageBackgroundType.AUTO,
)

@Serializable
@SchemaTsType("{ repeat?: boolean; rows?: Row[] }")
data class PageFooterConfig(
    @SchemaBoolDefault(true) val repeat: Boolean = true,
    val rows: List<Row> = emptyList(),
)

@Serializable
data class TypographyConfig(
    @SchemaDescription("Bundled or external font family key.")
    val family: String? = null,
    @SchemaDescription("Font size in points.") @SchemaMin(1)
    val size: Int? = null,
    @SchemaDescription("Font weight; one of the FontWeight enum values.")
    val weight: FontWeight? = null,
    @SchemaDescription("Text alignment for this typography scope.")
    val align: Align? = null,
    @SchemaDescription("CSS color value used for text.")
    val color: String? = null,
)

@Serializable
data class SpacingConfig(
    @SchemaDescription("Top spacing in millimetres.") @SchemaMin(0)
    val top: Int? = null,
    @SchemaDescription("Right spacing in millimetres.") @SchemaMin(0)
    val right: Int? = null,
    @SchemaDescription("Bottom spacing in millimetres.") @SchemaMin(0)
    val bottom: Int? = null,
    @SchemaDescription("Left spacing in millimetres.") @SchemaMin(0)
    val left: Int? = null,
)

@Serializable
@SerialName("BlockConfig")
@SchemaTsType("{ typography?: TypographyConfig; spacing?: SpacingConfig; width?: string | null; align?: Align | null }")
data class BaseBlockConfig(
    @SchemaGroup(SchemaGroups.STYLE)
    val typography: TypographyConfig? = null,
    @SchemaGroup(SchemaGroups.LAYOUT)
    val spacing: SpacingConfig? = null,
    @SchemaDescription("CSS width for this block, such as 50%, 80mm, or auto.")
    @SchemaPattern(CSS_LENGTH_PATTERN)
    @SchemaGroup(SchemaGroups.LAYOUT)
    val width: String? = null,
    @SchemaDescription("Horizontal placement of this block within its row cell.")
    @SchemaGroup(SchemaGroups.LAYOUT)
    val align: Align? = null,
)

@Serializable
enum class Align {
    @SerialName("left")
    LEFT,

    @SerialName("center")
    CENTER,

    @SerialName("right")
    RIGHT,
}
