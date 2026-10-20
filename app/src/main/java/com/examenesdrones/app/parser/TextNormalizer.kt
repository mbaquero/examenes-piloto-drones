package com.examenesdrones.app.parser

import java.security.MessageDigest
import java.text.Normalizer

object TextNormalizer {
    private val ligatures = mapOf(
        "ﬁ" to "fi", "ﬂ" to "fl", "ﬀ" to "ff", "ﬃ" to "ffi", "ﬄ" to "ffl", "ﬅ" to "st", "ﬆ" to "st",
    )

    /** Private-use code points of Symbol/Wingdings fonts that the slides use as list bullets. */
    private val symbolBullets = setOf('', '', '', '', '', '')

    fun normalizeGlyph(s: String): String {
        var out = s
        for ((k, v) in ligatures) if (out.contains(k)) out = out.replace(k, v)
        if (out.any { it in ''..'' }) {
            // Other private-use glyphs are decorative icons without a text meaning.
            out = out.map { if (it in symbolBullets) "•" else if (it in ''..'') "" else it.toString() }.joinToString("")
        }
        return out
    }

    /** Lowercase, accent-free, alphanumeric-only form used for comparisons and stable keys. */
    fun comparable(s: String): String {
        val decomposed = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        val sb = StringBuilder(decomposed.length)
        for (c in decomposed) {
            if (Character.getType(c) == Character.NON_SPACING_MARK.toInt()) continue
            if (c.isLetterOrDigit()) sb.append(c)
        }
        return sb.toString()
    }

    /**
     * Finds [needle] in [haystack] ignoring case, accents, spacing and punctuation, and returns the
     * matching span of [haystack] exactly as written there (or null if it does not occur).
     */
    fun findLiteral(haystack: String, needle: String): String? {
        val target = comparable(needle)
        if (target.isEmpty()) return null
        val folded = StringBuilder(haystack.length)
        val origin = ArrayList<Int>(haystack.length)
        haystack.forEachIndexed { i, ch ->
            val c = comparable(ch.toString())
            for (k in c) { folded.append(k); origin += i }
        }
        val at = folded.indexOf(target)
        if (at < 0) return null
        var start = origin[at]
        var end = origin[at + target.length - 1] + 1
        // Punctuation that opens or closes the cited text ("(…)", "«…»", final period) is kept when the source has it.
        val leading = needle.trim().takeWhile { !it.isLetterOrDigit() }.filterNot { it.isWhitespace() }
        for (c in leading.reversed()) {
            var i = start - 1
            while (i >= 0 && haystack[i] == ' ') i--
            if (i >= 0 && haystack[i] == c) start = i else break
        }
        val trailing = needle.trim().takeLastWhile { !it.isLetterOrDigit() }.filterNot { it.isWhitespace() }
        for (c in trailing) {
            var i = end
            while (i < haystack.length && haystack[i] == ' ') i++
            if (i < haystack.length && haystack[i] == c) end = i + 1 else break
        }
        return haystack.substring(start, end)
    }

    /** Accent-free lowercase words, used for search and related-theory matching. */
    fun words(s: String): List<String> {
        val decomposed = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        val sb = StringBuilder(decomposed.length)
        for (c in decomposed) {
            if (Character.getType(c) == Character.NON_SPACING_MARK.toInt()) continue
            sb.append(if (c.isLetterOrDigit()) c else ' ')
        }
        return sb.split(' ').filter { it.isNotEmpty() }
    }

    fun foldForSearch(s: String): String = words(s).joinToString(" ")

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun sha256(s: String): String = sha256(s.toByteArray())
}
