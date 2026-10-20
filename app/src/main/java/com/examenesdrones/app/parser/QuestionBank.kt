package com.examenesdrones.app.parser

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Practice questions written from the study PDF (which contains no questions of its own).
 * Every question cites the PDF page and the literal sentence that supports its correct answer;
 * [DocumentParser] verifies that the sentence is on that page before the question can be used.
 */
@Serializable
data class QuestionBank(
    val version: Int = 1,
    /** Identifies the PDF edition the questions were written for. */
    val source: String = "",
    val questions: List<BankQuestion>,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun decode(text: String): QuestionBank = json.decodeFromString(serializer(), text)
    }
}

@Serializable
data class BankQuestion(
    val id: String,
    /** PDF page (1-based, as printed on the slide) where [source] appears. */
    val page: Int,
    val question: String,
    val options: List<String>,
    /** Index of the correct option (0-based). */
    val answer: Int,
    /** Literal sentence of the PDF that justifies the correct answer. */
    val source: String,
)
