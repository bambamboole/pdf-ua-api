package bambamboole.pdf.api.util

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import org.slf4j.LoggerFactory
import java.awt.Font
import java.awt.FontFormatException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.*

/**
 * A tool for listing all the fonts in a directory.
 * Adapted from openhtmltopdf wiki AutoFont example.
 */
object AutoFont {
    private val logger = LoggerFactory.getLogger(AutoFont::class.java)

    /**
     * Returns a list of fonts in a given directory.
     * NOTE: Should not be used repeatedly as each font found is parsed to get the family name.
     *
     * @param directory the directory to search
     * @param validFileExtensions list of file extensions that are fonts
     * @param recurse whether to look in sub-directories recursively
     * @param followLinks whether to follow symbolic links
     * @return a list of fonts
     */
    fun findFontsInDirectory(
        directory: Path,
        validFileExtensions: List<String> = listOf("ttf"),
        recurse: Boolean = true,
        followLinks: Boolean = true
    ): List<CSSFont> {
        val processor = FontFileProcessor(validFileExtensions)

        val maxDepth = if (recurse) Int.MAX_VALUE else 1
        val options = if (followLinks) {
            EnumSet.of(FileVisitOption.FOLLOW_LINKS)
        } else {
            EnumSet.noneOf(FileVisitOption::class.java)
        }

        Files.walkFileTree(directory, options, maxDepth, processor)

        return processor.fontsAdded
    }

    /**
     * Get a string containing added font families (duplicates removed) in a format suitable
     * for the CSS font-family property.
     */
    fun toCSSEscapedFontFamily(fontsList: List<CSSFont>): String {
        return fontsList
            .map { "'${it.familyCssEscaped()}'" }
            .distinct()
            .joinToString(", ")
    }

    /**
     * Adds all fonts in the list to the builder.
     */
    fun toBuilder(builder: PdfRendererBuilder, fonts: List<CSSFont>) {
        for (font in fonts) {
            builder.useFont(font.path.toFile(), font.family, font.weight, font.style, true)
        }
    }

    data class CSSFont(
        val path: Path,
        val family: String,
        /**
         * WARNING: Heuristics are used to determine if a font is bold (700) or normal (400) weight.
         */
        val weight: Int,
        /**
         * WARNING: Heuristics are used to determine if a font is italic or normal style.
         */
        val style: FontStyle
    ) {
        /**
         * WARNING: Basic escaping, may not be robust to attack.
         */
        fun familyCssEscaped(): String {
            return family.replace("'", "\\'")
        }
    }

    private class FontFileProcessor(
        private val validFileExtensions: List<String>
    ) : SimpleFileVisitor<Path>() {
        val fontsAdded = mutableListOf<CSSFont>()

        override fun visitFile(font: Path, attrs: BasicFileAttributes): FileVisitResult {
            if (attrs.isRegularFile && isValidFont(font)) {
                try {
                    val f = Font.createFont(Font.TRUETYPE_FONT, font.toFile())

                    val family = f.family
                    // Short of parsing the font ourselves there doesn't seem to be a way
                    // of getting the font properties, so we use heuristics based on font name.
                    val name = f.getFontName(Locale.US).lowercase(Locale.US)
                    val weight = if (name.contains("bold")) 700 else 400
                    val style = if (name.contains("italic")) FontStyle.ITALIC else FontStyle.NORMAL

                    val cssFont = CSSFont(font, family, weight, style)

                    onValidFont(cssFont)
                    fontsAdded.add(cssFont)
                } catch (ffe: FontFormatException) {
                    onInvalidFont(font, ffe)
                }
            }

            return FileVisitResult.CONTINUE
        }

        private fun onValidFont(font: CSSFont) {
            logger.debug(
                "Adding font: path='{}', name='{}', weight={}, style={}",
                font.path, font.family, font.weight, font.style.name
            )
        }

        private fun onInvalidFont(font: Path, ffe: FontFormatException) {
            logger.warn("Ignoring font file with invalid format: {}", font)
            logger.debug("Font format exception details", ffe)
        }

        private fun isValidFont(font: Path): Boolean {
            return validFileExtensions.any { ext -> font.toString().endsWith(ext) }
        }
    }
}
