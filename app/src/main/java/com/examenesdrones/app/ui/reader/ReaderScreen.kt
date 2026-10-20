package com.examenesdrones.app.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.examenesdrones.app.AppContainer
import com.examenesdrones.app.R
import com.examenesdrones.app.data.UserSettings
import com.examenesdrones.app.data.db.BookmarkEntity
import com.examenesdrones.app.data.db.TheoryBlockEntity
import com.examenesdrones.app.data.db.TopicEntity
import com.examenesdrones.app.ui.components.AppScaffold
import com.examenesdrones.app.ui.components.EmptyState
import com.examenesdrones.app.ui.components.LoadingBox
import com.examenesdrones.app.ui.components.TheoryBlockView
import com.examenesdrones.app.ui.components.appContainer
import com.examenesdrones.app.ui.components.appViewModel
import com.examenesdrones.app.ui.navigation.Routes
import com.examenesdrones.app.ui.theme.LocalFeedbackColors
import com.examenesdrones.app.ui.theme.readingStyle
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReaderState(
    val topic: TopicEntity,
    val blocks: List<TheoryBlockEntity>,
    val nextTopic: TopicEntity?,
    val initialIndex: Int,
    val initialOffset: Int,
    val completed: Boolean,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReaderViewModel(private val c: AppContainer, private val topicKey: String, private val jumpTo: Int) : ViewModel() {
    private val _state = MutableStateFlow<ReaderState?>(null)
    val state = _state.asStateFlow()
    private val _missing = MutableStateFlow(false)
    val missing = _missing.asStateFlow()

    val bookmarks = c.user.bookmarks(topicKey).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            val topic = c.content.topic(topicKey)
            if (topic == null) { _missing.value = true; return@launch }
            val blocks = c.content.blocks(topic.id)
            val topics = c.content.topicsOnce()
            val next = topics.getOrNull(topics.indexOfFirst { it.key == topicKey } + 1)
            val progress = c.user.reading(topicKey)
            // Index 0 of the list is the header; blocks start at 1.
            val (index, offset) = if (jumpTo >= 0) (jumpTo + 1) to 0 else (progress?.firstVisibleIndex ?: 0) to (progress?.scrollOffset ?: 0)
            _state.value = ReaderState(topic, blocks, next, index.coerceIn(0, blocks.size + 1), offset, progress?.completed == true)
        }
    }

    fun saveProgress(first: Int, offset: Int, lastVisible: Int) {
        val s = _state.value ?: return
        viewModelScope.launch {
            c.user.saveReading(topicKey, first, offset, (lastVisible - 1).coerceIn(0, (s.blocks.size - 1).coerceAtLeast(0)), s.blocks.size)
        }
    }

    fun setCompleted(done: Boolean) {
        val s = _state.value ?: return
        _state.value = s.copy(completed = done)
        viewModelScope.launch { c.user.setCompleted(topicKey, done, s.blocks.size) }
    }

    fun toggleBookmark(block: TheoryBlockEntity, add: Boolean) = viewModelScope.launch {
        val excerpt = block.text.ifBlank { "Imagen · pág. ${block.page}" }
        c.user.toggleBookmark(topicKey, block.position, excerpt, add)
    }
}

@OptIn(FlowPreview::class)
@Composable
fun ReaderScreen(nav: NavHostController, topicKey: String, block: Int) {
    val vm = appViewModel(key = "reader-$topicKey-$block") { ReaderViewModel(it, topicKey, block) }
    val state by vm.state.collectAsStateWithLifecycle()
    val missing by vm.missing.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val settings by appContainer().settings.settings.collectAsStateWithLifecycle(UserSettings())
    val container = appContainer()
    var showBookmarks by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val bookmarkAdded = stringResource(R.string.bookmark_added)
    val bookmarkRemoved = stringResource(R.string.bookmark_removed)

    val s = state
    AppScaffold(
        title = s?.topic?.title ?: stringResource(R.string.tab_topics),
        onBack = { nav.popBackStack() },
        snackbar = snackbar,
        actions = {
            if (s != null) {
                Box {
                    IconButton(onClick = { showBookmarks = true }) { Icon(Icons.Outlined.Bookmarks, stringResource(R.string.bookmarks)) }
                    BookmarkMenu(showBookmarks, bookmarks, onDismiss = { showBookmarks = false }) { pos ->
                        showBookmarks = false
                        nav.navigate(Routes.reader(topicKey, pos)) { popUpTo(Routes.READER) { inclusive = true } }
                    }
                }
                IconButton(onClick = { vm.setCompleted(!s.completed) }) {
                    if (s.completed) Icon(Icons.Filled.CheckCircle, stringResource(R.string.mark_not_completed), tint = LocalFeedbackColors.current.correct)
                    else Icon(Icons.Outlined.CheckCircle, stringResource(R.string.mark_completed))
                }
            }
        },
    ) { padding ->
        when {
            missing -> EmptyState(Icons.Outlined.Bookmarks, stringResource(R.string.topic_missing_title), stringResource(R.string.topic_missing_message), Modifier.padding(padding))
            s == null -> LoadingBox(Modifier.padding(padding))
            else -> {
                val listState = rememberLazyListState(s.initialIndex, s.initialOffset)
                var highlight by remember { mutableIntStateOf(block) }
                LaunchedEffect(highlight) { if (highlight >= 0) { delay(2500); highlight = -1 } }
                LaunchedEffect(listState) {
                    snapshotFlow { Triple(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) }
                        .debounce(400)
                        .collect { (first, offset, last) -> vm.saveProgress(first, offset, last) }
                }
                val bookmarked = bookmarks.map { it.blockPosition }.toSet()
                val textStyle = readingStyle(settings.textScale).copy(color = MaterialTheme.colorScheme.onSurface)
                val progress by remember { androidx.compose.runtime.derivedStateOf {
                    val total = listState.layoutInfo.totalItemsCount.coerceAtLeast(1)
                    ((listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) + 1).toFloat() / total
                } }
                Column(Modifier.padding(padding)) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), drawStopIndicator = {})
                    LazyColumn(
                        state = listState, modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        item(key = "header") { ReaderHeader(s.topic) }
                        itemsIndexed(s.blocks, key = { _, b -> b.id }, contentType = { _, b -> b.type }) { _, b ->
                            TheoryBlockView(
                                type = b.type, text = b.text, bold = b.bold, level = b.level,
                                imageFile = b.imageId?.let { container.content.imageFile(it) }, imageAspect = b.imageAspect,
                                style = textStyle, modifier = Modifier.widthIn(max = 720.dp),
                                bookmarked = b.position in bookmarked, highlighted = b.position == highlight,
                                onLongPress = {
                                    val add = b.position !in bookmarked
                                    vm.toggleBookmark(b, add)
                                    scope.launch { snackbar.showSnackbar(if (add) bookmarkAdded else bookmarkRemoved) }
                                },
                            )
                        }
                        item(key = "footer") {
                            ReaderFooter(
                                s, onComplete = { vm.setCompleted(!s.completed) },
                                onPractice = { nav.navigate(Routes.practiceSetup(listOf(s.topic.key))) },
                                onNext = { t -> nav.navigate(Routes.reader(t.key)) { popUpTo(Routes.READER) { inclusive = true } } },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderHeader(topic: TopicEntity) {
    Column(Modifier.fillMaxWidth().widthIn(max = 720.dp).padding(bottom = 8.dp)) {
        topic.code?.let { Text(stringResource(R.string.topic_code, it), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
        Text(topic.title, style = MaterialTheme.typography.headlineSmall)
        val subs = topic.subtopics.split('\n').filter { it.isNotBlank() }
        if (subs.size > 1) {
            Spacer(Modifier.height(8.dp))
            subs.forEach { Text("· $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.reader_bookmark_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun ReaderFooter(s: ReaderState, onComplete: () -> Unit, onPractice: () -> Unit, onNext: (TopicEntity) -> Unit) {
    Column(Modifier.fillMaxWidth().widthIn(max = 720.dp).padding(top = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider()
        Text(stringResource(R.string.reader_end_of_topic), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        if (s.completed) {
            OutlinedButton(onClick = onComplete, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.mark_not_completed)) }
        } else {
            Button(onClick = onComplete, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp)); Spacer(Modifier.size(8.dp)); Text(stringResource(R.string.mark_completed))
            }
        }
        if (s.topic.questionCount > 0) {
            OutlinedButton(onClick = onPractice, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Quiz, null, Modifier.size(18.dp)); Spacer(Modifier.size(8.dp)); Text(stringResource(R.string.practice_this_topic))
            }
        }
        s.nextTopic?.let { next ->
            OutlinedButton(onClick = { onNext(next) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.next_topic, next.title), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.size(8.dp)); Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun BookmarkMenu(expanded: Boolean, bookmarks: List<BookmarkEntity>, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (bookmarks.isEmpty()) {
            DropdownMenuItem(text = { Text(stringResource(R.string.no_bookmarks)) }, onClick = onDismiss, enabled = false)
        }
        bookmarks.forEach { b ->
            DropdownMenuItem(
                text = { Text(b.excerpt, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 280.dp)) },
                onClick = { onSelect(b.blockPosition) },
            )
        }
    }
}
