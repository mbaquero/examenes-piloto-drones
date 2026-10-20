package com.examenesdrones.app.parser

object FontStyle {
    private val boldName = Regex("""(bold|black|heavy|semibold|demibold|extrabold|ultrabold|[_-]?[6-9]00(wght)?\b|w[6-9]00)""", RegexOption.IGNORE_CASE)

    /** Decides whether a PDF font renders bold text, from its name and descriptor data. */
    fun isBold(fontName: String?, descriptorWeight: Float?, forceBold: Boolean): Boolean {
        if (forceBold) return true
        if (descriptorWeight != null && descriptorWeight >= 600f) return true
        val name = fontName?.substringAfter('+') ?: return false
        return boldName.containsMatchIn(name)
    }
}
