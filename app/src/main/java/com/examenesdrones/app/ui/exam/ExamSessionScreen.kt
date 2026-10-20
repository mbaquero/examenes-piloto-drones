package com.examenesdrones.app.ui.exam

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.examenesdrones.app.AppContainer
import com.examenesdrones.app.R
import com.examenesdrones.app.data.StudyRepository
import com.examenesdrones.app.ui.components.AppScaffold
import com.examenesdrones.app.ui.components.LoadingBox
import com.examenesdrones.app.ui.components.OptionCard
import com.examenesdrones.app.ui.components.OptionState
import com.examenesdrones.app.ui.components.appViewModel
import com.examenesdrones.app.ui.components.formatDuration
import com.examenesdrones.app.ui.components.optionLetter
import com.examenesdrones.app.ui.navigation.Routes
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Exam question as shown during the exam: no correct answer, explanation or theory is loaded. */
data class ExamItem(val topicTitle: String, val text: String, val options: List<String>, val selected: Int?)

data class ExamUi(
    val items: List<ExamItem>,
    val index: Int,
    val elapsedSec: Int,
    val timeLimitSec: Int?,
) {
    val answered: Int get() = items.count { it.selected != null }
    val remainingSec: Int? get() = timeLimitSec?.let { (it - elapsedSec).coerceAtLeast(0) }
}

class ExamSessionViewModel(private val c: AppContainer, private val examId: Long) : ViewModel() {
    private val _ui = MutableStateFlow<ExamUi?>(null)
    val ui = _ui.asStateFlow()
    private val _done = MutableStateFlow(false)
    val done = _done.asStateFlow()
    private var ticker: Job? = null
    private var submitting = false

    init {
        viewModelScope.launch {
            val exam = c.study.exam(examId)
            if (exam == null || exam.status != StudyRepository.STATUS_IN_PROGRESS) { _done.value = true; return@launch }
            val items = c.study.examResponses(examId).map { r ->
                ExamItem(r.topicTitle, r.questionText, c.study.decodeOptions(r), r.selectedDisplayIndex)
            }
            _ui.value = ExamUi(items, exam.currentIndex.coerceIn(0, items.size - 1), exam.elapsedSec, exam.timeLimitSec)
            resume()
        }
    }

    /** The clock only runs while the exam screen is visible. */
    fun resume() {
        if (ticker?.isActive == true || _ui.value == null) return
        ticker = viewModelScope.launch {
            var lastSave = 0
            while (isActive) {
                delay(1000)
                val s = _ui.value ?: continue
                val next = s.copy(elapsedSec = s.elapsedSec + 1)
                _ui.value = next
                if (next.timeLimitSec != null && next.elapsedSec >= next.timeLimitSec) { submit(); break }
                if (next.elapsedSec - lastSave >= 5) { lastSave = next.elapsedSec; c.study.saveExamProgress(examId, next.index, next.elapsedSec) }
            }
        }
    }

    fun pause() {
        ticker?.cancel()
        val s = _ui.value ?: return
        viewModelScope.launch { withContext(NonCancellable) { c.study.saveExamProgress(examId, s.index, s.elapsedSec) } }
    }

    fun select(display: Int) {
        val s = _ui.value ?: return
        val newValue = if (s.items[s.index].selected == display) null else display // tap again to clear
        _ui.update { u -> u?.copy(items = u.items.toMutableList().also { it[u.index] = it[u.index].copy(selected = newValue) }) }
        viewModelScope.launch { c.study.setExamAnswer(examId, s.index, newValue) }
    }

    fun goTo(i: Int) {
        _ui.update { it?.copy(index = i.coerceIn(0, it.items.size - 1)) }
        _ui.value?.let { s -> viewModelScope.launch { c.study.saveExamProgress(examId, s.index, s.elapsedSec) } }
    }

    fun submit() {
        if (submitting) return
        submitting = true
        ticker?.cancel()
        val s = _ui.value ?: return
        viewModelScope.launch {
            withContext(NonCancellable) { c.study.submitExam(examId, s.elapsedSec) }
            _done.value = true
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExamSessionScreen(nav: NavHostController, examId: Long) {
    val vm = appViewModel(key = "exam-$examId") { ExamSessionViewModel(it, examId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val done by vm.done.collectAsStateWithLifecycle()
    var showOverview by remember { mutableStateOf(false) }
    var confirmSubmit by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }

    LaunchedEffect(done) {
        if (done) nav.navigate(Routes.examResult(examId)) { popUpTo(Routes.EXAM_SESSION) { inclusive = true } }
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> vm.resume()
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> vm.pause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    BackHandler { confirmExit = true }

    val s = ui
    AppScaffold(
        title = if (s != null) stringResource(R.string.question_x_of_n, s.index + 1, s.items.size) else stringResource(R.string.mode_exam_title),
        onBack = { confirmExit = true },
        actions = {
            if (s != null) {
                s.remainingSec?.let { rem ->
                    val low = rem <= 120
                    val timeLabel = stringResource(R.string.cd_time_remaining, formatDuration(rem))
                    AssistChip(
                        onClick = {}, label = { Text(formatDuration(rem)) },
                        leadingIcon = { Icon(Icons.Outlined.Timer, null, Modifier.size(18.dp)) },
                        colors = if (low) AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.errorContainer, labelColor = MaterialTheme.colorScheme.onErrorContainer) else AssistChipDefaults.assistChipColors(),
                        modifier = Modifier.semantics { contentDescription = timeLabel },
                    )
                } ?: Text(formatDuration(s.elapsedSec), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 8.dp))
                IconButton(onClick = { showOverview = true }) { Icon(Icons.Outlined.Apps, stringResource(R.string.exam_overview)) }
            }
        },
        bottomBar = {
            if (s != null) {
                Surface(tonalElevation = 3.dp) {
                    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { vm.goTo(s.index - 1) }, enabled = s.index > 0, modifier = Modifier.weight(1f).height(52.dp)) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text(stringResource(R.string.previous))
                        }
                        if (s.index + 1 < s.items.size) {
                            Button(onClick = { vm.goTo(s.index + 1) }, modifier = Modifier.weight(1f).height(52.dp)) {
                                Text(stringResource(R.string.next)); Spacer(Modifier.size(6.dp)); Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(18.dp))
                            }
                        } else {
                            Button(onClick = { showOverview = true }, modifier = Modifier.weight(1f).height(52.dp)) { Text(stringResource(R.string.review_and_submit)) }
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (s == null) { LoadingBox(Modifier.padding(padding)); return@AppScaffold }
        val item = s.items[s.index]
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(Modifier.widthIn(max = 720.dp)) {
                    LinearProgressIndicator(progress = { s.answered.toFloat() / s.items.size }, modifier = Modifier.fillMaxWidth(), drawStopIndicator = {})
                    Text(
                        pluralStringResource(R.plurals.answered_of, s.items.size, s.answered, s.items.size),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            item {
                Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                    Text(item.topicTitle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(item.text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))
                }
            }
            item.options.forEachIndexed { i, o ->
                item(key = "o-${s.index}-$i") {
                    OptionCard(
                        optionLetter(i), o, if (item.selected == i) OptionState.SELECTED else OptionState.IDLE,
                        onClick = { vm.select(i) }, modifier = Modifier.widthIn(max = 720.dp),
                    )
                }
            }
            item {
                Text(stringResource(R.string.exam_change_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showOverview && s != null) {
        AlertDialog(
            onDismissRequest = { showOverview = false },
            title = { Text(stringResource(R.string.exam_overview)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(pluralStringResource(R.plurals.answered_of, s.items.size, s.answered, s.items.size), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        s.items.forEachIndexed { i, it ->
                            val answered = it.selected != null
                            val desc = stringResource(if (answered) R.string.cd_question_answered else R.string.cd_question_unanswered, i + 1)
                            Surface(
                                onClick = { vm.goTo(i); showOverview = false },
                                shape = CircleShape,
                                color = if (answered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = if (answered) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                border = if (i == s.index) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary) else null,
                                modifier = Modifier.size(44.dp).semantics { contentDescription = desc },
                            ) { Box(contentAlignment = Alignment.Center) { Text("${i + 1}") } }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.overview_legend), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { Button(onClick = { showOverview = false; confirmSubmit = true }) { Text(stringResource(R.string.submit_exam)) } },
            dismissButton = { TextButton(onClick = { showOverview = false }) { Text(stringResource(R.string.keep_answering)) } },
        )
    }

    if (confirmSubmit && s != null) {
        val unanswered = s.items.size - s.answered
        AlertDialog(
            onDismissRequest = { confirmSubmit = false },
            title = { Text(stringResource(R.string.submit_exam_title)) },
            text = {
                Text(
                    if (unanswered > 0) pluralStringResource(R.plurals.submit_with_unanswered, unanswered, unanswered)
                    else stringResource(R.string.submit_all_answered),
                )
            },
            confirmButton = { Button(onClick = { confirmSubmit = false; vm.submit() }) { Text(stringResource(R.string.submit)) } },
            dismissButton = { TextButton(onClick = { confirmSubmit = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text(stringResource(R.string.exit_exam_title)) },
            text = { Text(stringResource(R.string.exit_exam_message)) },
            confirmButton = { TextButton(onClick = { confirmExit = false; vm.pause(); nav.popBackStack() }) { Text(stringResource(R.string.exit_continue_later)) } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
