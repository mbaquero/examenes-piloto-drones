package com.examenesdrones.app.ui.exam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AssistChip
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
import com.examenesdrones.app.data.StudyRepository
import com.examenesdrones.app.data.db.ExamAttemptEntity
import com.examenesdrones.app.data.db.ExamResponseEntity
import com.examenesdrones.app.domain.ExamConfig
import com.examenesdrones.app.ui.components.AppScaffold
import com.examenesdrones.app.ui.components.EmptyState
import com.examenesdrones.app.ui.components.LoadingBox
import com.examenesdrones.app.ui.components.PercentBar
import com.examenesdrones.app.ui.components.ScoreRing
import com.examenesdrones.app.ui.components.SectionTitle
import com.examenesdrones.app.ui.components.StatTile
import com.examenesdrones.app.ui.components.appViewModel
import com.examenesdrones.app.ui.components.formatDateTime
import com.examenesdrones.app.ui.components.formatDuration
import com.examenesdrones.app.ui.components.formatPercent
import com.examenesdrones.app.ui.components.formatScore
import com.examenesdrones.app.ui.navigation.Routes
import com.examenesdrones.app.ui.theme.LocalFeedbackColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ExamResultData(
    val exam: ExamAttemptEntity,
    val responses: List<ExamResponseEntity>,
    val config: ExamConfig,
    val previous: List<ExamAttemptEntity>,
)

class ExamResultViewModel(private val c: AppContainer, private val examId: Long) : ViewModel() {
    private val _data = MutableStateFlow<ExamResultData?>(null)
    val data = _data.asStateFlow()
    private val _missing = MutableStateFlow(false)
    val missing = _missing.asStateFlow()
    private val _started = MutableStateFlow<Long?>(null)
    val started = _started.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init {
        viewModelScope.launch {
            val exam = c.study.exam(examId)
            if (exam == null || exam.status != StudyRepository.STATUS_SUBMITTED) { _missing.value = true; return@launch }
            val previous = c.study.submittedOnce().filter { (it.submittedAt ?: 0) < (exam.submittedAt ?: 0) }
            _data.value = ExamResultData(exam, c.study.examResponses(examId), c.study.decodeExamConfig(exam), previous)
        }
    }

    /** Same configuration, new random selection of questions. */
    fun repeat() = viewModelScope.launch {
        val d = _data.value ?: return@launch
        try {
            val titles = c.content.topicsOnce().associate { it.key to it.title }
            _started.value = c.study.startExam(d.config, titles)
        } catch (e: InsufficientQuestionsException) {
            _error.value = "No hay preguntas disponibles con la configuración de este examen."
        }
    }
}

@Composable
fun ExamResultScreen(nav: NavHostController, examId: Long) {
    val vm = appViewModel(key = "result-$examId") { ExamResultViewModel(it, examId) }
    val data by vm.data.collectAsStateWithLifecycle()
    val missing by vm.missing.collectAsStateWithLifecycle()
    val started by vm.started.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val fb = LocalFeedbackColors.current

    LaunchedEffect(started) { started?.let { nav.navigate(Routes.examSession(it)) { popUpTo(Routes.EXAM_RESULT) { inclusive = true } } } }

    AppScaffold(title = stringResource(R.string.exam_result), onBack = { nav.popBackStack() }) { padding ->
        val d = data
        when {
            missing -> { EmptyState(Icons.Outlined.TaskAlt, stringResource(R.string.exam_missing_title), stringResource(R.string.exam_missing_message), Modifier.padding(padding)); return@AppScaffold }
            d == null -> { LoadingBox(Modifier.padding(padding)); return@AppScaffold }
        }
        val e = d!!.exam
        val passed = e.passed
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatDateTime(e.submittedAt ?: e.startedAt), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ScoreRing(
                        e.percent, "${e.correct}/${e.questionCount}", formatPercent(Math.round(e.percent * 10) / 10.0),
                        color = when (passed) { true -> fb.correct; false -> fb.wrong; null -> MaterialTheme.colorScheme.primary },
                    )
                    if (passed != null) {
                        Card(colors = CardDefaults.cardColors(containerColor = if (passed) fb.correctContainer else fb.wrongContainer, contentColor = if (passed) fb.onCorrectContainer else fb.onWrongContainer)) {
                            Text(
                                stringResource(if (passed) R.string.result_passed else R.string.result_failed),
                                style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                            )
                        }
                        d.config.scoring.passPercent?.let {
                            Text(stringResource(R.string.pass_threshold_was, formatPercent(Math.round(it * 10) / 10.0)), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(stringResource(R.string.correct), e.correct.toString(), Modifier.weight(1f))
                    StatTile(stringResource(R.string.incorrect), e.incorrect.toString(), Modifier.weight(1f))
                    StatTile(stringResource(R.string.unanswered), e.unanswered.toString(), Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        stringResource(R.string.score), "${formatScore(e.score)} / ${formatScore(e.maxScore)}", Modifier.weight(1f),
                        supporting = if (d.config.scoring.penaltyPerWrong > 0) stringResource(R.string.penalty_applied, formatScore(d.config.scoring.penaltyPerWrong)) else null,
                    )
                    StatTile(
                        stringResource(R.string.time_spent), formatDuration(e.elapsedSec), Modifier.weight(1f),
                        supporting = e.timeLimitSec?.let { stringResource(R.string.of_limit, formatDuration(it)) },
                    )
                }
            }
            if (d.previous.isNotEmpty()) {
                item {
                    val last = d.previous.first()
                    val avg = d.previous.map { it.percent }.average()
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.comparison), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.vs_last_exam, signed(e.percent - last.percent)), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.vs_average, signed(e.percent - avg), formatPercent(Math.round(avg * 10) / 10.0), d.previous.size), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.by_topic)) }
            d.responses.groupBy { it.topicKey }.toSortedMap(compareBy { it.removePrefix("T").toIntOrNull() ?: 99 }).forEach { (key, list) ->
                item(key = "t-$key") {
                    val ok = list.count { it.isCorrect == true }
                    PercentBar(list.first().topicTitle, ok * 100.0 / list.size, "$ok/${list.size}")
                }
            }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            item {
                Button(onClick = { nav.navigate(Routes.examReview(examId)) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(stringResource(R.string.review_questions))
                }
            }
            if (e.incorrect + e.unanswered > 0) {
                item {
                    OutlinedButton(onClick = { nav.navigate(Routes.examReview(examId, "WRONG")) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.review_mistakes))
                    }
                }
            }
            item { OutlinedButton(onClick = vm::repeat, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.repeat_exam)) } }
            item {
                TextButton(onClick = { nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.back_home))
                }
            }
        }
    }
}

private fun signed(v: Double): String {
    val r = Math.round(v * 10) / 10.0
    return (if (r > 0) "+" else "") + formatPercent(r).replace(" %", " pts")
}
