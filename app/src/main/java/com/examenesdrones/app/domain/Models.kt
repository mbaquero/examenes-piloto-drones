package com.examenesdrones.app.domain

import kotlinx.serialization.Serializable

/** A usable question (it has a confirmed correct answer). Options are in source order. */
data class Question(
    val key: String,
    val topicKey: String,
    val number: Int,
    val text: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String?,
    val relatedBlocks: List<Int>,
    val page: Int,
)

/** Answer history of a question across practice sessions and exams. */
data class QuestionHistory(val attempts: Int, val correctCount: Int, val lastCorrect: Boolean)

@Serializable
data class ExamPresetData(
    val questionCount: Int,
    val passMark: Int? = null,
    val timeLimitMinutes: Int? = null,
    val perTopic: Map<String, Int> = emptyMap(),
)

@Serializable
enum class PracticeMode { TOPICS, RANDOM, RETRY }

@Serializable
data class PracticeConfig(
    val mode: PracticeMode,
    val topicKeys: List<String> = emptyList(),
    /** 0 means all available questions. */
    val count: Int = 20,
    val prioritizeWeak: Boolean = true,
    val shuffleQuestions: Boolean = true,
    val shuffleOptions: Boolean = false,
    /** Explicit question list (used to retry the questions failed in a session). */
    val questionKeys: List<String> = emptyList(),
)

@Serializable
data class ScoringRules(
    val pointsPerCorrect: Double = 1.0,
    /** Points subtracted for each incorrect answer; unanswered questions never penalize. */
    val penaltyPerWrong: Double = 0.0,
    /** Pass threshold as a percentage of the maximum score; null disables pass/fail. */
    val passPercent: Double? = null,
)

@Serializable
data class ExamConfig(
    val questionCount: Int,
    /** Empty means the whole syllabus. */
    val topicKeys: List<String> = emptyList(),
    /** Questions per topic, as defined by the official exam format (only for the whole syllabus). */
    val perTopic: Map<String, Int> = emptyMap(),
    val randomSelection: Boolean = true,
    val shuffleQuestions: Boolean = true,
    val shuffleOptions: Boolean = false,
    val timeLimitMinutes: Int? = null,
    val scoring: ScoringRules = ScoringRules(),
    val presetName: String? = null,
)

data class ScoreResult(
    val total: Int,
    val correct: Int,
    val incorrect: Int,
    val unanswered: Int,
    val score: Double,
    val maxScore: Double,
    /** Correct answers as a percentage of all questions. */
    val accuracyPercent: Double,
    /** Net score as a percentage of the maximum score (used for pass/fail). */
    val scorePercent: Double,
    val passed: Boolean?,
)
