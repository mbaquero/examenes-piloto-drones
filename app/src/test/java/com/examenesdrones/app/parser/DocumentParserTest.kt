package com.examenesdrones.app.parser

import com.examenesdrones.app.SlideDeck
import com.examenesdrones.app.TestContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentParserTest {

    private fun parse(deck: SlideDeck, bank: QuestionBank? = null) = DocumentParser(bank).parse(deck.input())

    private fun ParsedTopic.texts() = blocks.filter { it.type != BlockType.IMAGE }.map { it.text.text }

    @Test
    fun modulesIndexAndTopicsAreRecognized() {
        val doc = parse(TestContent.deck())
        assertEquals(listOf("T1", "T2"), doc.topics.map { it.key })
        val t1 = doc.topics[0]
        assertEquals("Funcionamiento del UAS", t1.title)
        assertEquals("1.1", t1.code)
        assertEquals("Módulo de prueba", t1.module)
        assertEquals(listOf("Hélices y motores"), t1.subtopics)
        assertEquals(3..3, t1.pages)
        assertEquals("Normativa básica", doc.topics[1].title)
        assertEquals(4..4, doc.topics[1].pages)
        // Footer, breadcrumb, cover, index, closing slide and page numbers are not theory.
        val all = doc.topics.flatMap { it.texts() }
        assertFalse(all.any { "FOR-UAS" in it || "Índice" in it || "Gracias" in it || "Versión" in it || it == "3" })
        assertEquals(BlockType.HEADING, t1.blocks[0].type)
        assertEquals(1, t1.blocks[0].level)
        assertEquals(BlockType.HEADING, t1.blocks[1].type)
        assertEquals(2, t1.blocks[1].level)
        assertEquals(TestContent.SENTENCE_T1, t1.blocks[2].text.text)
        // Bold key concepts are preserved.
        assertEquals("hélices", t1.blocks[2].text.text.substring(t1.blocks[2].text.boldRanges().single()))
        // No exam format is invented: the PDF does not define one.
        assertNull(doc.examPreset)
    }

    @Test
    fun pagesFollowTheBestMatchingIndexEntryInOrder() {
        val deck = SlideDeck().apply {
            page { line("Curso", size = 36f, at = 400f); line("Versión 9 (Julio 2024)", size = 12f, at = 524f) }
            page("Curso Índice") {
                line("1) Principios de vuelo", size = 27f, left = 46f)
                line("a) Efecto de las condiciones ambientales en el vuelo de los UAS", size = 21f, left = 80f)
                line("2) Conocimiento general del UAS", size = 27f, left = 46f)
                line("a) Principios de mando y control", size = 21f, left = 80f)
            }
            page("Curso Principios de vuelo") { title("Fuerzas"); line("Texto sobre fuerzas del vuelo.") }
            // Plural/singular differences still match the subsection of topic 1, not topic 2.
            page("Efectos de las condiciones ambientales en el vuelo del UAS") { line("Texto sobre la densidad del aire.") }
            page("Principios de mando y control") { line("Texto sobre la estación de control.") }
            // A page whose header matches nothing stays in the current topic.
            page("Notificación de sucesos") { line("Texto sobre sucesos.") }
            page("Curso Resumen") { title("RECUERDA"); line("Resumen del curso.") }
            page { line("Gracias por su atención", size = 27f, at = 200f) }
        }
        val doc = parse(deck)
        assertEquals(listOf("1.1", "1.2", "1.R"), doc.topics.map { it.code })
        assertEquals(3..4, doc.topics[0].pages)
        assertEquals(5..6, doc.topics[1].pages)
        assertEquals("Resumen: Curso", doc.topics[2].title)
        assertTrue(doc.topics[1].texts().contains("Texto sobre sucesos."))
    }

    @Test
    fun wrappedIndexEntriesAreJoined() {
        val deck = SlideDeck().apply {
            page { line("Curso", size = 36f, at = 400f); line("Versión 9 (Julio 2024)", size = 12f, at = 524f) }
            page("Índice") {
                line("1) Espacio aéreo", size = 26f, left = 34f)
                line("a) Condiciones operacionales aplicables a las zonas geográficas de", size = 21f, left = 68f)
                line("los UAS", size = 21f, left = 68f)
                line("I. Densidad del aire", size = 20f, left = 104f)
                line("b) U-Space", size = 21f, left = 68f)
            }
            page("Espacio aéreo") { line("Contenido.") }
        }
        val t = parse(deck).topics.single()
        assertEquals(listOf("Condiciones operacionales aplicables a las zonas geográficas de los UAS", "U-Space"), t.subtopics)
    }

    @Test
    fun wrappedLinesFormParagraphsAndShortSentenceEndsAreKept() {
        val deck = SlideDeck().apply {
            page("Tema") {
                title("Conceptos básicos")
                line("Por lo tanto, los términos RPAS, dron o aeromodelo están englobados dentro del concepto")
                line("de UAS. En los Reglamentos siempre se usará el termino")
                line("UAS.", right = 121f)
                gap()
                line("• Primer punto de la lista que continúa")
                line("en la línea siguiente.", left = 109f)
                line("• Segundo punto.")
                gap()
                // Isolated diagram labels are dropped.
                line("15 m", left = 300f, right = 330f)
                line("VLOS", left = 400f, right = 440f)
            }
        }
        val t = parse(deck).topics.single()
        assertEquals(
            listOf(
                "Conceptos básicos",
                "Por lo tanto, los términos RPAS, dron o aeromodelo están englobados dentro del concepto de UAS. En los Reglamentos siempre se usará el termino UAS.",
                "Primer punto de la lista que continúa en la línea siguiente.",
                "Segundo punto.",
            ),
            t.texts(),
        )
        assertEquals(listOf(BlockType.HEADING, BlockType.PARAGRAPH, BlockType.BULLET, BlockType.BULLET), t.blocks.map { it.type })
    }

    @Test
    fun sideBySideTextBoxesAreReadColumnByColumn() {
        val deck = SlideDeck().apply {
            page("Tema") {
                title("Densidad")
                // Two text boxes whose baselines are slightly offset, as on the PDF's page 68.
                line("Si la densidad del aire aumenta hace que", left = 67f, right = 358f, at = 293f)
                line("Si la densidad del aire disminuye, la", left = 403f, right = 695f, at = 290f)
                line("aumente la sustentación y la resistencia de", left = 67f, right = 358f, at = 312f)
                line("sustentación y la resistencia de la aeronave", left = 403f, right = 695f, at = 310f)
                line("la aeronave.", left = 67f, right = 146f, at = 331f)
                line("disminuyen.", left = 403f, right = 482f, at = 329f)
            }
        }
        val t = parse(deck).topics.single()
        assertEquals(
            listOf(
                "Densidad",
                "Si la densidad del aire aumenta hace que aumente la sustentación y la resistencia de la aeronave.",
                "Si la densidad del aire disminuye, la sustentación y la resistencia de la aeronave disminuyen.",
            ),
            t.texts(),
        )
    }

    @Test
    fun repeatedSlideTitlesAreShownOnce() {
        val deck = SlideDeck().apply {
            page("Tema") { title("Definiciones importantes"); line("Primera definición.") }
            page("Tema") { title("Definiciones importantes"); line("Segunda definición.") }
        }
        val t = parse(deck).topics.single()
        assertEquals(listOf("Definiciones importantes", "Primera definición.", "Segunda definición."), t.texts())
    }

    @Test
    fun allCapsBulletLabelsBecomeHeadings() {
        val deck = SlideDeck().apply {
            page("Tema") {
                title("Subcategoría A1")
                line("• CONDICIONES PARTICULARES DE OPERACIÓN")
                line("Además de las condiciones generales, debe cumplirse lo siguiente:")
            }
        }
        val t = parse(deck).topics.single()
        assertEquals(BlockType.HEADING, t.blocks[1].type)
        assertEquals("CONDICIONES PARTICULARES DE OPERACIÓN", t.blocks[1].text.text)
        assertEquals(BlockType.PARAGRAPH, t.blocks[2].type)
    }

    @Test
    fun bankQuestionsAreLinkedToTheTopicOfTheirPageWithLiteralJustification() {
        val doc = parse(TestContent.deck(), TestContent.bank(perTopic = 2))
        assertEquals(4, doc.questions.size)
        assertTrue(doc.questions.none { it.issues.isNotEmpty() })
        assertEquals(listOf("T1", "T1", "T2", "T2"), doc.questions.map { it.topicKey })
        assertEquals(listOf(1, 2, 1, 2), doc.questions.map { it.numberInTopic })
        val q = doc.questions.first()
        assertEquals(1, q.correctIndex)
        assertEquals(listOf("A", "B", "C"), q.options.map { it.label })
        assertEquals("«${TestContent.SENTENCE_T1}» (pág. 3)", q.explanation)
        // The related theory is the block that contains the cited sentence.
        assertEquals(listOf(2), q.relatedBlocks)
        assertEquals(doc.questions.size, doc.questions.map { it.stableKey }.toSet().size)
    }

    @Test
    fun citationsThatAreNotOnThePageAreFlaggedAndNeverGuessed() {
        val good = TestContent.bank(perTopic = 1).questions.first()
        val bank = QuestionBank(
            questions = listOf(
                good.copy(id = "wrong-page", question = "Pregunta de otra página", page = 4), // the sentence is on page 3
                good.copy(id = "invented", question = "Pregunta inventada", source = "Los UAS pueden volar a 500 m."),
                good.copy(id = "bad-answer", question = "Otra pregunta", answer = 5),
                good.copy(id = "two-options", question = "Pregunta corta", options = listOf("Sí", "No")),
                good.copy(id = "no-topic", question = "Pregunta sin página", page = 99),
                good.copy(id = "ok"),
                good.copy(id = "dup"),
            ),
        )
        val issues = parse(TestContent.deck(), bank).questions.associate { it.sourceId to it.issues }
        assertEquals(setOf(QuestionIssue.SOURCE_NOT_FOUND), issues["wrong-page"])
        assertEquals(setOf(QuestionIssue.SOURCE_NOT_FOUND), issues["invented"])
        assertEquals(setOf(QuestionIssue.NO_CORRECT_ANSWER), issues["bad-answer"])
        assertEquals(setOf(QuestionIssue.TOO_FEW_OPTIONS), issues["two-options"])
        assertTrue(QuestionIssue.UNKNOWN_TOPIC in issues.getValue("no-topic"))
        assertEquals(emptySet<QuestionIssue>(), issues["ok"])
        assertEquals(setOf(QuestionIssue.DUPLICATE), issues["dup"])
        assertTrue(QuestionIssue.SOURCE_NOT_FOUND.blocking && QuestionIssue.NO_CORRECT_ANSWER.blocking)
        assertFalse(QuestionIssue.DUPLICATE.blocking)
        val doc = parse(TestContent.deck(), bank)
        assertTrue(doc.warnings.any { "invented" in it })
        assertNull(doc.questions.first { it.sourceId == "invented" }.explanation)
    }

    @Test
    fun questionBankJsonIsDecoded() {
        val bank = QuestionBank.decode(
            """{"version":1,"source":"x","questions":[{"id":"a","page":3,"question":"¿Q?","options":["1","2","3"],"answer":2,"source":"s","extra":true}]}"""
        )
        assertEquals("a", bank.questions.single().id)
        assertEquals(2, bank.questions.single().answer)
    }

    @Test
    fun literalSpansAreTakenFromTheSourceText() {
        val page = "Estación de control o emisora de radio denominada GCS (Ground Control System), que sirve para controlar."
        assertEquals("GCS (Ground Control System)", TextNormalizer.findLiteral(page, "gcs (ground control system)"))
        assertEquals("emisora de radio", TextNormalizer.findLiteral(page, "EMISORA  DE   RADIO"))
        assertEquals("Estación de control", TextNormalizer.findLiteral(page, "Estacion de control"))
        assertNull(TextNormalizer.findLiteral(page, "satélite"))
        assertEquals("controlar.", TextNormalizer.findLiteral(page, "controlar."))
    }

    @Test
    fun titleSimilarityToleratesSmallWordingChanges() {
        assertTrue(TitleSimilarity.score("Seguro y accidentes e incidentes de UAS", "Seguros y accidentes e incidentes con UAS") > 0.9)
        assertTrue(TitleSimilarity.score("Principios de mando y control", "Principios de vuelo") < 0.6)
        assertEquals(0.0, TitleSimilarity.score("", "Algo"), 0.0)
    }

    @Test
    fun symbolFontBulletsAreNormalized() {
        assertEquals("•", TextNormalizer.normalizeGlyph(""))
        assertEquals("", TextNormalizer.normalizeGlyph(""))
        assertEquals("fi", TextNormalizer.normalizeGlyph("ﬁ"))
    }

    @Test
    fun lineAssemblerSplitsSideBySideBoxesButKeepsBulletsAndWordsTogether() {
        fun glyphs(text: String, x0: Float, gapAfter: Map<Int, Float> = emptyMap()): List<Glyph> {
            var x = x0
            return text.mapIndexed { i, c ->
                Glyph(1, c.toString(), x, 100f, 7f, 14f, false).also { x += 7f + (gapAfter[i] ?: 0f) }
            }
        }
        // Normal word spacing (and a justified space of 1.5 x font size) stays in one line.
        val oneLine = glyphs("Una línea justificada", 50f, mapOf(3 to 21f))
        assertEquals(listOf("Una línea justificada"), LineAssembler.assemble(oneLine, mapOf(1 to 720f)).map { it.text })
        // A bullet followed by a tab-like gap stays with its text.
        val bullet = glyphs("•Texto", 50f, mapOf(0 to 40f))
        assertEquals(listOf("• Texto"), LineAssembler.assemble(bullet, mapOf(1 to 720f)).map { it.text })
        // Two text boxes on the same baseline are separate fragments.
        val boxes = glyphs("Izquierda", 50f) + glyphs("Derecha", 400f)
        assertEquals(listOf("Izquierda", "Derecha"), LineAssembler.assemble(boxes, mapOf(1 to 720f)).map { it.text })
    }
}
