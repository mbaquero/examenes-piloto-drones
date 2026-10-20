package com.examenesdrones.app

import android.content.Context
import com.examenesdrones.app.data.ImportPreview
import com.examenesdrones.app.parser.BankQuestion
import com.examenesdrones.app.parser.DocumentParser
import com.examenesdrones.app.parser.ParserInput
import com.examenesdrones.app.parser.QuestionBank
import com.examenesdrones.app.parser.StyledLine
import com.examenesdrones.app.pdf.ExtractedPdf
import java.io.File

/**
 * Builds styled lines laid out like the AESA slide deck: a footer and a breadcrumb at the top of
 * each page, a large slide title, body text and the page number at the bottom.
 */
class SlideDeck {
    val lines = ArrayList<StyledLine>()
    private var pageNo = 0

    inner class Page(val number: Int) {
        var y = 96f

        fun line(markup: String, size: Float = 15f, left: Float = 89f, right: Float = 676f, at: Float? = null) {
            val baseline = at ?: y
            lines += StyledLine.markup(markup, page = number, pageWidth = WIDTH, left = left, right = right, fontSize = size, baseline = baseline)
            if (at == null) y += size * 1.27f
        }

        fun title(text: String) = line(text, size = 27f, left = 66f).also { y += 8f }
        fun subtitle(text: String) = line(text, size = 21f, left = 91f)
        fun gap() { y += 20f }
    }

    fun page(header: String? = null, content: Page.() -> Unit = {}) {
        pageNo++
        lines += StyledLine.plain("FOR-UAS-P01-DT01_Ed.09 DOCUMENTACIÓN PÚBLICA", page = pageNo, pageWidth = WIDTH, left = 62f, right = 352f, fontSize = 12f, baseline = 15f)
        if (header != null) lines += StyledLine.plain(header, page = pageNo, pageWidth = WIDTH, left = 10f, right = 698f, fontSize = 18f, baseline = 26f)
        Page(pageNo).content()
        lines += StyledLine.plain(pageNo.toString(), page = pageNo, pageWidth = WIDTH, left = 11f, right = 17f, fontSize = 12f, baseline = 534f)
    }

    fun input() = ParserInput(lines)

    companion object {
        const val WIDTH = 720f
    }
}

/** Synthetic study material for tests only; never shipped with the app. */
object TestContent {
    const val SENTENCE_T1 = "Las hélices generan la sustentación del UAS."
    const val SENTENCE_T2 = "La altura máxima en categoría abierta es de 120 m."

    /** A one-module course with two topics (pages 3 and 4) and [perTopic] questions each; option B is always correct. */
    fun deck(): SlideDeck = SlideDeck().apply {
        page { line("Módulo de prueba", size = 36f, left = 204f, at = 445f); line("Versión 1 (Enero 2024)", size = 12f, left = 469f, at = 524f) }
        page("Módulo de prueba Índice") {
            line("1) Funcionamiento del UAS", size = 27f, left = 46f)
            line("a) Hélices y motores", size = 21f, left = 80f)
            line("2) Normativa básica", size = 27f, left = 46f)
        }
        page("Módulo de prueba Funcionamiento del UAS") {
            title("Funcionamiento del UAS")
            subtitle("Hélices y motores")
            line("Las **hélices** generan la sustentación del UAS.")
        }
        page("Módulo de prueba Normativa básica") {
            title("Normativa básica")
            line("La altura máxima en categoría abierta es de 120 m.")
        }
        page { line("Gracias por su atención", size = 27f, left = 200f, at = 250f) }
    }

    fun bank(perTopic: Int = 6, variant: String = ""): QuestionBank = QuestionBank(
        questions = (1..2).flatMap { t ->
            (1..perTopic).map { n ->
                BankQuestion(
                    id = "t$t-$n", page = 2 + t,
                    question = "Pregunta $n del tema $t $variant sobre las hélices y la altura",
                    options = listOf("Respuesta uno $t$n", "Respuesta dos $t$n", "Respuesta tres $t$n"), answer = 1,
                    source = if (t == 1) SENTENCE_T1 else SENTENCE_T2,
                )
            }
        },
    )

    fun preview(context: Context, perTopic: Int = 6, variant: String = "", hash: String = "hash-$variant"): ImportPreview {
        val input = deck().input()
        val doc = DocumentParser(bank(perTopic, variant)).parse(input)
        val dir = File(context.filesDir, "images/test").apply { mkdirs() }
        return ImportPreview(ExtractedPdf(input, hash, "temario$variant.pdf", dir), doc)
    }
}
