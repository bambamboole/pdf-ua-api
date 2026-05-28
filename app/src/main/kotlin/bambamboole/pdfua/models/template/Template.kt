package bambamboole.pdfua.models.template

import bambamboole.pdfua.models.FileAttachment
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Row(val blocks: List<Block>)

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
data class PageConfig(
    val size: PageSize = PresetPageSize(),
    val locale: String = "de_DE",
    val margins: SpacingConfig = SpacingConfig(20, 20, 20, 25),
    val pageNumbers: PageNumbersConfig = PageNumbersConfig(),
    val background: PageBackgroundConfig? = null,
    val footer: PageFooterConfig = PageFooterConfig(),
)

@Serializable
data class TemplateConfig(
    val page: PageConfig = PageConfig(),
    val typography: TypographyConfig = TypographyConfig(),
)

@Serializable
data class Template(
    val version: Int,
    val config: TemplateConfig = TemplateConfig(),
    val fonts: Map<String, FontFace> = emptyMap(),
    val attachments: List<FileAttachment> = emptyList(),
    val rows: List<Row> = emptyList(),
)
