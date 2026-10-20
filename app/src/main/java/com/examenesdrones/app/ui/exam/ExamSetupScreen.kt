package com.examenesdrones.app.ui.exam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.examenesdrones.app.AppContainer
import com.examenesdrones.app.R
import com.examenesdrones.app.data.InsufficientQuestionsException
import com.examenesdrones.app.data.UserSettings
import com.examenesdrones.app.data.db.TopicEntity
import com.examenesdrones.app.domain.ExamConfig
import com.examenesdrones.app.domain.ExamPresetData
import com.examenesdrones.app.domain.ScoringRules
import com.examenesdrones.app.domain.Scoring
import com.examenesdrones.app.ui.components.AppScaffold
import com.examenesdrones.app.ui.components.EmptyState
import com.examenesdrones.app.ui.components.ErrorCard
import com.examenesdrones.app.ui.components.LoadingBox
import com.examenesdrones.app.ui.components.NoticeCard
import com.examenesdrones.app.ui.components.SectionTitle
import com.examenesdrones.app.ui.components.appViewModel
import com.examenesdrones.app.ui.components.formatPercent
import com.examenesdrones.app.ui.navigation.Routes
import com.examenesdrones.app.ui.practice.SwitchRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class ExamSetupData(
    val topics: List<TopicEntity>,
    val pool: Int,
    val preset: ExamPresetData?,
    val settings: UserSettings,
)

/** Builds the official configuration from the PDF's preset, or null if the PDF does not define one. */
fun officialConfig(preset: ExamPresetData?, shuffleOptions: Boolean): ExamConfig? = preset?.let {
    ExamConfig(
        questionCount = it.questionCount, perTopic = it.perTopic, randomSelection = true, shuffleQuestions = true,
        shuffleOptions = shuffleOptions, timeLimitMinutes = it.timeLimitMinutes,
        scoring = ScoringRules(1.0, 0.0, it.passMark?.let { m -> Scoring.passPercentFor(m, it.questionCount) }),
        presetName = "OFFICIAL",
    )
}

class ExamSetupViewModel(private val c: AppContainer) : ViewModel() {
    private val _data = MutableStateFlow<ExamSetupData?>(null)
    val data = _data.asStateFlow()
    private val _started = MutableStateFlow<Long?>(null)
    val started = _started.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init {
        viewModelScope.launch {
            val topics = c.content.topicsOnce().filter { it.questionCount > 0 }
            _data.value = ExamSetupData(topics, topics.sumOf { it.questionCount }, c.content.preset(), c.settings.current())
        }
    }

    fun start(config: ExamConfig) = viewModelScope.launch {
        _error.value = null
        try {
            val titles = c.content.topicsOnce().associate { it.key to it.title }
            _started.value = c.study.startExam(config, titles)
        } catch (e: InsufficientQuestionsException) {
            _error.value = "No hay preguntas disponibles para la selección realizada."
        }
    }

    fun consumed() { _started.value = null }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExamSetupScreen(nav: NavHostController) {
    val vm = appViewModel { ExamSetupViewModel(it) }
    val data by vm.data.collectAsStateWithLifecycle()
    val started by vm.started.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    LaunchedEffect(started) { started?.let { vm.consumed(); nav.navigate(Routes.examSession(it)) } }

    AppScaffold(title = stringResource(R.string.mode_exam_title), tabScreen = true) { padding ->
        val d = data
        if (d == null) { LoadingBox(Modifier.padding(padding)); return@AppScaffold }
        if (d.pool == 0) {
            EmptyState(Icons.Outlined.TaskAlt, stringResource(R.string.no_questions_title), stringResource(R.string.no_questions_message), Modifier.padding(padding))
            return@AppScaffold
        }
        val st = d.settings
        val official = officialConfig(d.preset, st.shuffleOptions)
        var useOfficial by rememberSaveable { mutableStateOf(official != null) }
        val defaultCount = when {
            st.examCount > 0 -> st.examCount
            d.preset != null -> d.preset.questionCount
            else -> 20
        }.coerceAtMost(d.pool)
        var count by rememberSaveable { mutableStateOf(defaultCount) }
        var wholeSyllabus by rememberSaveable { mutableStateOf(true) }
        var selected by rememberSaveable { mutableStateOf(d.topics.map { it.key }) }
        var randomSelection by rememberSaveable { mutableStateOf(true) }
        var shuffleOptions by rememberSaveable { mutableStateOf(st.shuffleOptions) }
        var timer by rememberSaveable { mutableStateOf(st.examTimerEnabled) }
        val defaultMinutes = if (st.examTimerMinutes > 0) st.examTimerMinutes else d.preset?.timeLimitMinutes ?: 20
        var minutes by rememberSaveable { mutableStateOf(defaultMinutes) }
        val defaultPass = when {
            st.passPercent >= 0 -> st.passPercent
            d.preset?.passMark != null -> Scoring.passPercentFor(d.preset.passMark, d.preset.questionCount)
            else -> 50.0
        }
        var passPercent by rememberSaveable { mutableStateOf(defaultPass) }
        var penalty by rememberSaveable { mutableStateOf(st.penaltyPerWrong) }

        val pool = if (wholeSyllabus) d.pool else d.topics.filter { it.key in selected }.sumOf { it.questionCount }
        val effectiveCount = count.coerceIn(0, pool)

        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { NoticeCard(stringResource(R.string.questions_disclaimer)) }
            if (official != null) {
                item {
                    PresetCard(
                        selected = useOfficial, title = stringResource(R.string.exam_official),
                        desc = stringResource(
                            R.string.exam_official_desc, official.questionCount,
                            official.timeLimitMinutes ?: 0, d.preset?.passMark ?: 0,
                        ),
                    ) { useOfficial = true }
                }
                if (official.perTopic.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.exam_distribution, official.perTopic.entries.joinToString(" · ") { (k, v) ->
                                "${d.topics.firstOrNull { it.key == k }?.code ?: k}: $v"
                            }),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
            }
            item { PresetCard(!useOfficial, stringResource(R.string.exam_custom), stringResource(R.string.exam_custom_desc)) { useOfficial = false } }

            if (!useOfficial) {
                item { SectionTitle(stringResource(R.string.exam_scope)) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(wholeSyllabus, { wholeSyllabus = true }, { Text(stringResource(R.string.whole_syllabus)) })
                        FilterChip(!wholeSyllabus, { wholeSyllabus = false }, { Text(stringResource(R.string.selected_topics)) })
                    }
                }
                if (!wholeSyllabus) {
                    d.topics.forEach { t ->
                        item(key = t.key) {
                            Row(
                                Modifier.fillMaxWidth().toggleable(t.key in selected, role = Role.Checkbox) { on ->
                                    selected = if (on) selected + t.key else selected - t.key
                                },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(t.key in selected, null)
                                Text("${t.code?.let { "$it · " } ?: ""}${t.title}", Modifier.weight(1f).padding(start = 8.dp))
                                Text(t.questionCount.toString(), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                item { SectionTitle(stringResource(R.string.number_of_questions)) }
                item {
                    Column {
                        Slider(
                            value = effectiveCount.coerceAtLeast(1).toFloat(), onValueChange = { count = it.roundToInt() },
                            valueRange = 1f..pool.coerceAtLeast(2).toFloat(), enabled = pool > 1,
                        )
                        Text(stringResource(R.string.questions_selected, effectiveCount, pool), style = MaterialTheme.typography.bodySmall)
                        if (count > pool) Text(stringResource(R.string.exam_count_limited, pool), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                item { SwitchRow(stringResource(R.string.random_selection), stringResource(R.string.random_selection_desc), randomSelection) { randomSelection = it } }
                item { SwitchRow(stringResource(R.string.shuffle_options), stringResource(R.string.shuffle_options_desc), shuffleOptions) { shuffleOptions = it } }
                item { SwitchRow(stringResource(R.string.time_limit), null, timer) { timer = it } }
                if (timer) {
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(10, 20, 30, 45, 60).forEach { m -> FilterChip(minutes == m, { minutes = m }, { Text(stringResource(R.string.minutes_n, m)) }) }
                        }
                    }
                }
                item { SectionTitle(stringResource(R.string.scoring)) }
                item {
                    Text(stringResource(R.string.penalty_per_wrong), style = MaterialTheme.typography.bodyLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.0 to "0", 0.25 to "0,25", 1.0 / 3 to "0,33", 0.5 to "0,5", 1.0 to "1").forEach { (v, label) ->
                            FilterChip(kotlin.math.abs(penalty - v) < 1e-6, { penalty = v }, { Text(label) })
                        }
                    }
                    Text(stringResource(R.string.penalty_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    Column {
                        Text(stringResource(R.string.pass_threshold, formatPercent(passPercent.roundToInt().toDouble())), style = MaterialTheme.typography.bodyLarge)
                        Slider(value = passPercent.toFloat(), onValueChange = { passPercent = (it / 5).roundToInt() * 5.0 }, valueRange = 0f..100f, steps = 19)
                    }
                }
            }
            error?.let { item { ErrorCard(it) } }
            item {
                Text(stringResource(R.string.exam_rules_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                val config = if (useOfficial && official != null) official else ExamConfig(
                    questionCount = effectiveCount, topicKeys = if (wholeSyllabus) emptyList() else selected,
                    randomSelection = randomSelection, shuffleQuestions = randomSelection, shuffleOptions = shuffleOptions,
                    timeLimitMinutes = if (timer) minutes else null,
                    scoring = ScoringRules(1.0, penalty, passPercent), presetName = null,
                )
                Button(
                    onClick = { vm.start(config) }, enabled = config.questionCount > 0 && (config.topicKeys.isNotEmpty() || wholeSyllabus || useOfficial),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(stringResource(R.string.start_exam_n, config.questionCount.coerceAtMost(d.pool))) }
            }
        }
    }
}

@Composable
private fun PresetCard(selected: Boolean, title: String, desc: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected, null)
            Column(Modifier.padding(start = 8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(desc, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
