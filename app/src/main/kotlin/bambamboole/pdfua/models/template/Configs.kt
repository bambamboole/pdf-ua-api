package bambamboole.pdfua.models.template

import kotlinx.serialization.Serializable

@Serializable
data class TypographyConfig(
    val family: String? = null,
    val size: Int? = null,
    val weight: Int? = null,
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
