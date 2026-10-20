package com.examenesdrones.app.parser

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import java.io.File

/**
 * Test-only twin of the app's PdfBox-Android extractor, built on desktop Apache PDFBox
 * (identical API) so the full glyph -> line -> document pipeline runs on the JVM.
 */
object DesktopPdfReader {

    private class Collector : PDFTextStripper() {
        val glyphs = ArrayList<Glyph>()
        override fun processTextPosition(text: TextPosition) {
            val font = text.font
            val bold = FontStyle.isBold(font?.name, font?.fontDescriptor?.fontWeight, font?.fontDescriptor?.isForceBold == true)
            glyphs += Glyph(currentPageNo, text.unicode, text.xDirAdj, text.yDirAdj, text.widthDirAdj, text.fontSizeInPt, bold)
        }
    }

    fun read(file: File): ParserInput = PDDocument.load(file).use { doc ->
        val collector = Collector()
        collector.getText(doc)
        val widths = (1..doc.numberOfPages).associateWith { doc.getPage(it - 1).mediaBox.width }
        ParserInput(LineAssembler.assemble(collector.glyphs, widths), pageCount = doc.numberOfPages)
    }

    fun samplePdf(): File? {
        val dir = System.getProperty("samplePdfDir") ?: return null
        return File(dir).listFiles { f -> f.extension.equals("pdf", true) }?.firstOrNull()
    }
}
