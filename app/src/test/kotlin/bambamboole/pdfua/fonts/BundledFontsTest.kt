package bambamboole.pdfua.fonts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BundledFontsTest {

    @Test
    fun loadsApprovedFontFamiliesFromManifest() {
        val expectedFamilies = setOf(
            "Liberation Sans",
            "Liberation Serif",
            "Liberation Mono",
            "Roboto",
            "Open Sans",
            "Poppins",
            "Montserrat",
            "Lato",
            "Inter",
            "Noto Sans",
            "Noto Serif",
            "Noto Sans Mono",
            "Oswald",
            "Nunito",
            "Raleway",
            "Rubik",
            "Merriweather",
            "Playfair Display",
            "Fira Code",
            "JetBrains Mono"
        )

        assertEquals(expectedFamilies, BundledFonts.families)
        assertTrue(BundledFonts.fonts.all { it.path.startsWith("/fonts/") })
        assertTrue(BundledFonts.fonts.all { it.path.count { char -> char == '/' } >= 3 })
    }

    @Test
    fun includesRegularWeightForEveryFamily() {
        val regularFamilies = BundledFonts.fonts
            .filter { it.weight == 400 && it.style == BundledFonts.FontStyle.Normal }
            .map { it.family }
            .toSet()

        assertEquals(BundledFonts.families, regularFamilies)
    }

    @Test
    fun manifestEntriesPointToBundledFontFiles() {
        assertTrue(BundledFonts.fonts.all { BundledFonts.loadBytes(it).isNotEmpty() })
    }

    @Test
    fun fontBytesAreCachedCentrally() {
        val font = BundledFonts.fonts.first()

        assertSame(BundledFonts.loadBytes(font), BundledFonts.loadBytes(font))
    }

    @Test
    fun htmlWithoutExplicitBundledFontsUsesBaseFallbackFontsOnly() {
        val fonts = BundledFonts.fontBytesForHtml("<p style=\"font-family: Arial, sans-serif\">Hello</p>").keys
        val families = fonts.map { it.family }.toSet()

        assertEquals(setOf("Liberation Sans", "Liberation Serif", "Liberation Mono"), families)
        assertTrue(fonts.size < BundledFonts.fonts.size)
    }

    @Test
    fun htmlWithExplicitBundledFontIncludesThatFamily() {
        val families = BundledFonts.fontBytesForHtml("<p style=\"font-family: Roboto, sans-serif\">Hello</p>")
            .keys
            .map { it.family }
            .toSet()

        assertTrue("Roboto" in families)
        assertTrue("Liberation Sans" in families)
    }

    @Test
    fun eachFontFamilyIncludesLicenseAndSourceMetadata() {
        val familyFolders = BundledFonts.fonts
            .map { it.path.removePrefix("/fonts/").substringBefore("/") }
            .toSet()

        for (folder in familyFolders) {
            val license = BundledFonts::class.java.getResourceAsStream("/fonts/$folder/LICENSE.txt")
            val source = BundledFonts::class.java.getResourceAsStream("/fonts/$folder/SOURCE.md")
            assertTrue(license != null, "Missing LICENSE.txt for $folder")
            assertTrue(source != null, "Missing SOURCE.md for $folder")
        }
    }
}
