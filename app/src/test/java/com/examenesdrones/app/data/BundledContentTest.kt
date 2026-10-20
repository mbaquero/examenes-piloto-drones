package com.examenesdrones.app.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.examenesdrones.app.ExamApp
import com.examenesdrones.app.domain.ExamConfig
import com.examenesdrones.app.parser.QuestionBank
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The study guide and question bank bundled in the APK are prepared on first launch and only once. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BundledContentTest {

    @Test
    fun bundledContentIsLoadedOnceAndKeepsHistory() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<ExamApp>()
        val c = app.container
        val bankSize = app.assets.open(BundledContentLoader.BANK_ASSET).use { QuestionBank.decode(it.readBytes().decodeToString()) }.questions.size
        c.bundledContent.ensureLoaded()
        assertEquals(ContentState.Ready, c.bundledContent.state.value)
        assertEquals(bankSize, c.content.usableQuestions().size)
        assertEquals(18, c.content.topicsOnce().size)
        // The PDF defines no exam format, so none is presented as official.
        assertNull(c.content.preset())
        val q = c.content.usableQuestions().first()
        assertTrue(q.explanation!!.startsWith("«"))

        val examId = c.study.startExam(ExamConfig(questionCount = 5), emptyMap())
        c.study.submitExam(examId, 10)
        val docId = c.content.activeDocument.first()!!.id

        // A later launch with the same bundled content does not reprocess or touch history.
        c.bundledContent.ensureLoaded()
        assertEquals(docId, c.content.activeDocument.first()!!.id)
        assertEquals(1, c.study.submittedExams.first().size)
        assertEquals(bankSize, c.content.usableQuestions().size)
    }
}
