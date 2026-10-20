package com.examenesdrones.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.examenesdrones.app.data.db.AppDatabase
import com.examenesdrones.app.domain.ExamConfig
import com.examenesdrones.app.domain.PracticeConfig
import com.examenesdrones.app.domain.PracticeMode
import com.examenesdrones.app.domain.ScoringRules
import com.examenesdrones.app.TestContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class RepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val dbName = "test-history.db"
    private lateinit var db: AppDatabase
    private lateinit var content: ContentRepository
    private lateinit var study: StudyRepository
    private var now = 1_700_000_000_000L

    private fun open() {
        db = Room.databaseBuilder(context, AppDatabase::class.java, dbName).allowMainThreadQueries().build()
        content = ContentRepository(context, db, json)
        study = StudyRepository(db, content, json, clock = { now++ }, random = Random(42))
    }

    @Before fun setUp() { context.deleteDatabase(dbName); open() }
    @After fun tearDown() { db.close(); context.deleteDatabase(dbName) }

    private fun restart() { db.close(); open() }

    /** A small study guide with 2 topics and [perTopic] questions each; option B is always correct. */
    private fun preview(perTopic: Int = 6, variant: String = "", hash: String = "hash-$variant"): ImportPreview =
        TestContent.preview(context, perTopic, variant, hash)

    private suspend fun importDoc(p: ImportPreview = preview()) = content.commit(p, p.document.questions.map { ReviewedQuestion(it) })

    @Test
    fun importStoresTopicsQuestionsAndExplanations() = runTest {
        importDoc()
        val topics = content.topicsOnce()
        assertEquals(listOf("T1", "T2"), topics.map { it.key })
        assertEquals(listOf("1.1", "1.2"), topics.map { it.code })
        assertEquals("Módulo de prueba", topics.first().module)
        val qs = content.usableQuestions()
        assertEquals(12, qs.size)
        assertTrue(qs.all { it.correctIndex == 1 })
        assertEquals(6, topics.first { it.key == "T1" }.questionCount)
        assertTrue(qs.all { it.explanation == "«${TestContent.SENTENCE_T1}» (pág. 3)" || it.explanation == "«${TestContent.SENTENCE_T2}» (pág. 4)" })
        assertNull(content.preset())
    }

    @Test
    fun questionsWithoutConfirmedAnswerAreNeverUsed() = runTest {
        val p = preview()
        val reviewed = p.document.questions.mapIndexed { i, q ->
            if (i == 0) ReviewedQuestion(q, correctIndex = null) else ReviewedQuestion(q)
        }
        content.commit(p, reviewed)
        assertEquals(11, content.usableQuestions().size)
        assertEquals(12, content.allQuestions().size)
    }

    @Test
    fun examSubmissionIsScoredAndSurvivesRestart() = runTest {
        importDoc()
        val config = ExamConfig(questionCount = 4, scoring = ScoringRules(passPercent = 50.0))
        val id = study.startExam(config, mapOf("T1" to "Tema uno", "T2" to "Tema dos"))
        val responses = study.examResponses(id)
        assertEquals(4, responses.size)
        assertTrue("answers must not be graded before submission", responses.all { it.isCorrect == null })
        // Correct, wrong, blank, correct.
        study.setExamAnswer(id, 0, responses[0].correctDisplayIndex)
        study.setExamAnswer(id, 1, (responses[1].correctDisplayIndex + 1) % 3)
        study.setExamAnswer(id, 3, responses[3].correctDisplayIndex)
        val score = study.submitExam(id, elapsedSec = 95)!!
        assertEquals(2, score.correct)
        assertEquals(1, score.incorrect)
        assertEquals(1, score.unanswered)
        assertEquals(true, score.passed)

        restart()

        val exam = study.exam(id)!!
        assertEquals(StudyRepository.STATUS_SUBMITTED, exam.status)
        assertEquals(2, exam.correct)
        assertEquals(1, exam.unanswered)
        assertEquals(50.0, exam.percent, 1e-9)
        assertEquals(95, exam.elapsedSec)
        assertEquals(listOf(true, false, null, true), study.examResponses(id).map { it.isCorrect })
        assertEquals(1, study.submittedExams.first().size)
        // Statistics are updated for every exam question.
        assertEquals(4, study.history().size)
    }

    @Test
    fun answersAreFrozenAfterSubmission() = runTest {
        importDoc()
        val id = study.startExam(ExamConfig(questionCount = 2), emptyMap())
        study.submitExam(id, 10)
        study.setExamAnswer(id, 0, 1)
        assertNull(study.examResponses(id)[0].selectedDisplayIndex)
        assertNull("second submission must be ignored", study.submitExam(id, 20))
        assertEquals(10, study.exam(id)!!.elapsedSec)
    }

    @Test
    fun reimportDoesNotTouchHistory() = runTest {
        importDoc(preview(variant = "v1"))
        val examId = study.startExam(ExamConfig(questionCount = 3), mapOf("T1" to "Tema uno"))
        val before = study.examResponses(examId)
        study.setExamAnswer(examId, 0, before[0].correctDisplayIndex)
        study.submitExam(examId, 30)
        val practiceId = study.startPractice(PracticeConfig(PracticeMode.RANDOM, count = 2))

        // Replace the study material with a different PDF.
        importDoc(preview(perTopic = 3, variant = "v2"))

        assertEquals(6, content.usableQuestions().size)
        val exam = study.exam(examId)!!
        assertEquals(StudyRepository.STATUS_SUBMITTED, exam.status)
        val after = study.examResponses(examId)
        assertEquals(before.map { it.questionText }, after.map { it.questionText })
        assertEquals(before.map { study.decodeOptions(it) }, after.map { study.decodeOptions(it) })
        assertNotNull(study.practiceSession(practiceId))
    }

    @Test
    fun reimportingTheSamePdfDoesNotDuplicateContent() = runTest {
        importDoc(preview(variant = "same", hash = "abc"))
        importDoc(preview(variant = "same", hash = "abc"))
        assertEquals(12, content.usableQuestions().size)
        assertEquals(2, content.topicsOnce().size)
    }

    @Test
    fun practiceSessionCanBeResumedAfterRestart() = runTest {
        importDoc()
        val id = study.startPractice(PracticeConfig(PracticeMode.TOPICS, topicKeys = listOf("T2"), count = 5))
        val keys = study.practiceSession(id)!!.questionKeys.split(',')
        assertEquals(5, keys.size)
        val questions = content.questionsByKeys(keys)
        assertTrue(questions.values.all { it.topicKey == "T2" })
        study.answerPractice(id, 0, questions.getValue(keys[0]), "Tema dos", 1)
        study.advancePractice(id, 1)
        study.answerPractice(id, 1, questions.getValue(keys[1]), "Tema dos", 0)

        restart()

        val resumed = study.practiceInProgress.first()!!
        assertEquals(id, resumed.id)
        assertEquals(1, resumed.currentIndex)
        assertEquals(1, resumed.correct)
        assertEquals(1, resumed.incorrect)
        assertEquals(listOf(true, false), study.practiceResponses(id).map { it.correct })
    }

    @Test
    fun practiceAnswerCannotBeSubmittedTwice() = runTest {
        importDoc()
        val id = study.startPractice(PracticeConfig(PracticeMode.RANDOM, count = 3))
        val key = study.practiceSession(id)!!.questionKeys.split(',').first()
        val q = content.questionsByKeys(listOf(key)).getValue(key)
        study.answerPractice(id, 0, q, "T", 1)
        study.answerPractice(id, 0, q, "T", 0)
        assertEquals(1, study.practiceResponses(id).size)
        assertEquals(1, study.practiceSession(id)!!.correct)
        assertEquals(1, study.history().getValue(key).attempts)
    }

    @Test
    fun inProgressExamKeepsAnswersAndTimeAfterRestart() = runTest {
        importDoc()
        val id = study.startExam(ExamConfig(questionCount = 5, timeLimitMinutes = 20), emptyMap())
        study.setExamAnswer(id, 2, 1)
        study.saveExamProgress(id, index = 2, elapsedSec = 300)

        restart()

        val exam = study.examInProgress.first()!!
        assertEquals(id, exam.id)
        assertEquals(2, exam.currentIndex)
        assertEquals(300, exam.elapsedSec)
        assertEquals(1200, exam.timeLimitSec)
        assertEquals(1, study.examResponses(id)[2].selectedDisplayIndex)
    }

    @Test
    fun startingANewExamAbandonsThePreviousUnfinishedOne() = runTest {
        importDoc()
        val first = study.startExam(ExamConfig(questionCount = 2), emptyMap())
        val second = study.startExam(ExamConfig(questionCount = 2), emptyMap())
        assertEquals(StudyRepository.STATUS_ABANDONED, study.exam(first)!!.status)
        assertEquals(second, study.examInProgress.first()!!.id)
    }

    @Test
    fun topicStatisticsCombinePracticeAndExams() = runTest {
        importDoc()
        val pid = study.startPractice(PracticeConfig(PracticeMode.TOPICS, topicKeys = listOf("T1"), count = 2))
        val keys = study.practiceSession(pid)!!.questionKeys.split(',')
        val qs = content.questionsByKeys(keys)
        study.answerPractice(pid, 0, qs.getValue(keys[0]), "Tema uno", 1)
        study.answerPractice(pid, 1, qs.getValue(keys[1]), "Tema uno", 2)

        val eid = study.startExam(ExamConfig(questionCount = 12), mapOf("T1" to "Tema uno", "T2" to "Tema dos"))
        study.examResponses(eid).forEach { r ->
            if (r.topicKey == "T2") study.setExamAnswer(eid, r.position, r.correctDisplayIndex)
        }
        study.submitExam(eid, 60)

        val rows = study.topicAccuracy.first().associateBy { it.topicKey }
        // T1: 2 practice answers (1 correct); its exam questions were left blank and are not counted as answered.
        assertEquals(2, rows.getValue("T1").answered)
        assertEquals(1, rows.getValue("T1").correct)
        assertEquals(6, rows.getValue("T2").answered)
        assertEquals(6, rows.getValue("T2").correct)
        // Blank exam answers count as not mastered for prioritization.
        val missed = study.mostMissed(50).first()
        assertTrue(missed.any { it.topicKey == "T1" })
    }

    @Test
    fun resetProgressKeepsStudyMaterial() = runTest {
        importDoc()
        val id = study.startExam(ExamConfig(questionCount = 2), emptyMap())
        study.submitExam(id, 5)
        study.resetProgress()
        assertTrue(study.submittedExams.first().isEmpty())
        assertTrue(study.history().isEmpty())
        assertEquals(12, content.usableQuestions().size)
    }
}
