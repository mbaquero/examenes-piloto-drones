package com.examenesdrones.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ContentDao {
    @Query("SELECT * FROM imported_document WHERE active = 1 LIMIT 1")
    fun observeActiveDocument(): Flow<ImportedDocumentEntity?>

    @Query("SELECT * FROM imported_document WHERE active = 1 LIMIT 1")
    suspend fun activeDocument(): ImportedDocumentEntity?

    @Insert
    suspend fun insertDocument(doc: ImportedDocumentEntity): Long

    @Query("DELETE FROM imported_document")
    suspend fun deleteAllDocuments()

    @Insert
    suspend fun insertTopic(topic: TopicEntity): Long

    @Insert
    suspend fun insertBlocks(blocks: List<TheoryBlockEntity>)

    @Insert
    suspend fun insertQuestion(q: QuestionEntity): Long

    @Insert
    suspend fun insertOptions(options: List<AnswerOptionEntity>)

    @Query("SELECT t.* FROM topic t JOIN imported_document d ON d.id = t.documentId WHERE d.active = 1 ORDER BY t.position")
    fun observeTopics(): Flow<List<TopicEntity>>

    @Query("SELECT t.* FROM topic t JOIN imported_document d ON d.id = t.documentId WHERE d.active = 1 ORDER BY t.position")
    suspend fun topics(): List<TopicEntity>

    @Query("SELECT t.* FROM topic t JOIN imported_document d ON d.id = t.documentId WHERE d.active = 1 AND t.`key` = :key LIMIT 1")
    suspend fun topicByKey(key: String): TopicEntity?

    @Query("SELECT * FROM theory_block WHERE topicId = :topicId ORDER BY position")
    suspend fun blocks(topicId: Long): List<TheoryBlockEntity>

    @Query("SELECT * FROM theory_block WHERE topicId = :topicId AND position IN (:positions) ORDER BY position")
    suspend fun blocksAt(topicId: Long, positions: List<Int>): List<TheoryBlockEntity>

    @Query(
        """SELECT b.* FROM theory_block b JOIN topic t ON t.id = b.topicId JOIN imported_document d ON d.id = t.documentId
           WHERE d.active = 1 AND b.type != 'IMAGE' AND b.searchText LIKE '%' || :query || '%'
           ORDER BY t.position, b.position LIMIT 100"""
    )
    suspend fun searchBlocks(query: String): List<TheoryBlockEntity>

    @Query("SELECT * FROM topic WHERE id = :id")
    suspend fun topicById(id: Long): TopicEntity?

    @Transaction
    @Query(
        """SELECT q.* FROM question q JOIN imported_document d ON d.id = q.documentId
           WHERE d.active = 1 AND q.excluded = 0 AND q.correctIndex IS NOT NULL ORDER BY q.topicKey, q.numberInTopic"""
    )
    suspend fun usableQuestions(): List<QuestionWithOptions>

    @Transaction
    @Query("SELECT q.* FROM question q JOIN imported_document d ON d.id = q.documentId WHERE d.active = 1 ORDER BY q.topicKey, q.numberInTopic")
    suspend fun allQuestions(): List<QuestionWithOptions>

    @Transaction
    @Query("SELECT * FROM question WHERE stableKey IN (:keys)")
    suspend fun questionsByKeys(keys: List<String>): List<QuestionWithOptions>

    @Query(
        """SELECT COUNT(*) FROM question q JOIN imported_document d ON d.id = q.documentId
           WHERE d.active = 1 AND q.excluded = 0 AND q.correctIndex IS NOT NULL"""
    )
    fun observeUsableQuestionCount(): Flow<Int>

    @Query("UPDATE question SET correctIndex = :correctIndex, text = :text, excluded = :excluded, editedByUser = 1, issues = :issues WHERE id = :id")
    suspend fun updateQuestion(id: Long, text: String, correctIndex: Int?, excluded: Boolean, issues: String)

    @Query("UPDATE answer_option SET text = :text WHERE id = :id")
    suspend fun updateOption(id: Long, text: String)
}

@Dao
interface PracticeDao {
    @Insert
    suspend fun insertSession(s: PracticeSessionEntity): Long

    @Update
    suspend fun updateSession(s: PracticeSessionEntity)

    @Query("SELECT * FROM practice_session WHERE id = :id")
    suspend fun session(id: Long): PracticeSessionEntity?

    @Query("SELECT * FROM practice_session WHERE status = 'IN_PROGRESS' ORDER BY startedAt DESC LIMIT 1")
    fun observeInProgress(): Flow<PracticeSessionEntity?>

    @Query("UPDATE practice_session SET status = 'ABANDONED' WHERE status = 'IN_PROGRESS' AND id != :keepId")
    suspend fun abandonOthers(keepId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResponse(r: PracticeResponseEntity)

    @Query("SELECT * FROM practice_response WHERE sessionId = :sessionId ORDER BY position")
    suspend fun responses(sessionId: Long): List<PracticeResponseEntity>

    @Query("SELECT COUNT(*) FROM practice_response")
    fun observeTotalAnswered(): Flow<Int>
}

@Dao
interface ExamDao {
    @Insert
    suspend fun insertAttempt(a: ExamAttemptEntity): Long

    @Update
    suspend fun updateAttempt(a: ExamAttemptEntity)

    @Insert
    suspend fun insertResponses(r: List<ExamResponseEntity>)

    @Query("UPDATE exam_response SET selectedDisplayIndex = :selected WHERE examId = :examId AND position = :position")
    suspend fun setAnswer(examId: Long, position: Int, selected: Int?)

    @Query("UPDATE exam_attempt SET currentIndex = :index, elapsedSec = :elapsed WHERE id = :examId AND status = 'IN_PROGRESS'")
    suspend fun saveProgress(examId: Long, index: Int, elapsed: Int)

    @Update
    suspend fun updateResponses(r: List<ExamResponseEntity>)

    @Query("SELECT * FROM exam_attempt WHERE id = :id")
    suspend fun attempt(id: Long): ExamAttemptEntity?

    @Query("SELECT * FROM exam_attempt WHERE id = :id")
    fun observeAttempt(id: Long): Flow<ExamAttemptEntity?>

    @Query("SELECT * FROM exam_response WHERE examId = :examId ORDER BY position")
    suspend fun responses(examId: Long): List<ExamResponseEntity>

    @Query("SELECT * FROM exam_attempt WHERE status = 'SUBMITTED' ORDER BY submittedAt DESC")
    fun observeSubmitted(): Flow<List<ExamAttemptEntity>>

    @Query("SELECT * FROM exam_attempt WHERE status = 'SUBMITTED' ORDER BY submittedAt DESC")
    suspend fun submitted(): List<ExamAttemptEntity>

    @Query("SELECT * FROM exam_attempt WHERE status = 'IN_PROGRESS' ORDER BY startedAt DESC LIMIT 1")
    fun observeInProgress(): Flow<ExamAttemptEntity?>

    @Query("UPDATE exam_attempt SET status = 'ABANDONED' WHERE status = 'IN_PROGRESS' AND id != :keepId")
    suspend fun abandonOthers(keepId: Long)

    @Query("DELETE FROM exam_attempt WHERE id = :id")
    suspend fun delete(id: Long)

    /** Questions answered incorrectly in more than one submitted exam. */
    @Query(
        """SELECT r.questionKey AS questionKey, r.topicKey AS topicKey, COUNT(*) AS attempts,
                  SUM(CASE WHEN r.isCorrect = 1 THEN 1 ELSE 0 END) AS correctCount
           FROM exam_response r JOIN exam_attempt a ON a.id = r.examId
           WHERE a.status = 'SUBMITTED' GROUP BY r.questionKey
           HAVING SUM(CASE WHEN r.isCorrect = 1 THEN 0 ELSE 1 END) >= 2
           ORDER BY (COUNT(*) - correctCount) DESC LIMIT 30"""
    )
    suspend fun recurringExamMistakes(): List<MissedQuestionRow>
}

@Dao
interface StatsDao {
    @Query("SELECT * FROM question_stat WHERE questionKey = :key")
    suspend fun stat(key: String): QuestionStatEntity?

    @Upsert
    suspend fun upsert(stat: QuestionStatEntity)

    @Query("SELECT * FROM question_stat")
    suspend fun all(): List<QuestionStatEntity>

    @Query("SELECT * FROM question_stat")
    fun observeAll(): Flow<List<QuestionStatEntity>>

    @Query(
        """SELECT questionKey, topicKey, attempts, correctCount FROM question_stat
           WHERE attempts > correctCount ORDER BY (attempts - correctCount) DESC, attempts DESC LIMIT :limit"""
    )
    fun observeMostMissed(limit: Int): Flow<List<MissedQuestionRow>>

    @Query(
        """SELECT topicKey, MAX(topicTitle) AS topicTitle, COUNT(*) AS answered, SUM(c) AS correct FROM (
              SELECT topicKey, topicTitle, CASE WHEN correct THEN 1 ELSE 0 END AS c FROM practice_response
              UNION ALL
              SELECT r.topicKey, r.topicTitle, CASE WHEN r.isCorrect = 1 THEN 1 ELSE 0 END AS c
              FROM exam_response r JOIN exam_attempt a ON a.id = r.examId
              WHERE a.status = 'SUBMITTED' AND r.selectedDisplayIndex IS NOT NULL
           ) GROUP BY topicKey ORDER BY topicKey"""
    )
    fun observeTopicAccuracy(): Flow<List<TopicAccuracyRow>>

    @Query("DELETE FROM question_stat")
    suspend fun clear()
}

@Dao
interface UserDao {
    @Query("SELECT * FROM bookmark ORDER BY createdAt DESC")
    fun observeBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmark WHERE topicKey = :topicKey ORDER BY blockPosition")
    fun observeBookmarks(topicKey: String): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addBookmark(b: BookmarkEntity)

    @Query("DELETE FROM bookmark WHERE topicKey = :topicKey AND blockPosition = :position")
    suspend fun removeBookmark(topicKey: String, position: Int)

    @Query("SELECT * FROM reading_progress")
    fun observeReading(): Flow<List<ReadingProgressEntity>>

    @Query("SELECT * FROM reading_progress WHERE topicKey = :key")
    suspend fun reading(key: String): ReadingProgressEntity?

    @Upsert
    suspend fun upsertReading(p: ReadingProgressEntity)

    @Query("SELECT * FROM reading_progress ORDER BY updatedAt DESC LIMIT 1")
    fun observeLastRead(): Flow<ReadingProgressEntity?>

    @Query("DELETE FROM reading_progress")
    suspend fun clearReading()

    @Query("DELETE FROM bookmark")
    suspend fun clearBookmarks()

    @Query("DELETE FROM practice_session")
    suspend fun clearPractice()

    @Query("DELETE FROM exam_attempt")
    suspend fun clearExams()
}
