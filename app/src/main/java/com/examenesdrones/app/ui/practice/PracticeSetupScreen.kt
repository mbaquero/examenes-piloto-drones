package com.examenesdrones.app.ui.practice

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
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.examenesdrones.app.data.db.TopicEntity
import com.examenesdrones.app.domain.PracticeConfig
import com.examenesdrones.app.domain.PracticeMode
import com.examenesdrones.app.ui.components.AppScaffold
import com.examenesdrones.app.ui.components.EmptyState
import com.examenesdrones.app.ui.components.ErrorCard
import com.examenesdrones.app.ui.components.LoadingBox
import com.examenesdrones.app.ui.components.NoticeCard
import com.examenesdrones.app.ui.components.SectionTitle
import com.examenesdrones.app.ui.components.appViewModel
import com.examenesdrones.app.ui.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class PracticeSetupData(
    val topics: List<TopicEntity>,
    val total: Int,
    val defaultCount: Int,
    val prioritizeWeak: Boolean,
    val shuffleOptions: Boolean,
)

class PracticeSetupViewModel(private val c: AppContainer) : ViewModel() {
    private val _data = MutableStateFlow<PracticeSetupData?>(null)
    val data = _data.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _started = MutableStateFlow<Long?>(null)
    val started = _started.asStateFlow()

    init {
        viewModelScope.launch {
            val s = c.settings.current()
            val topics = c.content.topicsOnce().filter { it.questionCount > 0 }
            _data.value = PracticeSetupData(topics, topics.sumOf { it.questionCount }, s.practiceCount, s.prioritizeWeak, s.shuffleOptions)
        }
    }

    fun start(config: PracticeConfig) = viewModelScope.launch {
        try {
            _error.value = null
            _started.value = c.study.startPractice(config)
            c.settings.update { it.copy(practiceCount = if (config.count > 0) config.count else it.practiceCount, prioritizeWeak = config.prioritizeWeak, shuffleOptions = config.shuffleOptions) }
        } catch (e: InsufficientQuestionsException) {
            _error.value = "No hay preguntas disponibles para la selección realizada."
        }
    }

    fun consumed() { _started.value = null }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PracticeSetupScreen(nav: NavHostController, preselected: String) {
    val vm = appViewModel { PracticeSetupViewModel(it) }
    val data by vm.data.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val started by vm.started.collectAsStateWithLifecycle()

    LaunchedEffect(started) {
        started?.let { id -> vm.consumed(); nav.navigate(Routes.practiceSession(id)) }
    }

    AppScaffold(title = stringResource(R.string.mode_practice_title), tabScreen = preselected.isEmpty(), onBack = if (preselected.isEmpty()) null else ({ nav.popBackStack() })) { padding ->
        val d = data
        if (d == null) { LoadingBox(Modifier.padding(padding)); return@AppScaffold }
        if (d.total == 0) {
            EmptyState(Icons.Outlined.Quiz, stringResource(R.string.no_questions_title), stringResource(R.string.no_questions_message), Modifier.padding(padding))
            return@AppScaffold
        }
        val initialTopics = preselected.split(',').filter { it.isNotBlank() }
        var byTopic by rememberSaveable { mutableStateOf(initialTopics.isNotEmpty()) }
        var selected by rememberSaveable { mutableStateOf(initialTopics.toSet().toList()) }
        var count by rememberSaveable { mutableStateOf(d.defaultCount) }
        var all by rememberSaveable { mutableStateOf(false) }
        var prioritize by rememberSaveable { mutableStateOf(d.prioritizeWeak) }
        var shuffle by rememberSaveable { mutableStateOf(true) }
        var shuffleOptions by rememberSaveable { mutableStateOf(d.shuffleOptions) }

        val available = if (byTopic) d.topics.filter { it.key in selected }.sumOf { it.questionCount } else d.total
        val effective = if (all) available else count.coerceAtMost(available)

        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { NoticeCard(stringResource(R.string.questions_disclaimer)) }
            item { SectionTitle(stringResource(R.string.practice_type)) }
            item {
                ModeOption(!byTopic, stringResource(R.string.practice_random), stringResource(R.string.practice_random_desc, d.total)) { byTopic = false }
            }
            item {
                ModeOption(byTopic, stringResource(R.string.practice_by_topic), stringResource(R.string.practice_by_topic_desc)) { byTopic = true }
            }
            if (byTopic) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.select_topics), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { selected = if (selected.size == d.topics.size) emptyList() else d.topics.map { it.key } }) {
                            Text(stringResource(if (selected.size == d.topics.size) R.string.select_none else R.string.select_all))
                        }
                    }
                }
                d.topics.forEach { t ->
                    item(key = t.key) {
                        Row(
                            Modifier.fillMaxWidth().toggleable(t.key in selected, role = Role.Checkbox) { on ->
                                selected = if (on) selected + t.key else selected - t.key
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(t.key in selected, null)
                            Text("${t.code?.let { "$it · " } ?: ""}${t.title}", Modifier.weight(1f).padding(start = 8.dp))
                            Text(t.questionCount.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.number_of_questions)) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 20, 30, 50).forEach { n ->
                        FilterChip(!all && count == n, { all = false; count = n }, { Text(n.toString()) })
                    }
                    FilterChip(all, { all = true }, { Text(stringResource(R.string.all_questions_n, available)) })
                }
            }
            if (!all && available > 1) {
                item {
                    Column {
                        Slider(
                            value = count.coerceIn(1, available).toFloat(), onValueChange = { count = it.roundToInt() },
                            valueRange = 1f..available.toFloat(),
                        )
                        Text(stringResource(R.string.questions_selected, effective, available), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.options)) }
            item { SwitchRow(stringResource(R.string.prioritize_weak), stringResource(R.string.prioritize_weak_desc), prioritize) { prioritize = it } }
            item { SwitchRow(stringResource(R.string.shuffle_questions), null, shuffle) { shuffle = it } }
            item { SwitchRow(stringResource(R.string.shuffle_options), stringResource(R.string.shuffle_options_desc), shuffleOptions) { shuffleOptions = it } }
            error?.let { e -> item { ErrorCard(e) } }
            item {
                Button(
                    enabled = effective > 0,
                    onClick = {
                        vm.start(
                            PracticeConfig(
                                mode = if (byTopic) PracticeMode.TOPICS else PracticeMode.RANDOM,
                                topicKeys = if (byTopic) selected else emptyList(),
                                count = if (all) 0 else effective, prioritizeWeak = prioritize,
                                shuffleQuestions = shuffle, shuffleOptions = shuffleOptions,
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(52.dp),
                ) { Text(if (effective > 0) stringResource(R.string.start_practice_n, effective) else stringResource(R.string.select_topic_first)) }
            }
        }
    }
}

@Composable
private fun ModeOption(selected: Boolean, title: String, desc: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected, null)
            Column(Modifier.padding(start = 8.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(desc, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun SwitchRow(title: String, desc: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (desc != null) Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, null, modifier = Modifier.padding(start = 12.dp))
    }
}
