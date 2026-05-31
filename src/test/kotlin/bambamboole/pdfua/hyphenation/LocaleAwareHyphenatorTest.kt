package bambamboole.pdfua.hyphenation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocaleAwareHyphenatorTest {
    private val softHyphen = '­'

    @Test
    fun germanWordGetsSoftHyphensAtSyllableBoundaries() {
        val h = LocaleAwareHyphenator("de-DE")
        val out = h.hyphenateText("Verlegerichtlinie")
        assertTrue(out.contains(softHyphen), "expected at least one soft hyphen, got '$out'")
        // Removing soft hyphens must give back the original word.
        assertEquals("Verlegerichtlinie", out.replace(softHyphen.toString(), ""))
    }

    @Test
    fun englishWordGetsSoftHyphensAtSyllableBoundaries() {
        val h = LocaleAwareHyphenator("en-US")
        val out = h.hyphenateText("typography")
        assertTrue(out.contains(softHyphen), "expected at least one soft hyphen, got '$out'")
        assertEquals("typography", out.replace(softHyphen.toString(), ""))
    }

    @Test
    fun shortWordsAreLeftAlone() {
        val h = LocaleAwareHyphenator("de-DE")
        val out = h.hyphenateText("Ich bin ein Kind")
        assertFalse(out.contains(softHyphen), "no soft hyphens expected for short words, got '$out'")
    }

    @Test
    fun punctuationAndWhitespaceAreRoundTripped() {
        val h = LocaleAwareHyphenator("de-DE")
        val out = h.hyphenateText("Hallo, Welt! (Verlegerichtlinie)")
        // Strip soft hyphens to compare the visible string.
        assertEquals("Hallo, Welt! (Verlegerichtlinie)", out.replace(softHyphen.toString(), ""))
        // The long word inside the parentheses should have at least one break.
        assertTrue(out.contains(softHyphen), "expected soft hyphens in long word")
    }

    @Test
    fun forLangReturnsNullForBlankLang() {
        assertNull(LocaleAwareHyphenator.forLang(null))
        assertNull(LocaleAwareHyphenator.forLang(""))
        assertNull(LocaleAwareHyphenator.forLang("   "))
    }

    @Test
    fun forLangReturnsNullForUnknownDictionary() {
        // 'xx-YY' is not a real locale; the library returns no dictionary.
        assertNull(LocaleAwareHyphenator.forLang("xx-YY"))
    }

    @Test
    fun forLangAcceptsUnderscoreSeparator() {
        val h = LocaleAwareHyphenator.forLang("de_DE")
        assertNotNull(h, "expected German hyphenator for 'de_DE'")
    }

    @Test
    fun emptyInputIsReturnedUnchanged() {
        val h = LocaleAwareHyphenator("de-DE")
        assertEquals("", h.hyphenateText(""))
    }
}
