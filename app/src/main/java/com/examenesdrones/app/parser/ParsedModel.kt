package com.examenesdrones.app.parser

/** An image drawn on a PDF page, positioned by its top edge (top-down coordinates). */
data class PageImage(val page: Int, val top: Float, val id: String, val width: Int, val height: Int)

data class ParserInput(
    val lines: List<StyledLine>,
    val images: List<PageImage> = emptyList(),
    val pageCount: Int = lines.maxOfOrNull { it.page } ?: 0,
)

enum class BlockType { HEADING, PARAGRAPH, BULLET, IMAGE }

data class ContentBlock(
    val type: BlockType,
    val text: StyledText,
    val page: Int,
    val level: Int = 0,
    val imageId: String? = null,
    val imageAspect: Float = 0f,
)

data class ParsedTopic(
    /** Stable identifier across re-imports: T1..Tn, numbered in document order. */
    val key: String,
    val number: Int?,
    /** "module.section" as printed in the module index (e.g. "1.3"), or "1.R" for a module summary. */
    val code: String?,
    val title: String,
    val subtopics: List<String>,
    val blocks: List<ContentBlock>,
    /** Title of the course module the topic belongs to (e.g. "Concepto normativo"). */
    val module: String? = null,
    /** First and last PDF page of the topic. */
    val pages: IntRange = IntRange.EMPTY,
)

enum class QuestionIssue {
    NO_CORRECT_ANSWER,
    TOO_FEW_OPTIONS,
    DUPLICATE,
    EMPTY_TEXT,
    UNKNOWN_TOPIC,
    /** The literal source sentence that justifies the answer was not found on the cited page. */
    SOURCE_NOT_FOUND;

    /** Issues that make a question unusable. */
    val blocking: Boolean
        get() = this != DUPLICATE
}

data class ParsedOption(val label: String, val text: String)

data class ParsedQuestion(
    val topicKey: String,
    val numberInTopic: Int,
    val text: String,
    val options: List<ParsedOption>,
    val correctIndex: Int?,
    val issues: Set<QuestionIssue>,
    val page: Int,
    /** Indices into the topic's [ParsedTopic.blocks] with the most related theory. */
    val relatedBlocks: List<Int> = emptyList(),
    val stableKey: String = "",
    /** Literal sentence of the PDF that supports the correct answer, with its page. */
    val explanation: String? = null,
    /** Identifier of the question in the bundled question bank. */
    val sourceId: String? = null,
)

data class ExamPreset(
    val questionCount: Int,
    val passMark: Int?,
    val timeLimitMinutes: Int?,
    /** Questions per topic key (e.g. T1 -> 4). Empty if the source does not define it. */
    val perTopic: Map<String, Int>,
)

data class ParsedDocument(
    val topics: List<ParsedTopic>,
    val questions: List<ParsedQuestion>,
    val examPreset: ExamPreset?,
    val warnings: List<String>,
    val pageCount: Int,
) {
    val validQuestions: List<ParsedQuestion> get() = questions.filter { q -> q.issues.none { it.blocking } }
    val flaggedQuestions: List<ParsedQuestion> get() = questions.filter { it.issues.isNotEmpty() }
}
