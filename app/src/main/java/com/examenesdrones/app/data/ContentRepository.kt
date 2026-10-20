package com.examenesdrones.app.data

import android.content.Context
import androidx.room.withTransaction
import com.examenesdrones.app.data.db.AnswerOptionEntity
import com.examenesdrones.app.data.db.AppDatabase
import com.examenesdrones.app.data.db.ImportedDocumentEntity
import com.examenesdrones.app.data.db.QuestionEntity
import com.examenesdrones.app.data.db.QuestionWithOptions
import com.examenesdrones.app.data.db.TheoryBlockEntity
import com.examenesdrones.app.data.db.TopicEntity
import com.examenesdrones.app.domain.ExamPresetData
import com.examenesdrones.app.domain.Question
import com.examenesdrones.app.parser.ParsedDocument
import com.examenesdrones.app.parser.ParsedQuestion
import com.examenesdrones.app.parser.QuestionIssue
import com.examenesdrones.app.parser.TextNormalizer
import com.examenesdrones.app.pdf.ExtractedPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** A parsed PDF ready to be stored. */
data class ImportPreview(
    val extracted: ExtractedPdf,
    val document: ParsedDocument,
)

class ContentRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val json: Json,
) {
    private val dao = db.contentDao()

    val activeDocument: Flow<ImportedDocumentEntity?> = dao.observeActiveDocument()
    val topics: Flow<List<TopicEntity>> = dao.observeTopics()
    val usableQuestionCount: Flow<Int> = dao.observeUsableQuestionCount()

    /**
     * Replaces the study material with [questions] (possibly edited by the user) in a single transaction.
     * History tables are untouched: they reference questions by stable key and keep snapshots.
     */
    suspend fun commit(preview: ImportPreview, questions: List<ReviewedQuestion>) = withContext(Dispatchers.IO) {
        val doc = preview.document
        val preset = doc.examPreset?.let { ExamPresetData(it.questionCount, it.passMark, it.timeLimitMinutes, it.perTopic) }
        db.withTransaction {
            dao.deleteAllDocuments()
            val docId = dao.insertDocument(
                ImportedDocumentEntity(
                    fileName = preview.extracted.fileName,
                    fileHash = preview.extracted.fileHash,
                    importedAt = System.currentTimeMillis(),
                    pageCount = doc.pageCount,
                    topicCount = doc.topics.size,
                    questionCount = questions.count { it.usable },
                    flaggedCount = questions.count { !it.usable },
                    presetJson = preset?.let { json.encodeToString(it) },
                    active = true,
                )
            )
            val topicIds = HashMap<String, Long>()
            doc.topics.forEachIndexed { i, t ->
                val id = dao.insertTopic(
                    TopicEntity(
                        documentId = docId, key = t.key, number = t.number, code = t.code, title = t.title,
                        subtopics = t.subtopics.joinToString("\n"), position = i, blockCount = t.blocks.size,
                        questionCount = questions.count { it.parsed.topicKey == t.key && it.usable }, module = t.module,
                    )
                )
                topicIds[t.key] = id
                dao.insertBlocks(t.blocks.mapIndexed { pos, b ->
                    TheoryBlockEntity(
                        topicId = id, position = pos, type = b.type.name, text = b.text.text, bold = b.text.encodeBold(),
                        level = b.level, page = b.page, imageId = b.imageId, imageAspect = b.imageAspect,
                        searchText = TextNormalizer.foldForSearch(b.text.text),
                    )
                })
            }
            for (rq in questions) {
                val q = rq.parsed
                val topicId = topicIds[q.topicKey] ?: continue
                val qid = dao.insertQuestion(
                    QuestionEntity(
                        documentId = docId, topicId = topicId, topicKey = q.topicKey, stableKey = q.stableKey,
                        numberInTopic = q.numberInTopic, text = rq.text, correctIndex = rq.correctIndex,
                        explanation = q.explanation, page = q.page, issues = rq.remainingIssues.joinToString(",") { it.name },
                        relatedBlocks = q.relatedBlocks.joinToString(","), excluded = rq.excluded, editedByUser = rq.edited,
                    )
                )
                dao.insertOptions(rq.options.mapIndexed { i, text ->
                    AnswerOptionEntity(questionId = qid, position = i, label = q.options[i].label, text = text)
                })
            }
        }
        cleanupImages(keep = preview.extracted.imageDir)
    }

    private fun cleanupImages(keep: File) {
        File(context.filesDir, "images").listFiles()?.filter { it != keep }?.forEach { it.deleteRecursively() }
    }

    fun imageFile(imageId: String): File = File(context.filesDir, "images/$imageId")

    suspend fun topicsOnce(): List<TopicEntity> = dao.topics()
    suspend fun topic(key: String): TopicEntity? = dao.topicByKey(key)
    suspend fun blocks(topicId: Long): List<TheoryBlockEntity> = dao.blocks(topicId)
    suspend fun search(query: String): List<TheoryBlockEntity> {
        val q = TextNormalizer.foldForSearch(query)
        return if (q.length < 2) emptyList() else dao.searchBlocks(q)
    }
    suspend fun topicById(id: Long) = dao.topicById(id)

    suspend fun preset(): ExamPresetData? =
        dao.activeDocument()?.presetJson?.let { runCatching { json.decodeFromString<ExamPresetData>(it) }.getOrNull() }

    suspend fun usableQuestions(): List<Question> = dao.usableQuestions().mapNotNull { it.toDomain() }

    suspend fun allQuestions(): List<QuestionWithOptions> = dao.allQuestions()

    suspend fun questionsByKeys(keys: List<String>): Map<String, Question> =
        keys.chunked(500).flatMap { dao.questionsByKeys(it) }.mapNotNull { it.toDomain() }.associateBy { it.key }

    /** Related theory excerpts (source text) for a question. */
    suspend fun relatedTheory(q: Question): List<TheoryBlockEntity> {
        val topic = dao.topicByKey(q.topicKey) ?: return emptyList()
        if (q.relatedBlocks.isEmpty()) return emptyList()
        return dao.blocksAt(topic.id, q.relatedBlocks)
    }
}

/** A parsed question as stored; questions without a confirmed answer are kept but never used. */
data class ReviewedQuestion(
    val parsed: ParsedQuestion,
    val text: String = parsed.text,
    val options: List<String> = parsed.options.map { it.text },
    val correctIndex: Int? = parsed.correctIndex,
    val excluded: Boolean = false,
    val edited: Boolean = false,
) {
    /** A user-confirmed edit resolves the blocking issues; informative ones remain. */
    val remainingIssues: Set<QuestionIssue>
        get() = if (edited) parsed.issues.filterTo(HashSet()) { !it.blocking } else parsed.issues

    /** Usable in practice and exams: it has a known correct answer and enough options. */
    val usable: Boolean
        get() = !excluded && correctIndex != null && correctIndex in options.indices && options.size >= 2 &&
            text.isNotBlank() && remainingIssues.none { it.blocking }

    val needsReview: Boolean get() = !excluded && !usable
}

fun QuestionWithOptions.toDomain(): Question? {
    val q = question
    val correct = q.correctIndex ?: return null
    val opts = sortedOptions.map { it.text }
    if (correct !in opts.indices) return null
    return Question(
        key = q.stableKey, topicKey = q.topicKey, number = q.numberInTopic, text = q.text, options = opts,
        correctIndex = correct, explanation = q.explanation,
        relatedBlocks = q.relatedBlocks.split(',').mapNotNull { it.trim().toIntOrNull() }, page = q.page,
    )
}
