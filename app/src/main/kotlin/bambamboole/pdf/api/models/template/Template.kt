package bambamboole.pdf.api.models.template

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
data class PageConfig(
    val format: PageFormat = PageFormat.A4,
    val locale: String = "de_DE",
    val margins: SpacingConfig = SpacingConfig(20, 20, 20, 25),
    val pageNumbers: PageNumbersConfig = PageNumbersConfig(),
    val background: PageBackgroundConfig? = null,
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
    val rows: List<Row> = emptyList(),
)
