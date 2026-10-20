package com.examenesdrones.app.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.examenesdrones.app.AppContainer
import com.examenesdrones.app.R
import com.examenesdrones.app.data.db.ExamAttemptEntity
import com.examenesdrones.app.data.db.TopicAccuracyRow
import com.examenesdrones.app.domain.PracticeConfig
import com.examenesdrones.app.domain.PracticeMode
import com.examenesdrones.app.domain.Question
import com.examenesdrones.app.ui.components.AppScaffold
import com.examenesdrones.app.ui.components.EmptyState
import com.examenesdrones.app.ui.components.LoadingBox
import com.examenesdrones.app.ui.components.PercentBar
import com.examenesdrones.app.ui.components.SectionTitle
import com.examenesdrones.app.ui.components.StatTile
import com.examenesdrones.app.ui.components.TrendChart
import com.examenesdrones.app.ui.components.appViewModel
import com.examenesdrones.app.ui.components.formatPercent
import com.examenesdrones.app.ui.home.RecentExamRow
import com.examenesdrones.app.ui.navigation.Routes
import com.examenesdrones.app.ui.theme.LocalFeedbackColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MissedItem(val key: String, val question: Question?, val attempts: Int, val wrong: Int)

data class StatsState(
    val exams: List<ExamAttemptEntity>,
    val topicAccuracy: List<TopicAccuracyRow>,
    val totalAnswered: Int,
    val totalCorrect: Int,
    val missed: List<MissedItem>,
) {
    val average: Double? get() = exams.takeIf { it.isNotEmpty() }?.map { it.percent }?.average()
    val best: Double? get() = exams.maxOfOrNull { it.percent }
    val passRate: Double? get() = exams.filter { it.passed != null }.takeIf { it.isNotEmpty() }?.let { l -> l.count { it.passed == true } * 100.0 / l.size }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ProgressViewModel(private val c: AppContainer) : ViewModel() {
    val exams = c.study.submittedExams.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val missed = c.study.mostMissed(20).mapLatest { rows ->
        val questions = c.content.questionsByKeys(rows.map { it.questionKey })
        rows.map { MissedItem(it.questionKey, questions[it.questionKey], it.attempts, it.attempts - it.correctCount) }
    }

    val stats = combine(c.study.submittedExams, c.study.topicAccuracy, missed) { exams, topics, missed ->
        StatsState(exams, topics, topics.sumOf { it.answered }, topics.sumOf { it.correct }, missed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val started = MutableStateFlow<Long?>(null)

    fun practiceMissed(keys: List<String>) = viewModelScope.launch {
        runCatching { c.study.startPractice(PracticeConfig(PracticeMode.RETRY, questionKeys = keys, count = 0, shuffleQuestions = true)) }
            .onSuccess { started.value = it }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(nav: NavHostController) {
    val vm = appViewModel { ProgressViewModel(it) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val started by vm.started.collectAsStateWithLifecycle()
    LaunchedEffect(started) { started?.let { vm.started.value = null; nav.navigate(Routes.practiceSession(it)) } }

    AppScaffold(title = stringResource(R.string.tab_progress), tabScreen = true) { padding ->
        Column(Modifier.padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text(stringResource(R.string.statistics)) })
                Tab(tab == 1, { tab = 1 }, text = { Text(stringResource(R.string.exam_history)) })
            }
            if (tab == 0) StatsTab(vm, nav) else HistoryTab(vm, nav)
        }
    }
}

@Composable
private fun HistoryTab(vm: ProgressViewModel, nav: NavHostController) {
    val exams by vm.exams.collectAsStateWithLifecycle()
    val list = exams
    when {
        list == null -> LoadingBox()
        list.isEmpty() -> EmptyState(Icons.Outlined.Insights, stringResource(R.string.no_history_title), stringResource(R.string.no_history_message),
            actionLabel = stringResource(R.string.start_exam)) { nav.navigate(Routes.EXAM_SETUP) }
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { e -> RecentExamRow(e) { nav.navigate(Routes.examResult(e.id)) } }
        }
    }
}

@Composable
private fun StatsTab(vm: ProgressViewModel, nav: NavHostController) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    val s = stats ?: return LoadingBox()
    val fb = LocalFeedbackColors.current
    if (s.exams.isEmpty() && s.totalAnswered == 0) {
        EmptyState(Icons.Outlined.Insights, stringResource(R.string.no_stats_title), stringResource(R.string.no_stats_message),
            actionLabel = stringResource(R.string.start_practice)) { nav.navigate(Routes.practiceSetup(emptyList())) }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(stringResource(R.string.exams_completed), s.exams.size.toString(), Modifier.weight(1f))
                StatTile(stringResource(R.string.average_score), s.average?.let { formatPercent(Math.round(it * 10) / 10.0) } ?: "—", Modifier.weight(1f))
                StatTile(stringResource(R.string.best_score), s.best?.let { formatPercent(Math.round(it * 10) / 10.0) } ?: "—", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(stringResource(R.string.questions_answered), s.totalAnswered.toString(), Modifier.weight(1f))
                StatTile(
                    stringResource(R.string.overall_accuracy),
                    if (s.totalAnswered == 0) "—" else formatPercent(Math.round(s.totalCorrect * 1000.0 / s.totalAnswered) / 10.0),
                    Modifier.weight(1f),
                )
                StatTile(stringResource(R.string.pass_rate), s.passRate?.let { formatPercent(Math.round(it).toDouble()) } ?: "—", Modifier.weight(1f))
            }
        }
        if (s.exams.size >= 2) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.recent_trend), style = MaterialTheme.typography.titleSmall)
                        val recent = s.exams.take(10).reversed()
                        val delta = recent.last().percent - recent.first().percent
                        Text(
                            stringResource(if (delta >= 0) R.string.trend_up else R.string.trend_down, formatPercent(Math.round(kotlin.math.abs(delta) * 10) / 10.0)),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TrendChart(recent.map { it.percent }, Modifier.padding(top = 12.dp), threshold = null)
                    }
                }
            }
        }
        if (s.topicAccuracy.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.accuracy_by_topic)) }
            items(s.topicAccuracy, key = { it.topicKey }) { t ->
                val p = if (t.answered == 0) 0.0 else t.correct * 100.0 / t.answered
                PercentBar(t.topicTitle, p, "${formatPercent(Math.round(p).toDouble())} · ${t.correct}/${t.answered}",
                    color = when { p >= 80 -> fb.correct; p < 60 -> fb.wrong; else -> MaterialTheme.colorScheme.secondary })
            }
            val reliable = s.topicAccuracy.filter { it.answered >= 5 }
            val strong = reliable.filter { it.correct * 100.0 / it.answered >= 80 }
            val weak = reliable.filter { it.correct * 100.0 / it.answered < 70 }.sortedBy { it.correct.toDouble() / it.answered }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.strong_topics), style = MaterialTheme.typography.titleSmall, color = fb.correct)
                        Text(strong.joinToString(", ") { it.topicTitle }.ifEmpty { stringResource(R.string.none_yet) }, style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.weak_topics), style = MaterialTheme.typography.titleSmall, color = fb.wrong)
                        Text(weak.joinToString(", ") { it.topicTitle }.ifEmpty { stringResource(R.string.none_yet) }, style = MaterialTheme.typography.bodyMedium)
                        if (reliable.size < s.topicAccuracy.size) {
                            Text(stringResource(R.string.min_answers_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (weak.isNotEmpty()) {
                            Button(onClick = { nav.navigate(Routes.practiceSetup(weak.map { it.topicKey })) }) { Text(stringResource(R.string.practice_weak_topics)) }
                        }
                    }
                }
            }
        }
        if (s.missed.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.most_missed)) }
            items(s.missed, key = { it.key }) { m ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(m.question?.text ?: stringResource(R.string.question_not_in_material), style = MaterialTheme.typography.bodyMedium)
                        m.question?.let { q -> Text(stringResource(R.string.correct_answer, q.options[q.correctIndex]), style = MaterialTheme.typography.bodySmall, color = fb.correct) }
                        Text(stringResource(R.string.missed_times, m.wrong, m.attempts), style = MaterialTheme.typography.labelMedium, color = fb.wrong)
                    }
                }
            }
            item {
                val keys = s.missed.filter { it.question != null }.map { it.key }
                Button(onClick = { vm.practiceMissed(keys) }, enabled = keys.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.practice_missed, keys.size))
                }
            }
        }
    }
}
