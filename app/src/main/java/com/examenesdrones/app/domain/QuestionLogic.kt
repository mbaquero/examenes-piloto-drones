package com.examenesdrones.app.domain

import kotlin.math.max
import kotlin.random.Random

object QuestionSelector {

    /**
     * Picks [count] distinct questions (0 = all) for a practice session. With [prioritizeWeak],
     * questions last answered incorrectly come first, then never-answered ones, then the rest.
     */
    fun selectPractice(
        pool: List<Question>,
        history: Map<String, QuestionHistory>,
        count: Int,
        prioritizeWeak: Boolean,
        shuffle: Boolean,
        random: Random = Random.Default,
    ): List<Question> {
        val distinct = pool.distinctBy { it.key }
        val n = if (count <= 0) distinct.size else minOf(count, distinct.size)
        val ordered = if (prioritizeWeak) {
            val wrong = distinct.filter { history[it.key]?.lastCorrect == false }
            val unseen = distinct.filter { history[it.key] == null }
            val rest = distinct.filter { h -> history[h.key]?.lastCorrect == true }
            wrong.shuffled(random) + unseen.shuffled(random) + rest.shuffled(random)
        } else {
            distinct.shuffled(random)
        }
        val picked = ordered.take(n)
        return if (shuffle) picked.shuffled(random) else picked.sortedWith(sourceOrder)
    }

    /**
     * Builds the question list of an exam. When [ExamConfig.perTopic] is set (official format) and the
     * whole syllabus is selected, each topic contributes its quota; shortfalls are filled from other topics.
     * The exam never contains more questions than the pool, nor duplicates.
     */
    fun selectExam(pool: List<Question>, config: ExamConfig, random: Random = Random.Default): List<Question> {
        val candidates = pool.distinctBy { it.key }
            .filter { config.topicKeys.isEmpty() || it.topicKey in config.topicKeys }
        val n = minOf(max(config.questionCount, 1), candidates.size)
        if (n == 0) return emptyList()

        val pick: (List<Question>, Int) -> List<Question> = { list, k ->
            if (config.randomSelection) list.shuffled(random).take(k) else list.sortedWith(sourceOrder).take(k)
        }
        val selected = LinkedHashSet<Question>()
        val usesDistribution = config.perTopic.isNotEmpty() && config.topicKeys.isEmpty() &&
            config.perTopic.values.sum() == n
        if (usesDistribution) {
            val byTopic = candidates.groupBy { it.topicKey }
            for ((topic, quota) in config.perTopic) selected += pick(byTopic[topic].orEmpty(), quota)
        }
        if (selected.size < n) selected += pick(candidates.filter { it !in selected }, n - selected.size)

        val list = selected.toList()
        return if (config.shuffleQuestions) list.shuffled(random) else list.sortedWith(sourceOrder)
    }

    val sourceOrder: Comparator<Question> = compareBy({ topicSortKey(it.topicKey) }, { it.number })

    fun topicSortKey(key: String): Int = key.removePrefix("T").toIntOrNull() ?: 999
}

object OptionShuffler {
    /**
     * Options that refer to other options by position ("Las dos anteriores", "A y B", "Ninguna de las
     * anteriores"…) would change meaning if reordered, so such questions keep their source order.
     */
    private val positional = Regex(
        """anterior|\b[A-D]\)?\s*y\s*[A-D]\b|ambas|ambos|ninguna de las|todas las (?:anteriores|respuestas)|las dos|los dos|\bTodas\b""",
        RegexOption.IGNORE_CASE,
    )

    fun canShuffle(options: List<String>): Boolean = options.none { positional.containsMatchIn(it) }

    /** Returns the display order: element i is the source index of the option shown in position i. */
    fun order(options: List<String>, shuffle: Boolean, random: Random = Random.Default): List<Int> {
        val identity = options.indices.toList()
        if (!shuffle || !canShuffle(options)) return identity
        return identity.shuffled(random)
    }

    fun correctDisplayIndex(order: List<Int>, correctSourceIndex: Int): Int = order.indexOf(correctSourceIndex)
}

object Scoring {
    /**
     * @param answers pairs of (correct display index, selected display index or null if unanswered).
     */
    fun score(answers: List<Pair<Int, Int?>>, rules: ScoringRules): ScoreResult {
        val total = answers.size
        val correct = answers.count { (c, s) -> s != null && s == c }
        val unanswered = answers.count { it.second == null }
        val incorrect = total - correct - unanswered
        val score = correct * rules.pointsPerCorrect - incorrect * rules.penaltyPerWrong
        val maxScore = total * rules.pointsPerCorrect
        val accuracy = if (total == 0) 0.0 else correct * 100.0 / total
        val scorePercent = if (maxScore <= 0.0) 0.0 else (score.coerceAtLeast(0.0) * 100.0 / maxScore)
        val passed = rules.passPercent?.let { scorePercent + 1e-9 >= it }
        return ScoreResult(total, correct, incorrect, unanswered, score, maxScore, accuracy, scorePercent, passed)
    }

    /** Pass percentage equivalent to "at least [passMark] of [total]" from the official rules. */
    fun passPercentFor(passMark: Int, total: Int): Double = if (total == 0) 0.0 else passMark * 100.0 / total
}
