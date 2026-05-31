package bambamboole.pdfua.hyphenation

import com.openhtmltopdf.extend.Hyphenator
import io.sevcik.hypherator.HyphenationIterator
import org.slf4j.LoggerFactory
import io.sevcik.hypherator.Hypherator as HypheratorLib

/**
 * Adapts [io.sevcik.hypherator.Hypherator] (Hunspell-style hyphenation patterns) to OpenHTMLToPDF's
 * [Hyphenator] extension point. OHP calls [hyphenateText] for every inline text node whose style
 * carries `hyphens: auto` and substitutes the returned string for the original — soft hyphens
 * (`U+00AD`) become potential line-break points consumed by the line-breaker.
 *
 * One instance binds to one locale (resolved from the document's `<html lang>` at render time).
 * Hypherator dictionaries are static and thread-safe inside the library, so this class is too.
 */
class LocaleAwareHyphenator(
    private val locale: String,
) : Hyphenator {
    private val logger = LoggerFactory.getLogger(LocaleAwareHyphenator::class.java)
    private val dictionaryAvailable: Boolean = HypheratorLib.getInstance(locale) != null

    init {
        if (!dictionaryAvailable) {
            logger.info("No hyphenation dictionary for locale '{}'; hyphens: auto will be inert", locale)
        }
    }

    override fun hyphenateText(text: String): String {
        if (!dictionaryAvailable || text.length < MIN_RUN_LEN) return text
        val iter = HypheratorLib.getInstance(locale) ?: return text

        val sb = StringBuilder(text.length + (text.length shr SOFT_HYPHEN_BUDGET_SHIFT))
        var i = 0
        val n = text.length
        while (i < n) {
            val ch = text[i]
            if (!ch.isLetter()) {
                sb.append(ch)
                i++
                continue
            }
            val end = scanWord(text, i)
            sb.append(hyphenateWord(iter, text, i, end))
            i = end
        }
        return sb.toString()
    }

    private fun scanWord(
        text: String,
        start: Int,
    ): Int {
        var j = start
        while (j < text.length && text[j].isLetter()) j++
        return j
    }

    private fun hyphenateWord(
        iter: HyphenationIterator,
        text: String,
        start: Int,
        end: Int,
    ): String {
        val len = end - start
        if (len < MIN_WORD_LEN) return text.substring(start, end)
        val word = text.substring(start, end)
        iter.setWord(word)
        var bp = iter.first()
        if (bp === HyphenationIterator.DONE) return word

        val positions = ArrayList<Int>(MAX_BREAKS_HINT)
        while (bp !== HyphenationIterator.DONE) {
            positions.add(iter.applyBreak(bp).first.length)
            bp = iter.next()
        }
        if (positions.isEmpty()) return word

        val sb = StringBuilder(word.length + positions.size)
        var last = 0
        for (pos in positions) {
            sb.append(word, last, pos)
            sb.append(SOFT_HYPHEN)
            last = pos
        }
        sb.append(word, last, word.length)
        return sb.toString()
    }

    companion object {
        private const val SOFT_HYPHEN = '­'
        private const val MIN_WORD_LEN = 4
        private const val MIN_RUN_LEN = 4
        private const val MAX_BREAKS_HINT = 4
        private const val SOFT_HYPHEN_BUDGET_SHIFT = 3

        /**
         * Build a hyphenator from an HTML document's `lang` attribute. Returns `null` when no
         * `lang` is set or when no dictionary covers the locale.
         */
        fun forLang(lang: String?): LocaleAwareHyphenator? {
            val normalized = lang?.trim()?.takeIf { it.isNotEmpty() }?.replace('_', '-') ?: return null
            return LocaleAwareHyphenator(normalized).takeIf { it.dictionaryAvailable }
        }
    }
}
