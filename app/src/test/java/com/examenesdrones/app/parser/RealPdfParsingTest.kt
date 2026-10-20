package com.examenesdrones.app.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * End-to-end check of the AESA course PDF in the project root together with the bundled
 * question bank (app/src/main/assets/preguntas.json). Skipped when the PDF is absent.
 */
class RealPdfParsingTest {

    companion object {
        private var doc: ParsedDocument? = null
        private var bank: QuestionBank? = null

        @BeforeClass
        @JvmStatic
        fun parseOnce() {
            val pdf = DesktopPdfReader.samplePdf() ?: return
            val b = QuestionBank.decode(bankFile().readText())
            bank = b
            doc = DocumentParser(b).parse(DesktopPdfReader.read(pdf))
        }

        fun bankFile() = File(System.getProperty("samplePdfDir"), "app/src/main/assets/preguntas.json")
    }

    private fun document(): ParsedDocument {
        assumeTrue("Sample PDF not available", doc != null)
        return doc!!
    }

    private fun topic(code: String) = document().topics.first { it.code == code }

    private fun ParsedTopic.texts() = blocks.filter { it.type != BlockType.IMAGE }.map { it.text.text }

    @Test
    fun syllabusFollowsTheModuleIndexes() {
        val d = document()
        assertEquals(
            listOf("1.1", "1.2", "1.3", "1.4", "1.5", "1.R", "2.1", "2.2", "2.3", "2.4", "2.5", "2.6", "2.7", "2.R", "3.1", "3.2", "3.3", "3.R"),
            d.topics.map { it.code },
        )
        assertEquals(d.topics.indices.map { "T${it + 1}" }, d.topics.map { it.key })
        assertEquals(listOf("Concepto Normativo", "Conocimiento General del Vuelo con UAS", "Procedimientos operacionales"), d.topics.mapNotNull { it.module }.distinct())
        assertEquals("Conceptos básicos", topic("1.1").title)
        assertEquals("Procedimientos principales aplicables al uso de UAS", topic("1.5").title)
        assertEquals("Seguro y accidentes e incidentes de UAS", topic("2.7").title)
        assertEquals("Procedimientos operacionales posteriores al vuelo", topic("3.3").title)
        assertEquals("Resumen: Concepto Normativo", topic("1.R").title)
        // Page ranges: index and closing slides are excluded.
        assertEquals(4..7, topic("1.1").pages)
        assertEquals(17..50, topic("1.5").pages)
        assertEquals(51..54, topic("1.R").pages)
        assertEquals(60..69, topic("2.1").pages)
        assertEquals(90..114, topic("2.3").pages)
        assertEquals(115..120, topic("2.4").pages)
        assertEquals(133..139, topic("2.R").pages)
        assertEquals(143..149, topic("3.1").pages)
        assertEquals(157..157, topic("3.3").pages)
        assertEquals(
            listOf("Reglamento Delegado (UE) 2019/945 de la Comisión", "Reglamento de Ejecución (UE) 2019/947 de la Comisión", "Real Decreto de UAS 517/2024"),
            topic("1.3").subtopics,
        )
        assertTrue(d.topics.all { t -> t.blocks.count { it.type != BlockType.IMAGE } >= 5 })
        assertNull(d.examPreset)
        assertTrue("warnings: ${d.warnings}", d.warnings.isEmpty())
    }

    @Test
    fun theoryTextIsCleanAndInReadingOrder() {
        val all = document().topics.flatMap { it.texts() }
        assertTrue(all.none { "FOR-UAS" in it || "DOCUMENTACIÓN PÚBLICA" in it || "Gracias por su atención" in it })
        // A wrapped short sentence end is kept (page 5).
        assertTrue(topic("1.1").texts().any { it.endsWith("siempre se usará el termino UAS.") })
        // Two side-by-side text boxes (page 68) and two columns (page 65).
        val flight = topic("2.1").texts()
        assertTrue(flight.contains("Si la densidad del aire aumenta hace que aumente la sustentación y la resistencia de la aeronave."))
        assertTrue(flight.contains("Si la densidad del aire disminuye, la sustentación y la resistencia de la aeronave disminuyen."))
        assertTrue(flight.any { it.startsWith("Empuje (T): Fuerza necesaria para que la aeronave se mueva a través de una masa de aire.") })
        // Slide labels become headings; symbol-font bullets become list items.
        val open = topic("1.5")
        assertTrue(open.blocks.any { it.type == BlockType.HEADING && it.text.text == "Categoría «abierta» – Subcategoría A1" })
        assertTrue(open.blocks.any { it.type == BlockType.HEADING && it.text.text == "CONDICIONES PARTICULARES DE OPERACIÓN" })
        assertTrue(topic("2.6").blocks.any { it.type == BlockType.BULLET && it.text.text == "Usar únicamente los cargadores específicos." })
    }

    @Test
    fun everyBankQuestionIsUsableAndCitesItsPage() {
        val d = document()
        assertEquals(bank!!.questions.size, d.questions.size)
        assertTrue("bank too small: ${d.questions.size}", d.questions.size >= 250)
        val blocking = d.questions.filter { q -> q.issues.any { it.blocking } }
        assertTrue("Blocking issues: " + blocking.take(5).joinToString { "${it.sourceId} ${it.issues}" }, blocking.isEmpty())
        assertTrue(d.questions.none { it.issues.isNotEmpty() })
        assertEquals(d.questions.size, d.questions.map { it.stableKey }.toSet().size)
        val topics = d.topics.associateBy { it.key }
        for (q in d.questions) {
            val t = topics.getValue(q.topicKey)
            assertTrue("${q.sourceId} page ${q.page} outside ${t.pages}", q.page in t.pages)
            assertEquals(3, q.options.size)
            assertNotNull(q.correctIndex)
            // The justification is text of the PDF itself, found on the cited page.
            val quote = q.explanation!!.removePrefix("«").substringBeforeLast("» (pág.")
            val pageText = t.blocks.filter { it.page == q.page }.joinToString(" ") { it.text.text }
            assertNotNull(q.sourceId, TextNormalizer.findLiteral(pageText, quote))
            assertTrue("${q.sourceId} has no related theory", q.relatedBlocks.isNotEmpty())
        }
    }

    @Test
    fun everyContentTopicHasQuestionsAndAnswersAreBalanced() {
        val d = document()
        val perTopic = d.questions.groupingBy { it.topicKey }.eachCount()
        d.topics.filter { !it.code!!.endsWith(".R") }.forEach { assertTrue("${it.title} has no questions", (perTopic[it.key] ?: 0) >= 3) }
        // The correct option is not always in the same position.
        val positions = d.questions.groupingBy { it.correctIndex }.eachCount()
        assertEquals(setOf(0, 1, 2), positions.keys)
        assertTrue(positions.values.all { it > d.questions.size / 5 })
    }

    @Test
    fun knownAnswersMatchTheSyllabus() {
        val d = document()
        fun answerOf(prefix: String) = d.questions.first { it.text.startsWith(prefix) }.let { it.options[it.correctIndex!!].text }
        assertEquals("AESA", answerOf("¿Quién tiene la potestad sancionadora"))
        assertEquals("120 m del punto más próximo de la superficie", answerOf("En categoría «abierta», ¿cuál es como norma general la altura máxima"))
        assertEquals("16 años", answerOf("¿Cuál es la edad mínima para darse de alta como operador"))
        assertEquals("Troposfera", answerOf("¿En qué capa de la atmósfera vuelan los UAS?"))
        assertEquals("Activar el modo manual y aterrizar la aeronave", answerOf("Si se pierde o degrada la señal GNSS"))
        val potestad = d.questions.first { it.text.startsWith("¿Quién tiene la potestad sancionadora") }
        assertEquals("«En caso de incumplimiento de las normas de aviación civil en territorio nacional, AESA es quien tiene la potestad sancionadora.» (pág. 7)", potestad.explanation)
    }
}
