package com.examenesdrones.app.ui.navigation

object Routes {
    const val HOME = "home"
    const val TOPICS = "topics"
    const val PRACTICE_SETUP = "practice"
    const val EXAM_SETUP = "exam"
    const val PROGRESS = "progress"
    const val SETTINGS = "settings"

    const val READER = "reader/{topicKey}?block={block}"
    fun reader(topicKey: String, block: Int = -1) = "reader/$topicKey?block=$block"

    const val PRACTICE_SETUP_WITH = "practice?topics={topics}"
    fun practiceSetup(topics: List<String>) = "practice?topics=${topics.joinToString(",")}"

    const val PRACTICE_SESSION = "practice/session/{id}"
    fun practiceSession(id: Long) = "practice/session/$id"

    const val PRACTICE_SUMMARY = "practice/summary/{id}"
    fun practiceSummary(id: Long) = "practice/summary/$id"

    const val EXAM_SESSION = "exam/session/{id}"
    fun examSession(id: Long) = "exam/session/$id"

    const val EXAM_RESULT = "exam/result/{id}"
    fun examResult(id: Long) = "exam/result/$id"

    const val EXAM_REVIEW = "exam/review/{id}?filter={filter}"
    fun examReview(id: Long, filter: String = "ALL") = "exam/review/$id?filter=$filter"
}
