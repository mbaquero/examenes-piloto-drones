package com.examenesdrones.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.examenesdrones.app.BuildConfig
import com.examenesdrones.app.R
import com.examenesdrones.app.data.ThemeMode
import com.examenesdrones.app.data.UserSettings
import com.examenesdrones.app.ui.components.AppScaffold
import com.examenesdrones.app.ui.components.SectionTitle
import com.examenesdrones.app.ui.components.appContainer
import com.examenesdrones.app.ui.components.formatDateTime
import com.examenesdrones.app.ui.components.formatPercent
import com.examenesdrones.app.ui.navigation.Routes
import com.examenesdrones.app.ui.practice.SwitchRow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(nav: NavHostController) {
    val c = appContainer()
    val settings by c.settings.settings.collectAsStateWithLifecycle(UserSettings())
    val doc by c.content.activeDocument.collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmReset by remember { mutableStateOf(false) }
    val exported = stringResource(R.string.export_done)
    val exportFailed = stringResource(R.string.export_failed)
    val resetDone = stringResource(R.string.reset_done)

    fun update(f: (UserSettings) -> UserSettings) = scope.launch { c.settings.update(f) }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            runCatching { c.export.writeCsv(uri) }.fold({ snackbar.showSnackbar(exported) }, { snackbar.showSnackbar(exportFailed) })
        }
    }
    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching { c.export.writeJson(uri) }.fold({ snackbar.showSnackbar(exported) }, { snackbar.showSnackbar(exportFailed) })
        }
    }

    AppScaffold(title = stringResource(R.string.settings), onBack = { nav.popBackStack() }, snackbar = snackbar) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { SectionTitle(stringResource(R.string.appearance)) }
            item {
                Text(stringResource(R.string.theme), style = MaterialTheme.typography.bodyLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ThemeMode.SYSTEM to R.string.theme_system, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark).forEach { (m, l) ->
                        FilterChip(settings.theme == m, { update { it.copy(theme = m) } }, { Text(stringResource(l)) })
                    }
                }
            }
            item {
                Text(stringResource(R.string.language), style = MaterialTheme.typography.bodyLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("" to R.string.language_system, "es" to R.string.language_es).forEach { (tag, l) ->
                        FilterChip(settings.language == tag, { update { it.copy(language = tag) } }, { Text(stringResource(l)) })
                    }
                }
            }
            item {
                Column {
                    Text(stringResource(R.string.reading_text_size, (settings.textScale * 100).roundToInt()), style = MaterialTheme.typography.bodyLarge)
                    Slider(settings.textScale, { v -> update { it.copy(textScale = (v * 20).roundToInt() / 20f) } }, valueRange = 0.8f..1.6f, steps = 15)
                }
            }

            item { HorizontalDivider(); SectionTitle(stringResource(R.string.mode_practice_title)) }
            item {
                Text(stringResource(R.string.default_practice_count, settings.practiceCount), style = MaterialTheme.typography.bodyLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 20, 30, 50).forEach { n -> FilterChip(settings.practiceCount == n, { update { it.copy(practiceCount = n) } }, { Text("$n") }) }
                }
            }
            item { SwitchRow(stringResource(R.string.prioritize_weak), stringResource(R.string.prioritize_weak_desc), settings.prioritizeWeak) { v -> update { it.copy(prioritizeWeak = v) } } }
            item { SwitchRow(stringResource(R.string.shuffle_options), stringResource(R.string.shuffle_options_desc), settings.shuffleOptions) { v -> update { it.copy(shuffleOptions = v) } } }

            item { HorizontalDivider(); SectionTitle(stringResource(R.string.mode_exam_title)) }
            item { Text(stringResource(R.string.exam_defaults_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                Text(stringResource(R.string.default_exam_length), style = MaterialTheme.typography.bodyLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(settings.examCount == 0, { update { it.copy(examCount = 0) } }, { Text(stringResource(R.string.official)) })
                    listOf(10, 20, 30, 40, 50).forEach { n -> FilterChip(settings.examCount == n, { update { it.copy(examCount = n) } }, { Text("$n") }) }
                }
            }
            item { SwitchRow(stringResource(R.string.exam_timer), null, settings.examTimerEnabled) { v -> update { it.copy(examTimerEnabled = v) } } }
            if (settings.examTimerEnabled) {
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(settings.examTimerMinutes == 0, { update { it.copy(examTimerMinutes = 0) } }, { Text(stringResource(R.string.official)) })
                        listOf(10, 20, 30, 45, 60).forEach { m -> FilterChip(settings.examTimerMinutes == m, { update { it.copy(examTimerMinutes = m) } }, { Text(stringResource(R.string.minutes_n, m)) }) }
                    }
                }
            }
            item {
                Column {
                    val label = if (settings.passPercent < 0) stringResource(R.string.official) else formatPercent(settings.passPercent.roundToInt().toDouble())
                    Text(stringResource(R.string.pass_threshold, label), style = MaterialTheme.typography.bodyLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(settings.passPercent < 0, { update { it.copy(passPercent = -1.0) } }, { Text(stringResource(R.string.official)) })
                        listOf(50.0, 60.0, 70.0, 80.0, 90.0).forEach { p -> FilterChip(settings.passPercent == p, { update { it.copy(passPercent = p) } }, { Text(formatPercent(p)) }) }
                    }
                }
            }
            item {
                Text(stringResource(R.string.penalty_per_wrong), style = MaterialTheme.typography.bodyLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.0 to "0", 0.25 to "0,25", 1.0 / 3 to "0,33", 0.5 to "0,5").forEach { (v, l) ->
                        FilterChip(kotlin.math.abs(settings.penaltyPerWrong - v) < 1e-6, { update { it.copy(penaltyPerWrong = v) } }, { Text(l) })
                    }
                }
                Text(stringResource(R.string.penalty_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            item { HorizontalDivider(); SectionTitle(stringResource(R.string.study_material)) }
            item {
                val d = doc
                Text(
                    if (d == null) stringResource(R.string.no_document)
                    else stringResource(R.string.document_summary, d.topicCount, d.questionCount),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item { HorizontalDivider(); SectionTitle(stringResource(R.string.data)) }
            item { OutlinedButton(onClick = { csvLauncher.launch("historial_examenes.csv") }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_csv)) } }
            item { OutlinedButton(onClick = { jsonLauncher.launch("historial_examenes.json") }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_json)) } }
            item {
                Button(
                    onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                ) { Text(stringResource(R.string.reset_progress)) }
            }
            item {
                Text(
                    stringResource(R.string.version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }

    if (confirmReset) {
        var typed by remember { mutableStateOf("") }
        val keyword = stringResource(R.string.reset_keyword)
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.reset_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.reset_message, keyword))
                    OutlinedTextField(typed, { typed = it }, singleLine = true, label = { Text(keyword) })
                }
            },
            confirmButton = {
                TextButton(
                    enabled = typed.trim().equals(keyword, ignoreCase = true),
                    onClick = {
                        confirmReset = false
                        scope.launch { c.study.resetProgress(); snackbar.showSnackbar(resetDone) }
                    },
                ) { Text(stringResource(R.string.reset_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
