package bambamboole.pdf.api.services

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object BundledFonts {
    private val baseFontFamilies = setOf("Liberation Sans", "Liberation Serif", "Liberation Mono")

    enum class FontStyle {
        Normal,
        Italic
    }

    data class Font(
        val path: String,
        val family: String,
        val weight: Int,
        val style: FontStyle
    )

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val manifest: FontManifest by lazy {
        val stream = BundledFonts::class.java.getResourceAsStream("/fonts/fonts.json")
            ?: throw IllegalStateException("Bundled font manifest not found: /fonts/fonts.json")
        stream.use {
            json.decodeFromString(FontManifest.serializer(), it.bufferedReader().readText())
        }
    }

    val fonts: List<Font> by lazy {
        manifest.fonts.map { font ->
            Font(
                path = font.path,
                family = font.family,
                weight = font.weight,
                style = when (font.style) {
                    "normal" -> FontStyle.Normal
                    "italic" -> FontStyle.Italic
                    else -> throw IllegalStateException("Unsupported font style '${font.style}' for ${font.path}")
                }
            )
        }
    }

    val families: Set<String> by lazy {
        fonts.mapTo(linkedSetOf()) { it.family }
    }

    val fontBytes: Map<Font, ByteArray> by lazy {
        fonts.associateWith { font ->
            BundledFonts::class.java.getResourceAsStream(font.path)?.use { it.readBytes() }
                ?: throw IllegalStateException("Bundled font not found: ${font.path}")
        }
    }

    fun loadBytes(font: Font): ByteArray =
        fontBytes[font] ?: throw IllegalStateException("Bundled font not registered: ${font.path}")

    fun fontBytesForHtml(html: String): Map<Font, ByteArray> {
        val referencedFamilies = referencedFamilies(html)
        return fontBytes.filterKeys { font ->
            font.family in baseFontFamilies || font.family in referencedFamilies
        }
    }

    private fun referencedFamilies(html: String): Set<String> {
        val normalizedHtml = html.lowercase()
        return families.filterTo(linkedSetOf()) { family ->
            normalizedHtml.contains(family.lowercase())
        }
    }

    @Serializable
    private data class FontManifest(
        val fonts: List<ManifestFont>
    )

    @Serializable
    private data class ManifestFont(
        val path: String,
        val family: String,
        val weight: Int,
        val style: String
    )
}
