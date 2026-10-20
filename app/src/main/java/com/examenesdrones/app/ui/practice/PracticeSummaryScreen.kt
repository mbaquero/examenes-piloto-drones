package com.examenesdrones.app.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.examenesdrones.app.AppContainer
import com.examenesdrones.app.R
import com.examenesdrones.app.data.InsufficientQuestionsException
import com.examenesdrones.app.data.db.PracticeResponseEntity
import com.examenesdrones.app.domain.PracticeConfig
import com.examenesdrones.app.domain.PracticeMode
import com.examenesdrones.app.domain.Question
import com.examenesdrones.app.ui.components.AppScaffold
import com.examenesdrones.app.ui.components.LoadingBox
import com.examenesdrones.app.ui.components.PercentBar
import com.examenesdrones.app.ui.components.ScoreRing
import com.examenesdrones.app.ui.components.SectionTitle
import com.examenesdrones.app.ui.components.StatTile
import com.examenesdrones.app.ui.components.appViewModel
import com.examenesdrones.app.ui.components.formatPercent
import com.examenesdrones.app.ui.navigation.Routes
import com.examenesdrones.app.ui.theme.LocalFeedbackColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PracticeSummary(
    val responses: List<PracticeResponseEntity>,
    val questions: Map<String, Question>,
    val config: PracticeConfig,
)

class PracticeSummaryViewModel(private val c: AppContainer, private val sessionId: Long) : ViewModel() {
    private val _summary = MutableStateFlow<PracticeSummary?>(null)
    val summary = _summary.asStateFlow()
    private val _started = MutableStateFlow<Long?>(null)
    val started = _started.asStateFlow()

    init {
        viewModelScope.launch {
            val session = c.study.practiceSession(sessionId) ?: return@launch
            val responses = c.study.practiceResponses(sessionId)
            val questions = c.content.questionsByKeys(responses.filter { !it.correct }.map { it.questionKey })
            _summary.value = PracticeSummary(responses, questions, c.study.decodePracticeConfig(session))
        }
    }

    fun retryWrong() = viewModelScope.launch {
        val s = _summary.value ?: return@launch
        val keys = s.responses.filter { !it.correct }.map { it.questionKey }
        try {
            _started.value = c.study.startPractice(s.config.copy(mode = PracticeMode.RETRY, questionKeys = keys, count = 0))
        } catch (_: InsufficientQuestionsException) { }
    }

    fun again() = viewModelScope.launch {
        val s = _summary.value ?: return@launch
        val cfg = if (s.config.mode == PracticeMode.RETRY) s.config.copy(mode = PracticeMode.RANDOM, questionKeys = emptyList(), count = 20) else s.config
        try { _started.value = c.study.startPractice(cfg) } catch (_: InsufficientQuestionsException) { }
    }
}

@Composable
fun PracticeSummaryScreen(nav: NavHostController, sessionId: Long) {
    val vm = appViewModel(key = "summary-$sessionId") { PracticeSummaryViewModel(it, sessionId) }
    val summary by vm.summary.collectAsStateWithLifecycle()
    val started by vm.started.collectAsStateWithLifecycle()
    val fb = LocalFeedbackColors.current

    LaunchedEffect(started) {
        started?.let { nav.navigate(Routes.practiceSession(it)) { popUpTo(Routes.PRACTICE_SUMMARY) { inclusive = true } } }
    }

    AppScaffold(title = stringResource(R.string.practice_summary), onBack = { nav.popBackStack() }) { padding ->
        val s = summary
        if (s == null) { LoadingBox(Modifier.padding(padding)); return@AppScaffold }
        val total = s.responses.size
        val correct = s.responses.count { it.correct }
        val wrong = s.responses.filter { !it.correct }
        val accuracy = if (total == 0) 0.0 else correct * 100.0 / total
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    ScoreRing(accuracy, formatPercent(Math.round(accuracy * 10) / 10.0), stringResource(R.string.accuracy))
                    Text(
                        stringResource(if (accuracy >= 80) R.string.encourage_high else if (accuracy >= 50) R.string.encourage_mid else R.string.encourage_low),
                        style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(stringResource(R.string.answered), total.toString(), Modifier.weight(1f))
                    StatTile(stringResource(R.string.correct), correct.toString(), Modifier.weight(1f))
                    StatTile(stringResource(R.string.incorrect), wrong.size.toString(), Modifier.weight(1f))
                }
            }
            item { SectionTitle(stringResource(R.string.by_topic)) }
            s.responses.groupBy { it.topicKey }.toSortedMap(compareBy { it.removePrefix("T").toIntOrNull() ?: 99 }).forEach { (key, list) ->
                item(key = "topic-$key") {
                    val ok = list.count { it.correct }
                    PercentBar(list.first().topicTitle, ok * 100.0 / list.size, "$ok/${list.size}")
                }
            }
            if (wrong.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.incorrect_questions)) }
                items(wrong, key = { it.id }) { r ->
                    val q = s.questions[r.questionKey]
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(r.questionText, style = MaterialTheme.typography.bodyMedium)
                            if (q != null) {
                                Text(stringResource(R.string.your_answer, q.options.getOrElse(r.selectedIndex) { "?" }), style = MaterialTheme.typography.bodySmall, color = fb.wrong)
                                Text(stringResource(R.string.correct_answer, q.options[q.correctIndex]), style = MaterialTheme.typography.bodySmall, color = fb.correct)
                                TextButton(onClick = { nav.navigate(Routes.reader(q.topicKey, q.relatedBlocks.firstOrNull() ?: -1)) }) {
                                    Text(stringResource(R.string.see_theory))
                                }
                            }
                        }
                    }
                }
                item {
                    Button(onClick = vm::retryWrong, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text(stringResource(R.string.retry_wrong, wrong.size))
                    }
                }
            }
            item {
                OutlinedButton(onClick = vm::again, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.new_session)) }
            }
            item {
                TextButton(onClick = { nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.back_home))
                }
            }
        }
    }
}
