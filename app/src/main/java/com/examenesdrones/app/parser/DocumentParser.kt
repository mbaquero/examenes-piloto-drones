package com.examenesdrones.app.parser

/**
 * Turns the styled lines of the AESA course PDF (a slide deck) into a structured study guide.
 *
 * The deck is made of course modules. Each module starts with a cover slide ("Versión N (…)")
 * followed by index slides ("Índice") whose entries "1) …" are the module's topics and whose
 * entries "a) …" / "1. …" are their subsections. Content slides carry a breadcrumb in the page
 * header and a slide title; they are assigned, in document order, to the index entry that best
 * matches that text. "Resumen"/"RECUERDA" slides at the end of a module form a summary topic.
 *
 * The PDF contains no questions. Questions come from a separate [QuestionBank] whose entries
 * cite a page and a literal sentence of the PDF; the parser checks that the sentence really is
 * on that page and links each question to the topic and theory blocks of that page. Questions
 * whose source cannot be found are flagged and never used.
 */
class DocumentParser(private val bank: QuestionBank? = null) {

    private class Line(val line: StyledLine) {
        val text get() = line.text
        val page get() = line.page
        val y get() = line.baseline
        val size get() = line.fontSize
        val left get() = line.left
        val right get() = line.right
    }

    private enum class PageKind { COVER, INDEX, CLOSING, CONTENT }

    private class Page(
        val number: Int,
        val header: List<String>,
        val body: List<Line>,
        val images: List<PageImage>,
    ) {
        val bodyText: String get() = body.joinToString(" ") { it.text }
    }

    private class IndexEntry(val number: Int, var title: String, val subtopics: MutableList<String> = ArrayList())

    private class Module(val number: Int, val title: String?) {
        val index = ArrayList<IndexEntry>()
        val pagesByEntry = LinkedHashMap<Int, MutableList<Page>>()
        val summaryPages = ArrayList<Page>()
        val firstPage get() = (pagesByEntry.values.flatten() + summaryPages).minOfOrNull { it.number }
    }

    fun parse(input: ParserInput): ParsedDocument {
        val warnings = ArrayList<String>()
        val linesByPage = input.lines.map { it.trimmed() }.filter { !it.isBlank }.groupBy { it.page }
        val imagesByPage = input.images.groupBy { it.page }
        val pageCount = maxOf(input.pageCount, linesByPage.keys.maxOrNull() ?: 0)

        // --- Pages -> modules -> topics ----------------------------------------------------
        val modules = ArrayList<Module>()
        var module: Module? = null
        var entry = 0 // index position of the current topic within the module
        var inSummary = false

        for (p in 1..pageCount) {
            val page = buildPage(p, linesByPage[p].orEmpty(), imagesByPage[p].orEmpty())
            when (classify(page)) {
                PageKind.COVER -> {
                    val title = page.body.filterNot { COVER_VERSION.matches(it.text) }.joinToString(" ") { it.text }.trim()
                    module = Module(modules.size + 1, title.ifEmpty { null }).also { modules += it }
                    entry = 0
                    inSummary = false
                }
                PageKind.INDEX -> {
                    val m = module ?: Module(modules.size + 1, null).also { modules += it; module = it }
                    parseIndex(page, m.index)
                }
                PageKind.CLOSING -> Unit
                PageKind.CONTENT -> {
                    val m = module ?: Module(modules.size + 1, null).also { modules += it; module = it }
                    val headings = pageHeadings(page, m.title)
                    if (!inSummary && headings.any { h -> TextNormalizer.words(h).let { it.size in 1..2 && it.first() in SUMMARY_WORDS } }) {
                        inSummary = true
                    }
                    if (inSummary) {
                        m.summaryPages += page
                    } else {
                        entry = assignEntry(m.index, entry, headings)
                        m.pagesByEntry.getOrPut(entry) { ArrayList() } += page
                    }
                }
            }
        }

        val topics = ArrayList<ParsedTopic>()
        val pageTopic = HashMap<Int, String>()
        val pageText = HashMap<Int, String>()
        for (m in modules) {
            val moduleTitle = m.title?.let(::tidyTitle)
            val parts = ArrayList<Triple<String, List<String>, List<Page>>>() // code, title+subs, pages
            m.pagesByEntry.toSortedMap().forEach { (i, pages) ->
                val e = m.index.getOrNull(i)
                val code = "${m.number}.${e?.number ?: (i + 1)}"
                val title = e?.title ?: moduleTitle ?: "Módulo ${m.number}"
                parts += Triple(code, listOf(title) + e?.subtopics.orEmpty(), pages)
            }
            if (m.summaryPages.isNotEmpty()) {
                parts += Triple("${m.number}.R", listOf("Resumen" + (moduleTitle?.let { ": $it" } ?: "")), m.summaryPages)
            }
            for ((code, names, pages) in parts) {
                val key = "T${topics.size + 1}"
                val blocks = ArrayList<ContentBlock>()
                var previous: Set<String> = emptySet()
                for (page in pages) {
                    val pageBlocks = buildBlocks(page)
                    val fresh = pageBlocks.filter { b -> b.type == BlockType.IMAGE || TextNormalizer.comparable(b.text.text) !in previous }
                    blocks += fresh
                    previous = pageBlocks.filter { it.type != BlockType.IMAGE }.map { TextNormalizer.comparable(it.text.text) }.toSet()
                    pageTopic[page.number] = key
                    pageText[page.number] = pageBlocks.joinToString(" ") { it.text.text }
                }
                topics += ParsedTopic(
                    key = key, number = topics.size + 1, code = code, title = tidyTitle(names.first()),
                    subtopics = names.drop(1).map(::tidyTitle), blocks = blocks, module = moduleTitle,
                    pages = pages.first().number..pages.last().number,
                )
            }
        }
        if (topics.isEmpty()) warnings += "No se ha reconocido ningún tema en el PDF."
        topics.filter { it.blocks.none { b -> b.type != BlockType.IMAGE } }.forEach {
            warnings += "El tema \"${it.title}\" no tiene texto extraíble."
        }

        // --- Questions from the question bank ---------------------------------------------
        var questions = bank?.let { buildQuestions(it, topics, pageTopic, pageText) }.orEmpty()
        questions = markDuplicatesAndKeys(questions)
        questions.filter { QuestionIssue.SOURCE_NOT_FOUND in it.issues }.forEach {
            warnings += "La pregunta ${it.sourceId} cita un texto que no aparece en la página ${it.page}."
        }
        topics.filter { t -> questions.none { it.topicKey == t.key } && !t.code.orEmpty().endsWith(".R") }.forEach {
            warnings += "El tema \"${it.title}\" no tiene preguntas asociadas."
        }

        return ParsedDocument(topics, questions, examPreset = null, warnings = warnings, pageCount = pageCount)
    }

    // ---------------------------------------------------------------------------------------
    // Page structure
    // ---------------------------------------------------------------------------------------

    private fun buildPage(number: Int, lines: List<StyledLine>, images: List<PageImage>): Page {
        val header = ArrayList<String>()
        val body = ArrayList<Line>()
        for (l in lines.sortedWith(compareBy({ it.baseline }, { it.left }))) {
            when {
                FOOTER.containsMatchIn(l.text) -> Unit
                l.baseline < HEADER_LIMIT -> header += l.text
                l.text == number.toString() -> Unit // page number
                else -> body += Line(l)
            }
        }
        return Page(number, header, body, images)
    }

    private fun classify(page: Page): PageKind {
        val all = page.header + page.body.map { it.text }
        return when {
            page.body.any { COVER_VERSION.matches(it.text) } -> PageKind.COVER
            all.any { CLOSING.containsMatchIn(it) } -> PageKind.CLOSING
            page.header.any { INDEX_WORD.containsMatchIn(it) } && page.body.count { TOP_ENTRY.matches(it.text) || SUB_ENTRY.matches(it.text) } >= 2 -> PageKind.INDEX
            else -> PageKind.CONTENT
        }
    }

    private fun parseIndex(page: Page, index: MutableList<IndexEntry>) {
        var lastSub = false
        for (l in page.body) {
            val t = l.text
            val top = TOP_ENTRY.matchEntire(t)
            val sub = SUB_ENTRY.matchEntire(t)
            when {
                top != null -> {
                    val n = top.groupValues[1].toInt()
                    if (index.none { it.number == n }) index += IndexEntry(n, top.groupValues[2].trim())
                    lastSub = false
                }
                sub != null && index.isNotEmpty() -> {
                    index.last().subtopics += sub.groupValues[1].trim()
                    lastSub = true
                }
                ROMAN_ENTRY.matches(t) -> lastSub = false // third level: not listed separately
                index.isNotEmpty() -> {
                    // A wrapped index entry continues on the next line.
                    val e = index.last()
                    if (lastSub && e.subtopics.isNotEmpty()) e.subtopics[e.subtopics.lastIndex] = e.subtopics.last() + " " + t
                    else if (!lastSub) e.title = e.title + " " + t
                }
            }
        }
    }

    /** Header breadcrumb (without the module name) and the slide title, used to place the page. */
    private fun pageHeadings(page: Page, moduleTitle: String?): List<String> {
        val moduleWords = moduleTitle?.let { TextNormalizer.words(it) }.orEmpty()
        val out = ArrayList<String>()
        for (h in page.header) {
            val words = TextNormalizer.words(h)
            if (words.isEmpty()) continue
            // "Concepto Normativo Conceptos Básicos" -> "Conceptos Básicos"; "con UAS" (end of the module name) is dropped.
            if (moduleWords.isNotEmpty() && isSubsequenceRun(words, moduleWords)) continue
            val stripped = if (moduleWords.isNotEmpty() && words.size > moduleWords.size && words.take(moduleWords.size) == moduleWords) {
                h.split(Regex("\\s+")).drop(moduleWords.size).joinToString(" ")
            } else h
            out += stripped
        }
        val body = page.body
        if (body.isNotEmpty()) {
            val titleSize = maxOf(TITLE_MIN_SIZE, bodySize(body) * 1.3f)
            body.take(4).filter { it.size >= titleSize }.forEach { out += it.text }
        }
        return out
    }

    /** Whether [words] appears as a contiguous run inside [of]. */
    private fun isSubsequenceRun(words: List<String>, of: List<String>): Boolean {
        if (words.size > of.size) return false
        for (start in 0..of.size - words.size) if (of.subList(start, start + words.size) == words) return true
        return false
    }

    /** Moves to a later index entry only when it matches the page headings better than the current one. */
    private fun assignEntry(index: List<IndexEntry>, current: Int, headings: List<String>): Int {
        if (index.isEmpty() || headings.isEmpty()) return current
        fun score(i: Int): Double {
            val e = index[i]
            return (listOf(e.title) + e.subtopics).maxOf { name -> headings.maxOf { h -> TitleSimilarity.score(name, h) } }
        }
        val currentScore = score(current)
        var best = current
        var bestScore = currentScore
        for (i in current + 1 until index.size) {
            val s = score(i)
            if (s > bestScore + 1e-9) { best = i; bestScore = s }
        }
        return if (best != current && bestScore >= ADVANCE_SCORE) best else current
    }

    // ---------------------------------------------------------------------------------------
    // Theory blocks
    // ---------------------------------------------------------------------------------------

    /** Most common font size of the page's text (slide titles aside), weighted by length. */
    private fun bodySize(lines: List<Line>): Float {
        val text = lines.filter { it.size < TITLE_MIN_SIZE }.ifEmpty { lines }
        return text.groupBy { Math.round(it.size * 2) / 2f }.maxByOrNull { (_, v) -> v.sumOf { it.text.length } }?.key ?: 0f
    }

    /** A paragraph or list item that can still receive the lines drawn below it. */
    private class OpenBlock(var block: ContentBlock, val first: Line, var last: Line)

    /**
     * Builds the blocks of a slide. Slides place text boxes side by side (two columns, captions),
     * so several paragraphs can be open at once: a line continues the paragraph directly above it
     * in the same column, not simply the previous line in vertical order.
     */
    private fun buildBlocks(page: Page): List<ContentBlock> {
        val size = bodySize(page.body)
        // Tiny print belongs to diagrams, which are shown as images.
        val lines = page.body.filter { l -> l.size >= size * 0.7f && !COVER_VERSION.matches(l.text) }
            .sortedWith(compareBy({ it.y }, { it.left }))
        // Images go after the text that starts above their top edge.
        val items = ArrayList<Any>()
        val pendingImages = ArrayDeque(page.images.sortedBy { it.top })
        for (l in lines) {
            while (pendingImages.isNotEmpty() && pendingImages.first().top < l.y) items += pendingImages.removeFirst()
            items += l
        }
        items.addAll(pendingImages)

        val out = ArrayList<Any>() // ContentBlock (closed) or OpenBlock, in reading order
        val open = ArrayList<OpenBlock>()
        var lastHeading: Pair<Int, Line>? = null // index in [out] and last line of the latest heading

        fun canContinue(ob: OpenBlock, line: Line): Boolean {
            val prev = ob.last
            val overlap = minOf(prev.right, line.right) - maxOf(prev.left, line.left)
            val sameColumn = overlap > 0.3f * minOf(prev.right - prev.left, line.right - line.left) ||
                kotlin.math.abs(prev.left - line.left) <= line.size * 1.5f
            return sameColumn && kotlin.math.abs(line.size - prev.size) <= 1.5f &&
                line.y > prev.y + 1f && line.y - prev.y <= line.size * 2f && !isAllCaps(prev.text) &&
                (!TERMINAL.containsMatchIn(prev.text) || line.text.first().isLowerCase())
        }

        // Images never change how the text is grouped: they are skipped when looking at the previous element.
        fun lastTextIndex() = out.indexOfLast { !(it is ContentBlock && it.type == BlockType.IMAGE) }

        fun start(block: ContentBlock, line: Line) {
            val ob = OpenBlock(block, line, line)
            // Two boxes starting on (almost) the same row: the left one is read first.
            val i = lastTextIndex()
            val before = out.getOrNull(i) as? OpenBlock
            if (before != null && kotlin.math.abs(before.first.y - line.y) <= line.size * 0.6f && line.left < before.first.left) {
                out.add(i, ob)
            } else {
                out += ob
            }
            open += ob
        }

        for (item in items) {
            if (item is PageImage) {
                // Pictures sit beside the text on slides: they do not interrupt an open paragraph or heading.
                out += ContentBlock(
                    BlockType.IMAGE, StyledText.plain(""), item.page, imageId = item.id,
                    imageAspect = if (item.height > 0) item.width.toFloat() / item.height else 1f,
                )
                continue
            }
            val line = item as Line
            val text = line.line.toStyledText()
            val bullet = BULLET.find(line.text)
            val numbered = NUMBERED.containsMatchIn(line.text)
            val headingLevel = when {
                line.size >= maxOf(TITLE_MIN_SIZE, size * 1.3f) -> 1
                line.size >= size * 1.2f && line.text.length <= 100 -> 2
                bullet != null && isAllCaps(line.text) && line.text.length <= 70 -> 3 // "• CONDICIONES PARTICULARES…"
                else -> 0
            }
            open.removeAll { line.y - it.last.y > it.last.size * 3f }
            val target = if (headingLevel == 0 && bullet == null && !numbered) open.lastOrNull { canContinue(it, line) } else null
            if (target != null) {
                target.block = target.block.copy(text = target.block.text.joinWith(text))
                target.last = line
                continue
            }
            // Isolated short labels ("15 m", "VLOS") belong to diagrams; a short end of a sentence ("UAS.") was kept above.
            val alnum = line.line.alnumCount
            val isLabel = alnum <= 3 || (alnum <= 5 && line.text.none { it.isLowerCase() })
            if (isLabel && !(headingLevel in 1..2 && alnum >= 3)) continue

            if (headingLevel > 0) {
                open.clear()
                val body = (if (bullet != null) line.line.sub(bullet.range.last + 1) else text).trimStart()
                val level = if (bullet != null) 3 else headingLevel
                val h = lastHeading
                val prevHeading = h?.let { out.getOrNull(it.first) as? ContentBlock }
                // A heading wrapped over two lines.
                if (h != null && prevHeading != null && h.first == lastTextIndex() && prevHeading.level == level &&
                    line.y - h.second.y <= line.size * 1.6f && !TERMINAL.containsMatchIn(prevHeading.text.text)
                ) {
                    out[h.first] = prevHeading.copy(text = prevHeading.text.joinWith(body))
                } else {
                    out += ContentBlock(BlockType.HEADING, body, line.page, level = level)
                }
                lastHeading = out.lastIndex to line
                continue
            }
            lastHeading = null
            when {
                bullet != null -> start(ContentBlock(BlockType.BULLET, line.line.sub(bullet.range.last + 1).trimStart(), line.page), line)
                numbered -> start(ContentBlock(BlockType.BULLET, text, line.page, level = 1), line)
                else -> start(ContentBlock(BlockType.PARAGRAPH, text, line.page), line)
            }
        }
        return out.map { if (it is OpenBlock) it.block else it as ContentBlock }
            .filter { it.type == BlockType.IMAGE || it.text.text.isNotBlank() }
    }

    private fun isAllCaps(s: String): Boolean {
        val letters = s.filter { it.isLetter() }
        return letters.length >= 6 && letters == letters.uppercase()
    }

    private fun StyledText.trimStart(): StyledText {
        val n = text.length - text.trimStart().length
        return if (n == 0) this else StyledText(text.substring(n), boldMask.copyOfRange(n, boldMask.size))
    }

    // ---------------------------------------------------------------------------------------
    // Questions
    // ---------------------------------------------------------------------------------------

    private fun buildQuestions(
        bank: QuestionBank,
        topics: List<ParsedTopic>,
        pageTopic: Map<Int, String>,
        pageText: Map<Int, String>,
    ): List<ParsedQuestion> {
        val counters = HashMap<String, Int>()
        val matcher = RelatedTheoryMatcher(topics)
        val topicsByKey = topics.associateBy { it.key }
        return bank.questions.map { bq ->
            val issues = HashSet<QuestionIssue>()
            val text = bq.question.trim()
            if (text.isEmpty()) issues += QuestionIssue.EMPTY_TEXT
            val options = bq.options.mapIndexed { i, o -> ParsedOption(LABELS.getOrElse(i) { "${i + 1}" }, o.trim()) }
            if (options.size < MIN_OPTIONS || options.any { it.text.isEmpty() }) issues += QuestionIssue.TOO_FEW_OPTIONS
            val correct = bq.answer.takeIf { it in options.indices }
            if (correct == null) issues += QuestionIssue.NO_CORRECT_ANSWER
            val topicKey = pageTopic[bq.page] ?: UNKNOWN_TOPIC_KEY
            if (topicKey == UNKNOWN_TOPIC_KEY) issues += QuestionIssue.UNKNOWN_TOPIC
            val literal = pageText[bq.page]?.let { TextNormalizer.findLiteral(it, bq.source) }
            if (literal == null) issues += QuestionIssue.SOURCE_NOT_FOUND
            val n = (counters[topicKey] ?: 0) + 1
            counters[topicKey] = n
            val parsed = ParsedQuestion(
                topicKey = topicKey, numberInTopic = n, text = text, options = options, correctIndex = correct,
                issues = issues, page = bq.page, sourceId = bq.id,
                explanation = literal?.let { "«${it.trim()}» (pág. ${bq.page})" },
            )
            val related = topicsByKey[topicKey]?.let { blocksQuoting(it, bq.page, bq.source) }.orEmpty()
            parsed.copy(relatedBlocks = related.ifEmpty { matcher.related(parsed) })
        }
    }

    /** Theory blocks of [page] that contain (part of) the cited sentence. */
    private fun blocksQuoting(topic: ParsedTopic, page: Int, quote: String): List<Int> {
        val q = TextNormalizer.comparable(quote)
        if (q.isEmpty()) return emptyList()
        return topic.blocks.withIndex().filter { (_, b) ->
            if (b.page != page || b.type == BlockType.IMAGE) return@filter false
            val t = TextNormalizer.comparable(b.text.text)
            t.length >= 12 && (t.contains(q) || q.contains(t))
        }.map { it.index }.take(3)
    }

    private fun markDuplicatesAndKeys(questions: List<ParsedQuestion>): List<ParsedQuestion> {
        val seen = HashMap<String, Int>()
        return questions.map { q ->
            val stem = TextNormalizer.comparable(q.text)
            val opts = q.options.map { TextNormalizer.comparable(it.text) }
            val dupKey = stem + "|" + opts.sorted().joinToString("|")
            val occurrence = seen.getOrDefault(dupKey, 0)
            seen[dupKey] = occurrence + 1
            val base = TextNormalizer.sha256(stem + "|" + opts.joinToString("|")).take(24)
            val key = if (occurrence == 0) base else "$base-$occurrence"
            if (occurrence > 0) q.copy(issues = q.issues + QuestionIssue.DUPLICATE, stableKey = key) else q.copy(stableKey = key)
        }
    }

    companion object {
        const val UNKNOWN_TOPIC_KEY = "T0"
        private const val MIN_OPTIONS = 3
        private val LABELS = listOf("A", "B", "C", "D", "E")

        /** Header (breadcrumb) lines sit above this baseline. */
        private const val HEADER_LIMIT = 45f
        private const val TITLE_MIN_SIZE = 23.5f
        private const val ADVANCE_SCORE = 0.6

        private val FOOTER = Regex("""FOR-UAS|DOCUMENTACI|ENTACIÓN P""")
        private val COVER_VERSION = Regex("""^Versi[oó]n\s+\d+\s*\(.*\d{4}\)$""")
        private val CLOSING = Regex("""Gracias por su atenci[oó]n""", RegexOption.IGNORE_CASE)
        private val INDEX_WORD = Regex("""[ÍI]ndice""")
        private val TOP_ENTRY = Regex("""^(\d{1,2})\)\s*(.+)$""")
        private val SUB_ENTRY = Regex("""^(?:[a-z]\)|\d{1,2}\.)\s*(.+)$""")
        private val ROMAN_ENTRY = Regex("""^[IVX]+\.\s+.+$""")
        private val SUMMARY_WORDS = setOf("resumen", "recuerda")
        private val BULLET = Regex("""^(?:[•·▪◦●]|[-–](?=\s)|o(?=\s+[A-ZÁÉÍÓÚ¿«“"(]))\s*""")
        private val NUMBERED = Regex("""^(\d{1,2}\.(?!\d)|[a-z]\.|[a-z]\))\s""")
        private val TERMINAL = Regex("""[.:;!?]["»”)]?$""")

        /** Collapses spaces and trailing punctuation; all-caps titles become sentence case. */
        fun tidyTitle(s: String): String {
            val t = s.replace(Regex("\\s+"), " ").trim().trimEnd('.', ':').trim()
            val letters = t.filter { it.isLetter() }
            if (letters.length > 3 && letters == letters.uppercase()) return t.lowercase().replaceFirstChar { it.uppercase() }
            return t
        }
    }
}

/** Similarity between two titles, tolerant to plural/singular and small wording changes. */
object TitleSimilarity {
    private val stop = setOf("de", "del", "la", "las", "el", "los", "en", "y", "e", "o", "a", "al", "con", "por", "para", "que", "un", "una")

    fun stems(s: String): Set<String> =
        TextNormalizer.words(s).filter { it !in stop && it.length > 1 }.map { if (it.length > 6) it.take(6) else it.trimEnd('s') }.toSet()

    /** Dice coefficient over word stems, in [0, 1]. */
    fun score(a: String, b: String): Double {
        val x = stems(a)
        val y = stems(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        return 2.0 * x.count { it in y } / (x.size + y.size)
    }
}
