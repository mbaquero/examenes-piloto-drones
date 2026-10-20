package com.examenesdrones.app.data

import androidx.room.withTransaction
import com.examenesdrones.app.data.db.AppDatabase
import com.examenesdrones.app.data.db.ExamAttemptEntity
import com.examenesdrones.app.data.db.ExamResponseEntity
import com.examenesdrones.app.data.db.PracticeResponseEntity
import com.examenesdrones.app.data.db.PracticeSessionEntity
import com.examenesdrones.app.data.db.QuestionStatEntity
import com.examenesdrones.app.domain.ExamConfig
import com.examenesdrones.app.domain.OptionShuffler
import com.examenesdrones.app.domain.PracticeConfig
import com.examenesdrones.app.domain.PracticeMode
import com.examenesdrones.app.domain.Question
import com.examenesdrones.app.domain.QuestionHistory
import com.examenesdrones.app.domain.QuestionSelector
import com.examenesdrones.app.domain.ScoreResult
import com.examenesdrones.app.domain.Scoring
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.random.Random

class InsufficientQuestionsException(val available: Int) : Exception("Only $available questions available")

/** Practice sessions, exams and per-question statistics. */
class StudyRepository(
    private val db: AppDatabase,
    private val content: ContentRepository,
    private val json: Json,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
) {
    private val practiceDao = db.practiceDao()
    private val examDao = db.examDao()
    private val statsDao = db.statsDao()

    val practiceInProgress: Flow<PracticeSessionEntity?> = practiceDao.observeInProgress()
    val examInProgress: Flow<ExamAttemptEntity?> = examDao.observeInProgress()
    val submittedExams: Flow<List<ExamAttemptEntity>> = examDao.observeSubmitted()
    val questionStats: Flow<List<QuestionStatEntity>> = statsDao.observeAll()
    val topicAccuracy = statsDao.observeTopicAccuracy()
    val totalPracticeAnswers = practiceDao.observeTotalAnswered()
    fun mostMissed(limit: Int = 20) = statsDao.observeMostMissed(limit)

    suspend fun history(): Map<String, QuestionHistory> =
        statsDao.all().associate { it.questionKey to QuestionHistory(it.attempts, it.correctCount, it.lastCorrect) }

    private suspend fun recordStat(key: String, topicKey: String, correct: Boolean, at: Long) {
        val old = statsDao.stat(key)
        statsDao.upsert(
            QuestionStatEntity(
                questionKey = key, topicKey = topicKey,
                attempts = (old?.attempts ?: 0) + 1,
                correctCount = (old?.correctCount ?: 0) + if (correct) 1 else 0,
                lastCorrect = correct, lastAnsweredAt = at,
            )
        )
    }

    // ------------------------------------------------------------------------------------
    // Practice ("Preguntas en vivo")
    // ------------------------------------------------------------------------------------

    suspend fun startPractice(config: PracticeConfig): Long {
        val pool = content.usableQuestions()
        val questions = when (config.mode) {
            PracticeMode.RETRY -> {
                val byKey = pool.associateBy { it.key }
                config.questionKeys.mapNotNull { byKey[it] }.distinctBy { it.key }
                    .let { if (config.shuffleQuestions) it.shuffled(random) else it }
            }
            PracticeMode.TOPICS -> QuestionSelector.selectPractice(
                pool.filter { it.topicKey in config.topicKeys }, history(), config.count,
                config.prioritizeWeak, config.shuffleQuestions, random,
            )
            PracticeMode.RANDOM -> QuestionSelector.selectPractice(
                pool, history(), config.count, config.prioritizeWeak, config.shuffleQuestions, random,
            )
        }
        if (questions.isEmpty()) throw InsufficientQuestionsException(0)
        val id = practiceDao.insertSession(
            PracticeSessionEntity(
                startedAt = clock(), finishedAt = null, mode = config.mode.name,
                topicKeys = questions.map { it.topicKey }.distinct().joinToString(","),
                questionKeys = questions.joinToString(",") { it.key }, currentIndex = 0, total = questions.size,
                correct = 0, incorrect = 0, configJson = json.encodeToString(config), status = STATUS_IN_PROGRESS,
            )
        )
        practiceDao.abandonOthers(id)
        return id
    }

    suspend fun practiceSession(id: Long) = practiceDao.session(id)
    suspend fun practiceResponses(id: Long) = practiceDao.responses(id)

    suspend fun answerPractice(sessionId: Long, position: Int, question: Question, topicTitle: String, selected: Int): Boolean {
        val correct = selected == question.correctIndex
        val now = clock()
        db.withTransaction {
            val session = practiceDao.session(sessionId) ?: return@withTransaction
            if (practiceDao.responses(sessionId).any { it.position == position }) return@withTransaction // no double submit
            practiceDao.insertResponse(
                PracticeResponseEntity(
                    sessionId = sessionId, position = position, questionKey = question.key, topicKey = question.topicKey,
                    topicTitle = topicTitle, questionText = question.text, selectedIndex = selected,
                    correctIndex = question.correctIndex, correct = correct, answeredAt = now,
                )
            )
            practiceDao.updateSession(
                session.copy(
                    correct = session.correct + if (correct) 1 else 0,
                    incorrect = session.incorrect + if (correct) 0 else 1,
                )
            )
            recordStat(question.key, question.topicKey, correct, now)
        }
        return correct
    }

    suspend fun advancePractice(sessionId: Long, nextIndex: Int) {
        val s = practiceDao.session(sessionId) ?: return
        val finished = nextIndex >= s.total
        practiceDao.updateSession(
            s.copy(
                currentIndex = nextIndex.coerceAtMost(s.total),
                status = if (finished) STATUS_FINISHED else s.status,
                finishedAt = if (finished) clock() else null,
            )
        )
    }

    suspend fun finishPracticeEarly(sessionId: Long) {
        val s = practiceDao.session(sessionId) ?: return
        val answered = practiceDao.responses(sessionId).size
        practiceDao.updateSession(s.copy(status = STATUS_FINISHED, finishedAt = clock(), total = answered.coerceAtLeast(0)))
    }

    suspend fun abandonPractice(sessionId: Long) {
        val s = practiceDao.session(sessionId) ?: return
        if (s.status == STATUS_IN_PROGRESS) practiceDao.updateSession(s.copy(status = STATUS_ABANDONED))
    }

    // ------------------------------------------------------------------------------------
    // Exams
    // ------------------------------------------------------------------------------------

    suspend fun startExam(config: ExamConfig, topicTitles: Map<String, String>): Long {
        val pool = content.usableQuestions()
        val selected = QuestionSelector.selectExam(pool, config, random)
        if (selected.isEmpty()) throw InsufficientQuestionsException(0)
        val docHash = db.contentDao().activeDocument()?.fileHash.orEmpty()
        val responses = selected.mapIndexed { i, q ->
            val order = OptionShuffler.order(q.options, config.shuffleOptions, random)
            val related = content.relatedTheory(q)
            ExamResponseEntity(
                examId = 0, position = i, questionKey = q.key, topicKey = q.topicKey,
                topicTitle = topicTitles[q.topicKey] ?: q.topicKey, questionText = q.text,
                optionsJson = json.encodeToString(order.map { q.options[it] }),
                correctDisplayIndex = OptionShuffler.correctDisplayIndex(order, q.correctIndex),
                selectedDisplayIndex = null, isCorrect = null, explanation = q.explanation,
                relatedTheoryJson = json.encodeToString(related.map { it.text }),
                relatedBlockPosition = related.firstOrNull()?.position,
            )
        }
        var id = 0L
        db.withTransaction {
            id = examDao.insertAttempt(
                ExamAttemptEntity(
                    startedAt = clock(), submittedAt = null, status = STATUS_IN_PROGRESS,
                    configJson = json.encodeToString(config), questionCount = selected.size,
                    correct = 0, incorrect = 0, unanswered = selected.size, score = 0.0, maxScore = 0.0, percent = 0.0,
                    passed = null, timeLimitSec = config.timeLimitMinutes?.let { it * 60 }, elapsedSec = 0,
                    topicKeys = selected.map { it.topicKey }.distinct().sortedBy(QuestionSelector::topicSortKey).joinToString(","),
                    currentIndex = 0, documentHash = docHash,
                )
            )
            examDao.insertResponses(responses.map { it.copy(examId = id) })
            examDao.abandonOthers(id)
        }
        return id
    }

    suspend fun exam(id: Long) = examDao.attempt(id)
    fun observeExam(id: Long) = examDao.observeAttempt(id)
    suspend fun examResponses(id: Long) = examDao.responses(id)

    suspend fun setExamAnswer(examId: Long, position: Int, selected: Int?) {
        val exam = examDao.attempt(examId) ?: return
        if (exam.status != STATUS_IN_PROGRESS) return // answers are frozen after submission
        examDao.setAnswer(examId, position, selected)
    }

    suspend fun saveExamProgress(examId: Long, index: Int, elapsedSec: Int) = examDao.saveProgress(examId, index, elapsedSec)

    /** Scores the exam and stores the result before anything is shown to the user. */
    suspend fun submitExam(examId: Long, elapsedSec: Int): ScoreResult? {
        var result: ScoreResult? = null
        db.withTransaction {
            val exam = examDao.attempt(examId) ?: return@withTransaction
            if (exam.status != STATUS_IN_PROGRESS) return@withTransaction
            val config = json.decodeFromString<ExamConfig>(exam.configJson)
            val responses = examDao.responses(examId)
            val score = Scoring.score(responses.map { it.correctDisplayIndex to it.selectedDisplayIndex }, config.scoring)
            val now = clock()
            examDao.updateResponses(responses.map { r ->
                r.copy(isCorrect = r.selectedDisplayIndex?.let { it == r.correctDisplayIndex })
            })
            examDao.updateAttempt(
                exam.copy(
                    submittedAt = now, status = STATUS_SUBMITTED, correct = score.correct, incorrect = score.incorrect,
                    unanswered = score.unanswered, score = score.score, maxScore = score.maxScore,
                    percent = score.accuracyPercent, passed = score.passed, elapsedSec = elapsedSec,
                )
            )
            responses.forEach { r ->
                // Unanswered questions count as not mastered for practice prioritization.
                recordStat(r.questionKey, r.topicKey, r.selectedDisplayIndex == r.correctDisplayIndex, now)
            }
            result = score
        }
        return result
    }

    suspend fun abandonExam(examId: Long) {
        val e = examDao.attempt(examId) ?: return
        if (e.status == STATUS_IN_PROGRESS) examDao.updateAttempt(e.copy(status = STATUS_ABANDONED))
    }

    suspend fun deleteExam(examId: Long) = examDao.delete(examId)
    suspend fun submittedOnce() = examDao.submitted()
    suspend fun recurringExamMistakes() = examDao.recurringExamMistakes()

    fun decodeOptions(r: ExamResponseEntity): List<String> = json.decodeFromString(r.optionsJson)
    fun decodeTheory(r: ExamResponseEntity): List<String> = runCatching { json.decodeFromString<List<String>>(r.relatedTheoryJson) }.getOrDefault(emptyList())
    fun decodeExamConfig(e: ExamAttemptEntity): ExamConfig = json.decodeFromString(e.configJson)
    fun decodePracticeConfig(s: PracticeSessionEntity): PracticeConfig = json.decodeFromString(s.configJson)

    suspend fun resetProgress() {
        val user = db.userDao()
        db.withTransaction {
            user.clearExams()
            user.clearPractice()
            user.clearReading()
            user.clearBookmarks()
            statsDao.clear()
        }
    }

    companion object {
        const val STATUS_IN_PROGRESS = "IN_PROGRESS"
        const val STATUS_FINISHED = "FINISHED"
        const val STATUS_SUBMITTED = "SUBMITTED"
        const val STATUS_ABANDONED = "ABANDONED"
    }
}
