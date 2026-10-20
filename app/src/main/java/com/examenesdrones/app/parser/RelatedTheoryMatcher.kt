package com.examenesdrones.app.parser

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Finds the theory blocks of a question's topic that share the most distinctive vocabulary with
 * the question and its correct answer. It only *selects* source text; nothing is generated.
 */
class RelatedTheoryMatcher(topics: List<ParsedTopic>) {

    private class Doc(val index: Int, val terms: Map<String, Int>, val length: Int)

    private val docsByTopic: Map<String, List<Doc>>
    private val idf: Map<String, Double>

    init {
        val all = ArrayList<Doc>()
        docsByTopic = topics.associate { topic ->
            topic.key to topic.blocks.mapIndexedNotNull { i, b ->
                if (b.type == BlockType.IMAGE || b.type == BlockType.HEADING) return@mapIndexedNotNull null
                val stems = stems(b.text.text)
                if (stems.isEmpty()) null else Doc(i, stems.groupingBy { it }.eachCount(), stems.size).also { all += it }
            }
        }
        val df = HashMap<String, Int>()
        all.forEach { d -> d.terms.keys.forEach { df[it] = (df[it] ?: 0) + 1 } }
        val n = all.size.coerceAtLeast(1)
        idf = df.mapValues { (_, f) -> ln(1.0 + n.toDouble() / f) }
    }

    fun related(q: ParsedQuestion, limit: Int = 2): List<Int> {
        val docs = docsByTopic[q.topicKey] ?: return emptyList()
        val answer = q.correctIndex?.let { q.options.getOrNull(it)?.text }
            ?: q.options.joinToString(" ") { it.text }
        val queryTerms = (stems(q.text) + stems(answer) + stems(answer)).groupingBy { it }.eachCount()
        val scored = docs.map { d ->
            var s = 0.0
            for ((term, qf) in queryTerms) {
                val tf = d.terms[term] ?: continue
                s += (idf[term] ?: 0.0) * minOf(qf, 2) * (1 + ln(tf.toDouble()))
            }
            d.index to s / sqrt(d.length.toDouble().coerceAtLeast(8.0))
        }.filter { it.second > 0 }.sortedByDescending { it.second }
        val top = scored.firstOrNull()?.second ?: return emptyList()
        if (top < MIN_SCORE) return emptyList()
        return scored.filter { it.second >= top * 0.6 }.take(limit).map { it.first }.sorted()
    }

    companion object {
        private const val MIN_SCORE = 0.6

        private val STOP = setOf(
            "que", "de", "la", "el", "en", "los", "las", "del", "por", "con", "una", "uno", "para", "como", "mas", "pero",
            "sus", "son", "ser", "sera", "seran", "este", "esta", "estos", "estas", "ese", "esa", "puede", "pueden",
            "cual", "cuales", "cuando", "donde", "quien", "quienes", "sin", "sobre", "entre", "hasta", "desde", "tiene",
            "todo", "todos", "toda", "todas", "otro", "otra", "otros", "otras", "siempre", "nunca", "solo", "caso",
            "casos", "anteriores", "respuestas", "correctas", "correcta", "dos", "tres", "cierto", "falso", "segun",
            "deben", "debe", "debera", "deberan", "dicho", "dicha", "mismo", "misma", "tipo", "forma", "manera",
            "articulo", "reglamento", "uas", "aeronave", "piloto", "distancia", "les", "lo", "al", "se", "su", "un", "no", "si", "ya", "muy",
        )

        fun stems(text: String): List<String> =
            TextNormalizer.words(text).filter { it.length >= 3 && it !in STOP }.map { if (it.length > 6) it.take(6) else it }
    }
}
