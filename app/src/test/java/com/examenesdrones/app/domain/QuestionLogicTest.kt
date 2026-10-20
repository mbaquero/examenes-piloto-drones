package com.examenesdrones.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuestionLogicTest {

    private fun q(topic: Int, n: Int, options: List<String> = listOf("Uno", "Dos", "Tres"), correct: Int = 1) =
        Question("T$topic-$n", "T$topic", n, "Pregunta $n del tema $topic", options, correct, null, emptyList(), 1)

    private val pool = (1..6).flatMap { t -> (1..10).map { n -> q(t, n) } }

    @Test
    fun practiceSelectionHasNoDuplicatesAndRespectsCount() {
        repeat(50) { seed ->
            val picked = QuestionSelector.selectPractice(pool + pool.take(5), emptyMap(), 25, prioritizeWeak = false, shuffle = true, random = Random(seed))
            assertEquals(25, picked.size)
            assertEquals(25, picked.map { it.key }.toSet().size)
        }
    }

    @Test
    fun practiceCountZeroMeansAllAndNeverExceedsPool() {
        assertEquals(60, QuestionSelector.selectPractice(pool, emptyMap(), 0, false, true).size)
        assertEquals(60, QuestionSelector.selectPractice(pool, emptyMap(), 500, false, true).size)
    }

    @Test
    fun prioritizesPreviouslyWrongThenUnanswered() {
        val history = mapOf(
            "T1-1" to QuestionHistory(2, 0, lastCorrect = false),
            "T1-2" to QuestionHistory(1, 0, lastCorrect = false),
        ) + pool.drop(2).take(50).associate { it.key to QuestionHistory(1, 1, lastCorrect = true) }
        val picked = QuestionSelector.selectPractice(pool, history, 10, prioritizeWeak = true, shuffle = false, random = Random(1))
        val keys = picked.map { it.key }.toSet()
        assertTrue(keys.containsAll(listOf("T1-1", "T1-2")))
        // The 8 never-answered questions fill the rest before any mastered question.
        assertTrue(keys.containsAll(pool.drop(52).map { it.key }))
    }

    @Test
    fun examUsesOfficialDistribution() {
        val config = ExamConfig(questionCount = 20, perTopic = mapOf("T1" to 4, "T2" to 3, "T3" to 3, "T4" to 4, "T5" to 3, "T6" to 3))
        repeat(30) { seed ->
            val exam = QuestionSelector.selectExam(pool, config, Random(seed))
            assertEquals(20, exam.size)
            assertEquals(20, exam.map { it.key }.toSet().size)
            assertEquals(config.perTopic, exam.groupingBy { it.topicKey }.eachCount().toSortedMap().toMap())
        }
    }

    @Test
    fun examFillsShortfallFromOtherTopicsAndCapsAtPool() {
        val small = pool.filter { it.topicKey != "T1" || it.number <= 2 }
        val config = ExamConfig(questionCount = 20, perTopic = mapOf("T1" to 4, "T2" to 3, "T3" to 3, "T4" to 4, "T5" to 3, "T6" to 3))
        val exam = QuestionSelector.selectExam(small, config, Random(3))
        assertEquals(20, exam.size)
        assertEquals(2, exam.count { it.topicKey == "T1" })
        val tooMany = QuestionSelector.selectExam(pool.take(7), ExamConfig(questionCount = 40), Random(3))
        assertEquals(7, tooMany.size)
    }

    @Test
    fun examRestrictedToSelectedTopics() {
        val exam = QuestionSelector.selectExam(pool, ExamConfig(questionCount = 15, topicKeys = listOf("T2", "T5")), Random(9))
        assertEquals(15, exam.size)
        assertTrue(exam.all { it.topicKey == "T2" || it.topicKey == "T5" })
    }

    @Test
    fun nonRandomExamTakesSourceOrder() {
        val exam = QuestionSelector.selectExam(pool.shuffled(Random(4)), ExamConfig(questionCount = 3, randomSelection = false, shuffleQuestions = false))
        assertEquals(listOf("T1-1", "T1-2", "T1-3"), exam.map { it.key })
    }

    @Test
    fun optionShufflingPreservesCorrectAnswer() {
        val options = listOf("Roja", "Verde", "Azul", "Negra")
        repeat(100) { seed ->
            val order = OptionShuffler.order(options, shuffle = true, random = Random(seed))
            assertEquals(options.indices.toSet(), order.toSet())
            val display = OptionShuffler.correctDisplayIndex(order, 2)
            assertEquals("Azul", options[order[display]])
        }
    }

    @Test
    fun optionsReferringToOtherOptionsAreNeverShuffled() {
        listOf(
            listOf("Uno", "Dos", "Las dos anteriores son correctas."),
            listOf("Uno", "Dos", "Correctas A) y B)"),
            listOf("Uno", "Dos", "Ninguna de las anteriores"),
            listOf("Uno", "Dos", "Las respuestas anteriores (A y B) son armas prohibidas."),
        ).forEach { opts ->
            assertFalse(OptionShuffler.canShuffle(opts))
            repeat(20) { seed -> assertEquals(listOf(0, 1, 2), OptionShuffler.order(opts, true, Random(seed))) }
        }
    }

    @Test
    fun scoringWithoutPenalty() {
        val answers = listOf(0 to 0, 1 to 2, 2 to null, 1 to 1)
        val r = Scoring.score(answers, ScoringRules(passPercent = 50.0))
        assertEquals(4, r.total)
        assertEquals(2, r.correct)
        assertEquals(1, r.incorrect)
        assertEquals(1, r.unanswered)
        assertEquals(2.0, r.score, 1e-9)
        assertEquals(50.0, r.accuracyPercent, 1e-9)
        assertEquals(true, r.passed)
    }

    @Test
    fun scoringWithPenaltyNeverPenalizesBlanks() {
        val answers = listOf(0 to 0, 0 to 1, 0 to 1, 0 to null)
        val r = Scoring.score(answers, ScoringRules(pointsPerCorrect = 1.0, penaltyPerWrong = 0.5, passPercent = 10.0))
        assertEquals(0.0, r.score, 1e-9)
        assertEquals(0.0, r.scorePercent, 1e-9)
        assertEquals(false, r.passed)
    }

    @Test
    fun officialPassMarkIsSixteenOfTwenty() {
        val pass = Scoring.passPercentFor(16, 20)
        assertEquals(80.0, pass, 1e-9)
        val fifteen = List(20) { i -> 0 to if (i < 15) 0 else 1 }
        val sixteen = List(20) { i -> 0 to if (i < 16) 0 else 1 }
        assertEquals(false, Scoring.score(fifteen, ScoringRules(passPercent = pass)).passed)
        assertEquals(true, Scoring.score(sixteen, ScoringRules(passPercent = pass)).passed)
    }

    @Test
    fun noPassThresholdMeansNoVerdict() {
        assertEquals(null, Scoring.score(listOf(0 to 0), ScoringRules(passPercent = null)).passed)
    }
}
