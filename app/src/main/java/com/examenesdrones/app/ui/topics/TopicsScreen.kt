package com.examenesdrones.app.ui.topics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.examenesdrones.app.AppContainer
import com.examenesdrones.app.R
import com.examenesdrones.app.data.db.BookmarkEntity
import com.examenesdrones.app.data.db.ReadingProgressEntity
import com.examenesdrones.app.data.db.TheoryBlockEntity
import com.examenesdrones.app.data.db.TopicEntity
import com.examenesdrones.app.parser.TextNormalizer
import com.examenesdrones.app.parser.TitleSimilarity
import com.examenesdrones.app.ui.components.AppScaffold
import com.examenesdrones.app.ui.components.EmptyState
import com.examenesdrones.app.ui.components.SectionTitle
import com.examenesdrones.app.ui.components.appViewModel
import com.examenesdrones.app.ui.navigation.Routes
import com.examenesdrones.app.ui.theme.LocalFeedbackColors
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

data class TopicRow(
    val topic: TopicEntity,
    val progress: ReadingProgressEntity?,
    val bookmarks: List<BookmarkEntity>,
    /** Subtopic title and the position of its heading in the reader (or -1). */
    val subtopics: List<Pair<String, Int>>,
) {
    val percent: Float
        get() = when {
            progress == null -> 0f
            progress.completed -> 1f
            else -> ((progress.maxSeenIndex + 1).toFloat() / topic.blockCount.coerceAtLeast(1)).coerceIn(0f, 1f)
        }
}

data class SearchHit(val topic: TopicEntity, val block: TheoryBlockEntity)

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TopicsViewModel(private val c: AppContainer) : ViewModel() {
    val query = MutableStateFlow("")

    private val headings = c.content.topics.mapLatest { topics ->
        topics.associate { t -> t.key to c.content.blocks(t.id).filter { it.type == "HEADING" } }
    }

    val rows = combine(c.content.topics, c.user.readingProgress, c.user.bookmarks, headings) { topics, reading, bookmarks, heads ->
        val r = reading.associateBy { it.topicKey }
        topics.map { t ->
            val h = heads[t.key].orEmpty()
            val subs = t.subtopics.split('\n').filter { it.isNotBlank() }.map { sub ->
                // Index entries and slide headings differ slightly ("Efecto de…" / "Efectos de…").
                val best = h.maxByOrNull { TitleSimilarity.score(sub, it.text) }
                sub to (best?.takeIf { TitleSimilarity.score(sub, it.text) >= 0.6 }?.position ?: -1)
            }
            TopicRow(t, r[t.key], bookmarks.filter { it.topicKey == t.key }, subs)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val results = query.debounce(250).mapLatest { q ->
        if (q.isBlank()) null else {
            val topics = c.content.topicsOnce().associateBy { it.id }
            val titleHits = topics.values.filter { TextNormalizer.foldForSearch(it.title).contains(TextNormalizer.foldForSearch(q)) }
                .map { SearchHit(it, TheoryBlockEntity(0, it.id, 0, "HEADING", it.title, "", 1, 0, null, 0f, "")) }
            titleHits + c.content.search(q).mapNotNull { b -> topics[b.topicId]?.let { SearchHit(it, b) } }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

@Composable
fun TopicsScreen(nav: NavHostController) {
    val vm = appViewModel { TopicsViewModel(it) }
    val rows by vm.rows.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()

    AppScaffold(title = stringResource(R.string.tab_topics), tabScreen = true) { padding ->
        val list = rows
        if (list != null && list.isEmpty()) {
            EmptyState(
                Icons.AutoMirrored.Outlined.MenuBook, stringResource(R.string.no_content_title), stringResource(R.string.no_content_message),
                Modifier.padding(padding),
            )
            return@AppScaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                OutlinedTextField(
                    value = query, onValueChange = { vm.query.value = it }, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.search_syllabus)) }, singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Outlined.Clear, stringResource(R.string.clear)) }
                    },
                )
            }
            val hits = results
            if (hits != null) {
                if (hits.isEmpty()) item { Text(stringResource(R.string.search_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(hits, key = { "${it.topic.key}-${it.block.position}-${it.block.type}-${it.block.id}" }) { h ->
                    SearchHitRow(h, query) { nav.navigate(Routes.reader(h.topic.key, if (h.block.id == 0L) -1 else h.block.position)) }
                }
            } else {
                list.orEmpty().forEachIndexed { i, row ->
                    val module = row.topic.module
                    if (module != null && module != list.orEmpty().getOrNull(i - 1)?.topic?.module) {
                        item(key = "module-$i") { SectionTitle(module, Modifier.padding(top = 8.dp)) }
                    }
                    item(key = row.topic.key) {
                        TopicCard(row, onOpen = { pos -> nav.navigate(Routes.reader(row.topic.key, pos)) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHitRow(h: SearchHit, query: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(h.topic.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(snippet(h.block.text, query), style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun snippet(text: String, query: String): String {
    // Character-aligned, accent-insensitive folding so indices map back to the original text.
    fun fold(s: String) = s.map { ch ->
        java.text.Normalizer.normalize(ch.toString(), java.text.Normalizer.Form.NFD).firstOrNull()?.lowercaseChar() ?: ch
    }.joinToString("")
    val idx = fold(text).indexOf(fold(query.trim()))
    if (idx <= 60) return text
    return "…" + text.substring(idx - 50).substringAfter(' ')
}

@Composable
private fun TopicCard(row: TopicRow, onOpen: (Int) -> Unit) {
    var expanded by rememberSaveable(row.topic.key) { mutableStateOf(false) }
    val fb = LocalFeedbackColors.current
    val completed = row.progress?.completed == true
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(-1) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                        Text(
                            row.topic.code ?: if (row.topic.key == "INTRO") "i" else "+",
                            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(row.topic.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (row.topic.questionCount > 0) pluralStringResource(R.plurals.topic_questions, row.topic.questionCount, row.topic.questionCount)
                        else stringResource(R.string.topic_no_questions),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { row.percent }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        drawStopIndicator = {},
                    )
                }
                if (completed) Icon(Icons.Filled.CheckCircle, stringResource(R.string.completed), tint = fb.correct)
                val expandLabel = stringResource(if (expanded) R.string.collapse else R.string.expand)
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.semantics { stateDescription = expandLabel }) {
                    Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, expandLabel)
                }
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(start = 72.dp, end = 16.dp, bottom = 12.dp)) {
                    row.subtopics.forEach { (title, pos) ->
                        TextButton(onClick = { onOpen(pos) }) { Text(title, style = MaterialTheme.typography.bodyMedium) }
                    }
                    if (row.subtopics.isEmpty()) {
                        TextButton(onClick = { onOpen(-1) }) { Text(stringResource(R.string.open_topic)) }
                    }
                    row.bookmarks.forEach { b ->
                        TextButton(onClick = { onOpen(b.blockPosition) }) {
                            Icon(Icons.Outlined.Bookmark, null, Modifier.size(16.dp))
                            Text(b.excerpt, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        }
    }
}
