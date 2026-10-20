package com.examenesdrones.app.parser

/**
 * Groups glyphs into visual lines. Glyphs whose baselines are within a fraction of the font
 * size belong to the same line (bold runs are often rendered on a slightly shifted baseline).
 * A line is split where there is a wide horizontal gap: slides place text boxes side by side
 * (two columns, table cells), and each box must stay a separate fragment.
 */
object LineAssembler {

    /** Horizontal gap, in multiples of the font size, that separates two text boxes. */
    private const val COLUMN_GAP = 2.0f
    private val BULLETS = setOf("•", "·", "▪", "◦", "●", "-", "–", "o")

    fun assemble(glyphs: List<Glyph>, pageWidths: Map<Int, Float>): List<StyledLine> {
        val result = ArrayList<StyledLine>()
        glyphs.groupBy { it.page }.toSortedMap().forEach { (page, pageGlyphs) ->
            val sorted = pageGlyphs.sortedBy { it.baseline }
            val groups = ArrayList<MutableList<Glyph>>()
            var groupBaseline = Float.NaN
            for (g in sorted) {
                val tolerance = (g.fontSize * 0.45f).coerceAtLeast(1f)
                if (groups.isNotEmpty() && kotlin.math.abs(g.baseline - groupBaseline) <= tolerance) {
                    groups.last().add(g)
                } else {
                    groups.add(mutableListOf(g))
                    groupBaseline = g.baseline
                }
            }
            val width = pageWidths[page] ?: 0f
            for (group in groups) {
                for (segment in splitAtGaps(group.sortedBy { it.x })) result += buildLine(page, segment, width)
            }
        }
        return result
    }

    private fun splitAtGaps(glyphs: List<Glyph>): List<List<Glyph>> {
        val out = ArrayList<MutableList<Glyph>>()
        var prev: Glyph? = null
        for (g in glyphs) {
            val p = prev
            val gap = if (p == null) 0f else g.x - (p.x + p.width)
            // A list bullet is followed by a tab-like gap but belongs to its text.
            val afterBullet = p != null && TextNormalizer.normalizeGlyph(p.text).trim() in BULLETS
            if (p == null || (!afterBullet && gap > maxOf(g.fontSize, p.fontSize) * COLUMN_GAP && p.text.isNotBlank() && g.text.isNotBlank())) {
                out.add(mutableListOf(g))
            } else {
                out.last().add(g)
            }
            if (g.text.isNotBlank()) prev = g
        }
        return out
    }

    private fun buildLine(page: Int, glyphs: List<Glyph>, pageWidth: Float): StyledLine {
        val sb = StringBuilder()
        val mask = ArrayList<Boolean>()
        var prev: Glyph? = null
        fun append(s: String, bold: Boolean) {
            for (ch in s) {
                val c = if (ch == ' ' || ch == '\t') ' ' else ch
                if (c == ' ' && (sb.isEmpty() || sb.last() == ' ')) continue
                sb.append(c); mask.add(bold)
            }
        }
        for (g in glyphs) {
            val p = prev
            if (p != null) {
                val gap = g.x - (p.x + p.width)
                if (gap > g.fontSize * 0.2f) append(" ", p.bold && g.bold)
            }
            append(TextNormalizer.normalizeGlyph(g.text), g.bold)
            prev = g
        }
        val left = glyphs.firstOrNull()?.x ?: 0f
        val right = glyphs.maxOfOrNull { it.x + it.width } ?: 0f
        val size = glyphs.groupingBy { it.fontSize }.eachCount().maxByOrNull { it.value }?.key ?: 0f
        val baseline = glyphs.minOfOrNull { it.baseline } ?: 0f
        return StyledLine(page, sb.toString(), mask.toBooleanArray(), left, right, pageWidth, size, baseline)
    }
}
