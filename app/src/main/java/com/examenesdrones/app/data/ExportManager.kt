package com.examenesdrones.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Exports the stored exam history (with every answer) as CSV or JSON. */
class ExportManager(private val context: Context, private val study: StudyRepository) {

    @Serializable
    data class ExportedAnswer(
        val position: Int, val topic: String, val question: String, val options: List<String>,
        val correctAnswer: String, val selectedAnswer: String?, val correct: Boolean?,
    )

    @Serializable
    data class ExportedExam(
        val id: Long, val date: String, val questions: Int, val correct: Int, val incorrect: Int, val unanswered: Int,
        val score: Double, val maxScore: Double, val accuracyPercent: Double, val passed: Boolean?,
        val elapsedSeconds: Int, val topics: List<String>, val answers: List<ExportedAnswer>,
    )

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

    suspend fun collect(): List<ExportedExam> = study.submittedOnce().map { e ->
        ExportedExam(
            id = e.id, date = dateFormat.format(Instant.ofEpochMilli(e.submittedAt ?: e.startedAt)),
            questions = e.questionCount, correct = e.correct, incorrect = e.incorrect, unanswered = e.unanswered,
            score = e.score, maxScore = e.maxScore, accuracyPercent = e.percent, passed = e.passed,
            elapsedSeconds = e.elapsedSec, topics = e.topicKeys.split(',').filter { it.isNotBlank() },
            answers = study.examResponses(e.id).map { r ->
                val opts = study.decodeOptions(r)
                ExportedAnswer(
                    r.position + 1, r.topicTitle, r.questionText, opts, opts.getOrElse(r.correctDisplayIndex) { "" },
                    r.selectedDisplayIndex?.let { opts.getOrNull(it) }, r.isCorrect,
                )
            },
        )
    }

    suspend fun writeJson(uri: Uri) = write(uri, Json { prettyPrint = true }.encodeToString(collect()))

    suspend fun writeCsv(uri: Uri) {
        val sb = StringBuilder()
        sb.append("exam_id,date,question_number,topic,question,selected_answer,correct_answer,result,exam_score,exam_max_score,exam_accuracy_percent,exam_passed\n")
        for (e in collect()) for (a in e.answers) {
            val result = when (a.correct) { true -> "correcta"; false -> "incorrecta"; null -> "sin responder" }
            sb.append(listOf(
                e.id.toString(), e.date, a.position.toString(), a.topic, a.question, a.selectedAnswer.orEmpty(),
                a.correctAnswer, result, "%.2f".format(java.util.Locale.ROOT, e.score),
                "%.2f".format(java.util.Locale.ROOT, e.maxScore), "%.1f".format(java.util.Locale.ROOT, e.accuracyPercent),
                e.passed?.toString().orEmpty(),
            ).joinToString(",") { csv(it) }).append('\n')
        }
        write(uri, sb.toString())
    }

    private fun csv(s: String): String = if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    private suspend fun write(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            ?: error("No se ha podido escribir el archivo")
    }
}
