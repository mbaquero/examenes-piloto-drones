package com.examenesdrones.app.ui.practice

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.examenesdrones.app.AppContainer
import com.examenesdrones.app.R
import com.examenesdrones.app.data.StudyRepository
import com.examenesdrones.app.data.db.TheoryBlockEntity
import com.examenesdrones.app.domain.OptionShuffler
import com.examenesdrones.app.domain.Question
import com.examenesdrones.app.ui.components.AppScaffold
import com.examenesdrones.app.ui.components.EmptyState
import com.examenesdrones.app.ui.components.ExplanationCard
import com.examenesdrones.app.ui.components.FeedbackBanner
import com.examenesdrones.app.ui.components.LoadingBox
import com.examenesdrones.app.ui.components.OptionCard
import com.examenesdrones.app.ui.components.OptionState
import com.examenesdrones.app.ui.components.RelatedTheoryCard
import com.examenesdrones.app.ui.components.appViewModel
import com.examenesdrones.app.ui.components.optionLetter
import com.examenesdrones.app.ui.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

data class PracticeUi(
    val sessionId: Long,
    val questions: List<Question>,
    val index: Int,
    val topicTitles: Map<String, String>,
    /** Display order for the current question: element i = source index shown at position i. */
    val order: List<Int>,
    val selectedDisplay: Int? = null,
    /** Source index answered, once submitted. */
    val answeredSource: Int? = null,
    val related: List<TheoryBlockEntity> = emptyList(),
    val correctSoFar: Int = 0,
    val submitting: Boolean = false,
) {
    val question: Question get() = questions[index]
    val submitted: Boolean get() = answeredSource != null
}

class PracticeSessionViewModel(private val c: AppContainer, private val sessionId: Long) : ViewModel() {
    private val _ui = MutableStateFlow<PracticeUi?>(null)
    val ui = _ui.asStateFlow()
    private val _finished = MutableStateFlow(false)
    val finished = _finished.asStateFlow()
    private val _missing = MutableStateFlow(false)
    val missing = _missing.asStateFlow()
    private var shuffleOptions = false

    init {
        viewModelScope.launch {
            val session = c.study.practiceSession(sessionId)
            if (session == null) { _missing.value = true; return@launch }
            if (session.status != StudyRepository.STATUS_IN_PROGRESS) { _finished.value = true; return@launch }
            shuffleOptions = c.study.decodePracticeConfig(session).shuffleOptions
            val keys = session.questionKeys.split(',').filter { it.isNotBlank() }
            val byKey = c.content.questionsByKeys(keys)
            // Questions removed by a later re-import are skipped safely.
            val questions = keys.mapNotNull { byKey[it] }
            if (questions.isEmpty()) { _missing.value = true; return@launch }
            val responses = c.study.practiceResponses(sessionId)
            val titles = c.content.topicsOnce().associate { it.key to it.title }
            val index = session.currentIndex.coerceIn(0, questions.size - 1)
            _ui.value = PracticeUi(sessionId, questions, index, titles, orderFor(questions[index], index), correctSoFar = responses.count { it.correct })
            responses.firstOrNull { it.position == index }?.let { r -> showFeedback(r.selectedIndex) }
        }
    }

    private fun orderFor(q: Question, position: Int): List<Int> =
        OptionShuffler.order(q.options, shuffleOptions, Random(sessionId * 7919 + position))

    fun select(display: Int) = _ui.update { if (it == null || it.submitted) it else it.copy(selectedDisplay = display) }

    fun submit() {
        val s = _ui.value ?: return
        val display = s.selectedDisplay ?: return
        if (s.submitted || s.submitting) return
        _ui.value = s.copy(submitting = true)
        viewModelScope.launch {
            try {
                val source = s.order[display]
                val correct = c.study.answerPractice(sessionId, s.index, s.question, s.topicTitles[s.question.topicKey] ?: "", source)
                _ui.update { it?.copy(correctSoFar = it.correctSoFar + if (correct) 1 else 0) }
                showFeedback(source)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("PracticeSession", "Could not save answer", e)
                // Never leave the screen locked: let the user try again.
                _ui.update { it?.copy(submitting = false) }
            }
        }
    }

    private suspend fun showFeedback(source: Int) {
        val s = _ui.value ?: return
        val related = c.content.relatedTheory(s.question)
        _ui.value = s.copy(answeredSource = source, selectedDisplay = s.order.indexOf(source), related = related, submitting = false)
    }

    fun next() {
        val s = _ui.value ?: return
        viewModelScope.launch {
            val nextIndex = s.index + 1
            c.study.advancePractice(sessionId, nextIndex)
            if (nextIndex >= s.questions.size) {
                _finished.value = true
            } else {
                _ui.value = s.copy(index = nextIndex, order = orderFor(s.questions[nextIndex], nextIndex), selectedDisplay = null, answeredSource = null, related = emptyList())
            }
        }
    }

    fun finishNow() = viewModelScope.launch {
        c.study.finishPracticeEarly(sessionId)
        _finished.value = true
    }
}

@Composable
fun PracticeSessionScreen(nav: NavHostController, sessionId: Long) {
    val vm = appViewModel(key = "practice-$sessionId") { PracticeSessionViewModel(it, sessionId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val finished by vm.finished.collectAsStateWithLifecycle()
    val missing by vm.missing.collectAsStateWithLifecycle()
    var confirmExit by remember { mutableStateOf(false) }

    LaunchedEffect(finished) {
        if (finished) nav.navigate(Routes.practiceSummary(sessionId)) { popUpTo(Routes.PRACTICE_SESSION) { inclusive = true } }
    }
    BackHandler { confirmExit = true }

    val s = ui
    AppScaffold(
        title = if (s != null) stringResource(R.string.question_x_of_n, s.index + 1, s.questions.size) else stringResource(R.string.mode_practice_title),
        actions = { IconButton(onClick = { confirmExit = true }) { Icon(Icons.Outlined.Close, stringResource(R.string.exit_session)) } },
        bottomBar = {
            if (s != null) {
                Surface(tonalElevation = 3.dp) {
                    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                        if (!s.submitted) {
                            Button(
                                onClick = vm::submit, enabled = s.selectedDisplay != null && !s.submitting,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                            ) { Text(stringResource(R.string.check_answer)) }
                        } else {
                            val last = s.index + 1 >= s.questions.size
                            Button(onClick = vm::next, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                                Text(stringResource(if (last) R.string.see_summary else R.string.next_question))
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        when {
            missing -> EmptyState(Icons.Outlined.Quiz, stringResource(R.string.session_missing_title), stringResource(R.string.session_missing_message), Modifier.padding(padding),
                stringResource(R.string.back_home)) { nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } }
            s == null -> LoadingBox(Modifier.padding(padding))
            else -> QuestionContent(s, Modifier.padding(padding), vm::select) { pos ->
                nav.navigate(Routes.reader(s.question.topicKey, pos))
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text(stringResource(R.string.exit_session_title)) },
            text = { Text(stringResource(R.string.exit_session_message)) },
            confirmButton = {
                TextButton(onClick = { confirmExit = false; nav.popBackStack() }) { Text(stringResource(R.string.exit_continue_later)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmExit = false; vm.finishNow() }) { Text(stringResource(R.string.finish_now)) }
                    TextButton(onClick = { confirmExit = false }) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
    }
}

@Composable
private fun QuestionContent(s: PracticeUi, modifier: Modifier, onSelect: (Int) -> Unit, onOpenTheory: (Int) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(s.index) { listState.scrollToItem(0) }
    // After answering, bring the marked options and the verdict into view.
    LaunchedEffect(s.submitted) { if (s.submitted) listState.animateScrollToItem(2) }
    val q = s.question
    LazyColumn(
        modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(Modifier.widthIn(max = 720.dp)) {
                LinearProgressIndicator(
                    progress = { (s.index + if (s.submitted) 1 else 0).toFloat() / s.questions.size },
                    modifier = Modifier.fillMaxWidth(), drawStopIndicator = {},
                )
                Text(
                    stringResource(R.string.practice_running_score, s.correctSoFar, s.index + if (s.submitted) 1 else 0),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        item {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                Text(s.topicTitles[q.topicKey] ?: q.topicKey, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(q.text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))
            }
        }
        s.order.forEachIndexed { display, source ->
            item(key = "opt-${s.index}-$display") {
                val state = when {
                    !s.submitted -> if (s.selectedDisplay == display) OptionState.SELECTED else OptionState.IDLE
                    source == q.correctIndex && source == s.answeredSource -> OptionState.CORRECT
                    source == q.correctIndex -> OptionState.CORRECT_NOT_SELECTED
                    source == s.answeredSource -> OptionState.WRONG_SELECTED
                    else -> OptionState.DISABLED
                }
                OptionCard(optionLetter(display), q.options[source], state, onClick = { onSelect(display) }, enabled = !s.submitted, modifier = Modifier.widthIn(max = 720.dp))
            }
        }
        if (s.submitted) {
            item(key = "feedback-${s.index}") {
                val correctDisplay = s.order.indexOf(q.correctIndex)
                FeedbackBanner(
                    correct = s.answeredSource == q.correctIndex,
                    correctAnswer = "${optionLetter(correctDisplay)}) ${q.options[q.correctIndex]}",
                    modifier = Modifier.widthIn(max = 720.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            item(key = "explanation-${s.index}") { ExplanationCard(q.explanation, Modifier.widthIn(max = 720.dp)) }
            item(key = "theory-${s.index}") {
                RelatedTheoryCard(
                    s.related.map { it.text },
                    onOpenTheory = { onOpenTheory(s.related.firstOrNull()?.position ?: -1) },
                    modifier = Modifier.widthIn(max = 720.dp),
                )
            }
        }
    }
}
