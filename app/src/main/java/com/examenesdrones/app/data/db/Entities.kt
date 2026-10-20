package com.examenesdrones.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

// ------------------------------------------------------------------------------------------
// Study material (replaced as a whole on re-import)
// ------------------------------------------------------------------------------------------

@Entity(tableName = "imported_document")
data class ImportedDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val fileHash: String,
    val importedAt: Long,
    val pageCount: Int,
    val topicCount: Int,
    val questionCount: Int,
    val flaggedCount: Int,
    /** Serialized [com.examenesdrones.app.domain.ExamPresetData], null when the PDF does not define one. */
    val presetJson: String?,
    val active: Boolean,
)

@Entity(
    tableName = "topic",
    foreignKeys = [ForeignKey(ImportedDocumentEntity::class, ["id"], ["documentId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("documentId"), Index(value = ["documentId", "key"], unique = true)],
)
data class TopicEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    /** Stable across re-imports: T1..Tn in document order. */
    val key: String,
    val number: Int?,
    /** "module.section" (e.g. "1.3"); "1.R" for a module summary. */
    val code: String?,
    val title: String,
    /** Subtopics separated by new lines. */
    val subtopics: String,
    val position: Int,
    val blockCount: Int,
    val questionCount: Int,
    /** Course module the topic belongs to. */
    val module: String?,
)

@Entity(
    tableName = "theory_block",
    foreignKeys = [ForeignKey(TopicEntity::class, ["id"], ["topicId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["topicId", "position"], unique = true)],
)
data class TheoryBlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val topicId: Long,
    val position: Int,
    /** HEADING, PARAGRAPH, BULLET or IMAGE. */
    val type: String,
    val text: String,
    /** Bold ranges "start-end,start-end" (key concepts highlighted in the source). */
    val bold: String,
    val level: Int,
    val page: Int,
    val imageId: String?,
    val imageAspect: Float,
    /** Accent-free lowercase text used for search. */
    val searchText: String,
)

@Entity(
    tableName = "question",
    foreignKeys = [
        ForeignKey(ImportedDocumentEntity::class, ["id"], ["documentId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TopicEntity::class, ["id"], ["topicId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("documentId"), Index("topicId"), Index(value = ["stableKey"], unique = true)],
)
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val topicId: Long,
    val topicKey: String,
    /** Hash of the normalized question and options; links history across re-imports. */
    val stableKey: String,
    val numberInTopic: Int,
    val text: String,
    /** Index into the options (source order). Null means unknown: the question is not usable. */
    val correctIndex: Int?,
    /** Explanation provided by the source, if any. */
    val explanation: String?,
    val page: Int,
    /** Comma-separated [com.examenesdrones.app.parser.QuestionIssue] names. */
    val issues: String,
    /** Comma-separated block positions within the topic. */
    val relatedBlocks: String,
    val excluded: Boolean,
    val editedByUser: Boolean,
)

@Entity(
    tableName = "answer_option",
    foreignKeys = [ForeignKey(QuestionEntity::class, ["id"], ["questionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["questionId", "position"], unique = true)],
)
data class AnswerOptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionId: Long,
    val position: Int,
    val label: String,
    val text: String,
)

data class QuestionWithOptions(
    @Embedded val question: QuestionEntity,
    @Relation(parentColumn = "id", entityColumn = "questionId")
    val options: List<AnswerOptionEntity>,
) {
    val sortedOptions: List<AnswerOptionEntity> get() = options.sortedBy { it.position }
}

// ------------------------------------------------------------------------------------------
// User data (survives re-imports; references content only by stable keys)
// ------------------------------------------------------------------------------------------

@Entity(tableName = "practice_session")
data class PracticeSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val finishedAt: Long?,
    /** TOPICS, RANDOM or RETRY. */
    val mode: String,
    val topicKeys: String,
    /** Ordered, comma-separated stable keys of the session questions. */
    val questionKeys: String,
    val currentIndex: Int,
    val total: Int,
    val correct: Int,
    val incorrect: Int,
    val configJson: String,
    /** IN_PROGRESS, FINISHED or ABANDONED. */
    val status: String,
)

@Entity(
    tableName = "practice_response",
    foreignKeys = [ForeignKey(PracticeSessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId"), Index("questionKey"), Index(value = ["sessionId", "position"], unique = true)],
)
data class PracticeResponseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val position: Int,
    val questionKey: String,
    val topicKey: String,
    val topicTitle: String,
    val questionText: String,
    /** Index of the selected option in source order. */
    val selectedIndex: Int,
    val correctIndex: Int,
    val correct: Boolean,
    val answeredAt: Long,
)

@Entity(tableName = "exam_attempt")
data class ExamAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val submittedAt: Long?,
    /** IN_PROGRESS, SUBMITTED or ABANDONED. */
    val status: String,
    val configJson: String,
    val questionCount: Int,
    val correct: Int,
    val incorrect: Int,
    val unanswered: Int,
    val score: Double,
    val maxScore: Double,
    val percent: Double,
    val passed: Boolean?,
    val timeLimitSec: Int?,
    val elapsedSec: Int,
    val topicKeys: String,
    val currentIndex: Int,
    val documentHash: String,
)

@Entity(
    tableName = "exam_response",
    foreignKeys = [ForeignKey(ExamAttemptEntity::class, ["id"], ["examId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("examId"), Index("questionKey"), Index(value = ["examId", "position"], unique = true)],
)
data class ExamResponseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val examId: Long,
    val position: Int,
    val questionKey: String,
    val topicKey: String,
    val topicTitle: String,
    val questionText: String,
    /** JSON array of option texts in the order they were displayed. */
    val optionsJson: String,
    val correctDisplayIndex: Int,
    val selectedDisplayIndex: Int?,
    val isCorrect: Boolean?,
    val explanation: String?,
    /** JSON array of related theory excerpts, captured at exam time. */
    val relatedTheoryJson: String,
    val relatedBlockPosition: Int?,
)

@Entity(tableName = "question_stat")
data class QuestionStatEntity(
    @PrimaryKey val questionKey: String,
    val topicKey: String,
    val attempts: Int,
    val correctCount: Int,
    val lastCorrect: Boolean,
    val lastAnsweredAt: Long,
)

@Entity(tableName = "bookmark", indices = [Index(value = ["topicKey", "blockPosition"], unique = true)])
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val topicKey: String,
    val blockPosition: Int,
    val excerpt: String,
    val createdAt: Long,
)

@Entity(tableName = "reading_progress")
data class ReadingProgressEntity(
    @PrimaryKey val topicKey: String,
    val firstVisibleIndex: Int,
    val scrollOffset: Int,
    val maxSeenIndex: Int,
    val totalBlocks: Int,
    val completed: Boolean,
    val updatedAt: Long,
)

// ------------------------------------------------------------------------------------------
// Query projections
// ------------------------------------------------------------------------------------------

data class TopicAccuracyRow(
    val topicKey: String,
    val topicTitle: String,
    val answered: Int,
    val correct: Int,
)

data class MissedQuestionRow(
    val questionKey: String,
    val topicKey: String,
    val attempts: Int,
    @ColumnInfo(name = "correctCount") val correctCount: Int,
)
