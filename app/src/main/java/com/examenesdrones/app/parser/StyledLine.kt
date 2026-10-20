package com.examenesdrones.app.parser

/** A single glyph extracted from a PDF page, in top-down page coordinates. */
data class Glyph(
    val page: Int,
    val text: String,
    val x: Float,
    val baseline: Float,
    val width: Float,
    val fontSize: Float,
    val bold: Boolean,
)

/**
 * A visual line of text with per-character bold information.
 * [boldMask] has the same length as [text].
 */
class StyledLine(
    val page: Int,
    val text: String,
    val boldMask: BooleanArray,
    val left: Float,
    val right: Float,
    val pageWidth: Float,
    val fontSize: Float,
    /** Baseline in top-down page coordinates; used to interleave images with text. */
    val baseline: Float = 0f,
) {
    init {
        require(text.length == boldMask.size) { "boldMask must match text length" }
    }

    val isBlank: Boolean get() = text.isBlank()

    /** Number of letters/digits in the line. */
    val alnumCount: Int get() = text.count { it.isLetterOrDigit() }

    /** Number of bold letters/digits in [from, to). */
    fun boldAlnum(from: Int = 0, to: Int = text.length): Int {
        var n = 0
        for (i in from until to) if (boldMask[i] && text[i].isLetterOrDigit()) n++
        return n
    }

    fun boldRatio(): Float = if (alnumCount == 0) 0f else boldAlnum().toFloat() / alnumCount

    /** Whether the line ends close to the right margin, i.e. it was most likely wrapped. */
    val isWrapped: Boolean get() = pageWidth > 0 && right > pageWidth * 0.85f

    /** Horizontally centered/indented headings start well to the right of the body margin. */
    val isIndented: Boolean get() = pageWidth > 0 && left > pageWidth * 0.15f

    fun endsBold(): Boolean {
        for (i in text.indices.reversed()) {
            if (text[i].isLetterOrDigit()) return boldMask[i]
        }
        return false
    }

    /** Returns a trimmed copy, keeping the bold mask aligned. */
    fun trimmed(): StyledLine {
        var s = 0
        var e = text.length
        while (s < e && text[s].isWhitespace()) s++
        while (e > s && text[e - 1].isWhitespace()) e--
        if (s == 0 && e == text.length) return this
        return StyledLine(page, text.substring(s, e), boldMask.copyOfRange(s, e), left, right, pageWidth, fontSize, baseline)
    }

    fun sub(from: Int, to: Int = text.length): StyledText =
        StyledText(text.substring(from, to), boldMask.copyOfRange(from, to))

    fun toStyledText(): StyledText = StyledText(text, boldMask.copyOf())

    override fun toString(): String = "StyledLine(p=$page, '$text')"

    companion object {
        fun plain(text: String, page: Int = 1, pageWidth: Float = 600f, left: Float = 32f, right: Float = 300f, fontSize: Float = 7.3f, baseline: Float = 0f) =
            StyledLine(page, text, BooleanArray(text.length), left, right, pageWidth, fontSize, baseline)

        /**
         * Builds a line from markdown-like markup where `**bold**` marks bold text.
         * Only used by tests and debugging helpers.
         */
        fun markup(
            markup: String, page: Int = 1, pageWidth: Float = 600f, left: Float = 32f, right: Float = 300f, fontSize: Float = 7.3f,
            baseline: Float = 0f,
        ): StyledLine {
            val sb = StringBuilder()
            val mask = ArrayList<Boolean>()
            var bold = false
            var i = 0
            while (i < markup.length) {
                if (markup.startsWith("**", i)) {
                    bold = !bold; i += 2; continue
                }
                sb.append(markup[i]); mask.add(bold); i++
            }
            return StyledLine(page, sb.toString(), mask.toBooleanArray(), left, right, pageWidth, fontSize, baseline)
        }
    }
}

/** Text with bold spans; used to keep highlighted key concepts from the source. */
class StyledText(val text: String, val boldMask: BooleanArray) {
    init {
        require(text.length == boldMask.size)
    }

    fun boldRanges(): List<IntRange> {
        val out = ArrayList<IntRange>()
        var start = -1
        for (i in boldMask.indices) {
            if (boldMask[i] && start < 0) start = i
            if (!boldMask[i] && start >= 0) {
                out += start until i; start = -1
            }
        }
        if (start >= 0) out += start until boldMask.size
        return out
    }

    /** Serialized as "start-end,start-end" (end exclusive). */
    fun encodeBold(): String = boldRanges().joinToString(",") { "${it.first}-${it.last + 1}" }

    operator fun plus(other: StyledText): StyledText =
        StyledText(text + other.text, boldMask + other.boldMask)

    fun joinWith(other: StyledText, separator: String = " "): StyledText {
        if (text.isEmpty()) return other
        if (other.text.isEmpty()) return this
        val needsSep = !text.endsWith(" ") && !text.endsWith("-") && !other.text.startsWith(" ")
        val sep = if (needsSep) separator else ""
        // A separator inherits bold only if both sides are bold.
        val sepBold = BooleanArray(sep.length) { boldMask.lastOrNull() == true && other.boldMask.firstOrNull() == true }
        return StyledText(text + sep + other.text, boldMask + sepBold + other.boldMask)
    }

    companion object {
        fun plain(text: String) = StyledText(text, BooleanArray(text.length))

        fun decodeBold(text: String, encoded: String?): List<IntRange> {
            if (encoded.isNullOrBlank()) return emptyList()
            return encoded.split(',').mapNotNull { part ->
                val (a, b) = part.split('-').map { it.toIntOrNull() ?: return@mapNotNull null }
                val s = a.coerceIn(0, text.length)
                val e = b.coerceIn(s, text.length)
                if (e > s) s until e else null
            }
        }
    }
}
