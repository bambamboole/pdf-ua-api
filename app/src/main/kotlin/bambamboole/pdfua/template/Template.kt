package bambamboole.pdfua.template

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
data class TemplateConfig(
    val page: PageConfig = PageConfig(),
    val typography: TypographyConfig = TypographyConfig(),
)

@Serializable
data class Row(val blocks: List<Block>)

@Serializable
data class PageConfig(
    val size: PageSize = PresetPageSize(),
    val locale: String = "de_DE",
    val margins: SpacingConfig = SpacingConfig(20, 20, 20, 25),
    val pageNumbers: PageNumbersConfig = PageNumbersConfig(),
    val background: PageBackgroundConfig? = null,
    val footer: PageFooterConfig = PageFooterConfig(),
)

@Serializable
data class PageNumbersConfig(
    val enabled: Boolean = false,
    val position: Align = Align.CENTER,
)

@Serializable
enum class PageBackgroundType {
    @SerialName("auto") AUTO,
    @SerialName("image") IMAGE,
    @SerialName("pdf") PDF,
}

@Serializable
data class PageBackgroundConfig(
    val src: String,
    val type: PageBackgroundType = PageBackgroundType.AUTO,
)

@Serializable
data class PageFooterConfig(
    val repeat: Boolean = true,
    val rows: List<Row> = emptyList(),
)

@Serializable
data class TypographyConfig(
    val family: String? = null,
    val size: Int? = null,
    val weight: FontWeight? = null,
    val align: Align? = null,
    val color: String? = null,
)

@Serializable
data class SpacingConfig(
    val top: Int? = null,
    val right: Int? = null,
    val bottom: Int? = null,
    val left: Int? = null,
)

interface BlockConfig {
    val typography: TypographyConfig?
    val spacing: SpacingConfig?
    val width: String?
    val align: Align?
}

@Serializable
data class BaseBlockConfig(
    override val typography: TypographyConfig? = null,
    override val spacing: SpacingConfig? = null,
    override val width: String? = null,
    override val align: Align? = null,
) : BlockConfig

@Serializable
enum class Align {
    @SerialName("left") LEFT,
    @SerialName("center") CENTER,
    @SerialName("right") RIGHT,
}
