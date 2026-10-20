package com.examenesdrones.app.pdf

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.examenesdrones.app.parser.BlockType
import com.examenesdrones.app.parser.DesktopPdfReader
import com.examenesdrones.app.parser.DocumentParser
import com.examenesdrones.app.parser.QuestionBank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Runs the on-device extractor (PdfBox-Android) against the sample PDF, when present. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidPdfExtractorTest {

    @Test
    fun androidExtractorProducesTheSameSyllabusAndQuestions() {
        val pdf = DesktopPdfReader.samplePdf()
        assumeTrue("Sample PDF not available", pdf != null)
        val bank = QuestionBank.decode(File(System.getProperty("samplePdfDir"), "app/src/main/assets/preguntas.json").readText())
        val context: Context = ApplicationProvider.getApplicationContext()
        val extracted = PdfExtractor(context).extract(pdf!!, pdf.name) { _, _ -> }
        val doc = DocumentParser(bank).parse(extracted.input)
        assertEquals(bank.questions.size, doc.questions.size)
        assertTrue(doc.questions.none { it.issues.isNotEmpty() })
        // Same topics, text and question links as the desktop pipeline.
        val desktop = DocumentParser(bank).parse(DesktopPdfReader.read(pdf))
        assertEquals(desktop.topics.map { it.code to it.pages }, doc.topics.map { it.code to it.pages })
        fun texts(d: com.examenesdrones.app.parser.ParsedDocument) =
            d.topics.flatMap { t -> t.blocks.filter { it.type != BlockType.IMAGE }.map { it.text.text } }
        val (a, b) = texts(desktop) to texts(doc)
        val firstDiff = a.indices.firstOrNull { it >= b.size || a[it] != b[it] }
        assertEquals("first difference at block $firstDiff: desktop=${firstDiff?.let { a.getOrNull(it) }} android=${firstDiff?.let { b.getOrNull(it) }}", a, b)
        assertEquals(desktop.questions.map { it.stableKey to it.topicKey }, doc.questions.map { it.stableKey to it.topicKey })
        // Slide illustrations are extracted and placed within the theory.
        val images = doc.topics.flatMap { it.blocks }.count { it.imageId != null }
        println("Extracted images: $images, per topic: " + doc.topics.associate { it.code to it.blocks.count { b -> b.imageId != null } })
        assertTrue("images: $images", images > 50)
    }
}
