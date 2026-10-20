package com.examenesdrones.app.pdf

import android.content.Context
import android.graphics.Bitmap
import com.examenesdrones.app.parser.FontStyle
import com.examenesdrones.app.parser.Glyph
import com.examenesdrones.app.parser.LineAssembler
import com.examenesdrones.app.parser.PageImage
import com.examenesdrones.app.parser.ParserInput
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.contentstream.PDFStreamEngine
import com.tom_roush.pdfbox.contentstream.operator.DrawObject
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.contentstream.operator.state.Concatenate
import com.tom_roush.pdfbox.contentstream.operator.state.Restore
import com.tom_roush.pdfbox.contentstream.operator.state.Save
import com.tom_roush.pdfbox.contentstream.operator.state.SetGraphicsStateParameters
import com.tom_roush.pdfbox.contentstream.operator.state.SetMatrix
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class PdfImportException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Result of reading a PDF: styled lines and images ready for [com.examenesdrones.app.parser.DocumentParser]. */
data class ExtractedPdf(val input: ParserInput, val fileHash: String, val fileName: String, val imageDir: File)

/** Extracts glyphs (with bold information) and positioned images from the bundled study PDF. */
class PdfExtractor(private val context: Context) {

    fun interface Progress {
        fun update(message: String, fraction: Float)
    }

    fun extract(file: File, fileName: String, progress: Progress): ExtractedPdf {
        PDFBoxResourceLoader.init(context.applicationContext)
        val hash = file.inputStream().use { com.examenesdrones.app.parser.TextNormalizer.sha256(it.readBytes()) }
        val imageDir = File(context.filesDir, "images/${hash.take(16)}").apply { mkdirs() }

        val doc = try {
            PDDocument.load(file)
        } catch (e: InvalidPasswordException) {
            throw PdfImportException("El PDF está protegido con contraseña y no se puede leer.", e)
        } catch (e: IOException) {
            throw PdfImportException("El PDF del temario está dañado.", e)
        }

        doc.use { pdf ->
            val pageCount = pdf.numberOfPages
            if (pageCount == 0) throw PdfImportException("El PDF no contiene páginas.")
            progress.update("Leyendo texto…", 0.05f)

            val collector = GlyphCollector()
            collector.getText(pdf)
            val widths = (1..pageCount).associateWith { pdf.getPage(it - 1).mediaBox.width }

            progress.update("Extrayendo imágenes…", 0.5f)
            val images = ArrayList<PageImage>()
            for (p in 1..pageCount) {
                try {
                    images += ImageCollector(pdf.getPage(p - 1), p, imageDir).collect()
                } catch (e: Exception) {
                    // Images are optional; text remains usable.
                }
                progress.update("Extrayendo imágenes…", 0.5f + 0.4f * p / pageCount)
            }
            val lines = LineAssembler.assemble(collector.glyphs, widths)
            if (lines.none { !it.isBlank }) throw PdfImportException("No se ha podido extraer texto del PDF.")
            return ExtractedPdf(ParserInput(lines, filterRepeatedImages(images), pageCount = pageCount), hash, fileName, imageDir)
        }
    }

    /** Logos or decorations repeated on many pages are not content. */
    private fun filterRepeatedImages(images: List<PageImage>): List<PageImage> {
        val pagesById = images.groupBy { it.id.substringBefore('@') }.mapValues { (_, v) -> v.map { it.page }.toSet().size }
        return images.filter { (pagesById[it.id.substringBefore('@')] ?: 0) <= 3 }
            .map { it.copy(id = it.id.substringAfter('@')) }
    }

    private class GlyphCollector : PDFTextStripper() {
        val glyphs = ArrayList<Glyph>()

        override fun processTextPosition(text: TextPosition) {
            val font = text.font
            val descriptor = font?.fontDescriptor
            val bold = FontStyle.isBold(font?.name, descriptor?.fontWeight, descriptor?.isForceBold == true)
            glyphs += Glyph(currentPageNo, text.unicode ?: return, text.xDirAdj, text.yDirAdj, text.widthDirAdj, text.fontSizeInPt, bold)
        }
    }

    /** Records where images are drawn on a page and saves them as JPEG files. */
    private class ImageCollector(private val page: PDPage, private val pageNo: Int, private val dir: File) : PDFStreamEngine() {
        private val found = ArrayList<PageImage>()
        private var counter = 0

        init {
            addOperator(Concatenate())
            addOperator(DrawObject())
            addOperator(SetGraphicsStateParameters())
            addOperator(Save())
            addOperator(Restore())
            addOperator(SetMatrix())
        }

        fun collect(): List<PageImage> {
            processPage(page)
            return found
        }

        override fun processOperator(operator: Operator, operands: MutableList<COSBase>) {
            if (operator.name == "Do") {
                val name = operands.firstOrNull() as? COSName
                val xobject = name?.let { resources?.getXObject(it) }
                if (xobject is PDImageXObject) {
                    handleImage(xobject)
                    return
                }
            }
            super.processOperator(operator, operands)
        }

        private fun handleImage(image: PDImageXObject) {
            val ctm = graphicsState.currentTransformationMatrix
            val drawnWidth = ctm.scalingFactorX
            val drawnHeight = ctm.scalingFactorY
            if (drawnWidth < 40 || drawnHeight < 40) return // icons, bullets, decorations
            val pageHeight = page.mediaBox.height
            val top = pageHeight - (ctm.translateY + drawnHeight)
            val bitmap = try { image.image } catch (e: Exception) { null } ?: return
            val contentId = "${image.cosObject.length}x${image.width}x${image.height}"
            val fileName = "p${pageNo}_${counter++}.jpg"
            FileOutputStream(File(dir, fileName)).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            bitmap.recycle()
            found += PageImage(pageNo, top, "$contentId@${dir.name}/$fileName", image.width, image.height)
        }
    }
}
