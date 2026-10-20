package com.examenesdrones.app.data

import android.content.Context
import com.examenesdrones.app.parser.DocumentParser
import com.examenesdrones.app.parser.QuestionBank
import com.examenesdrones.app.parser.TextNormalizer
import com.examenesdrones.app.pdf.PdfExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

sealed interface ContentState {
    data class Preparing(val message: String, val fraction: Float) : ContentState
    data object Ready : ContentState
    data class Failed(val message: String) : ContentState
}

/**
 * The study guide is built into the app: assets/temario.pdf (the AESA course) and
 * assets/preguntas.json (questions written from it, each citing its source sentence).
 * On first launch, or when an app update ships a different PDF or question bank, both are
 * processed and stored. User history is never touched.
 */
class BundledContentLoader(private val context: Context, private val content: ContentRepository) {
    private val _state = MutableStateFlow<ContentState>(ContentState.Preparing("Preparando el temario…", 0f))
    val state: StateFlow<ContentState> = _state.asStateFlow()
    private val mutex = Mutex()

    suspend fun ensureLoaded() = mutex.withLock {
        withContext(Dispatchers.Default) {
            try {
                val pdf = copyAsset()
                val bankText = context.assets.open(BANK_ASSET).use { it.readBytes() }.decodeToString()
                val pdfHash = pdf.inputStream().use { TextNormalizer.sha256(it.readBytes()) }
                val hash = TextNormalizer.sha256(pdfHash + TextNormalizer.sha256(bankText))
                val active = content.activeDocument.first()
                if (active?.fileHash != hash) {
                    val extracted = PdfExtractor(context).extract(pdf, ASSET_NAME) { msg, f ->
                        _state.value = ContentState.Preparing(msg, f)
                    }
                    _state.value = ContentState.Preparing("Organizando temas y preguntas…", 0.95f)
                    val parsed = DocumentParser(QuestionBank.decode(bankText)).parse(extracted.input)
                    val preview = ImportPreview(extracted.copy(fileHash = hash), parsed)
                    content.commit(preview, parsed.questions.map { ReviewedQuestion(it) })
                }
                pdf.delete()
                _state.value = ContentState.Ready
            } catch (e: Exception) {
                _state.value = ContentState.Failed(e.message ?: e.javaClass.simpleName)
            } catch (e: OutOfMemoryError) {
                _state.value = ContentState.Failed("Memoria insuficiente para preparar el temario.")
            }
        }
    }

    private fun copyAsset(): File {
        val out = File(context.cacheDir, ASSET_NAME)
        context.assets.open(ASSET_NAME).use { input -> out.outputStream().use { input.copyTo(it) } }
        return out
    }

    companion object {
        const val ASSET_NAME = "temario.pdf"
        const val BANK_ASSET = "preguntas.json"
    }
}
