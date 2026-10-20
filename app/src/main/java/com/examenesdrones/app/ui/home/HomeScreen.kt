package com.examenesdrones.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.examenesdrones.app.AppContainer
import com.examenesdrones.app.R
import com.examenesdrones.app.data.db.ExamAttemptEntity
import com.examenesdrones.app.data.db.ImportedDocumentEntity
import com.examenesdrones.app.data.db.PracticeSessionEntity
import com.examenesdrones.app.data.db.ReadingProgressEntity
import com.examenesdrones.app.data.db.TopicEntity
import com.examenesdrones.app.ui.components.AppScaffold
import com.examenesdrones.app.ui.components.EmptyState
import com.examenesdrones.app.ui.components.LoadingBox
import com.examenesdrones.app.ui.components.SectionTitle
import com.examenesdrones.app.ui.components.StatTile
import com.examenesdrones.app.ui.components.appViewModel
import com.examenesdrones.app.ui.components.formatDateTime
import com.examenesdrones.app.ui.components.formatPercent
import com.examenesdrones.app.ui.navigation.Routes
import com.examenesdrones.app.ui.navigation.navigateTab
import com.examenesdrones.app.ui.theme.LocalFeedbackColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeState(
    val loading: Boolean = true,
    val document: ImportedDocumentEntity? = null,
    val topics: List<TopicEntity> = emptyList(),
    val questionCount: Int = 0,
    val readingPercent: Double = 0.0,
    val masteryPercent: Double = 0.0,
    val topicsCompleted: Int = 0,
    val studyTopics: Int = 0,
    val recentExams: List<ExamAttemptEntity> = emptyList(),
    val practiceInProgress: PracticeSessionEntity? = null,
    val examInProgress: ExamAttemptEntity? = null,
    val lastRead: ReadingProgressEntity? = null,
    val lastReadTitle: String? = null,
)

class HomeViewModel(c: AppContainer) : ViewModel() {
    private val base = combine(
        c.content.activeDocument, c.content.topics, c.content.usableQuestionCount, c.user.readingProgress, c.study.questionStats,
    ) { doc, topics, count, reading, stats ->
        val study = topics.filter { it.blockCount > 0 }
        val readingByKey = reading.associateBy { it.topicKey }
        val readingPercent = if (study.isEmpty()) 0.0 else study.sumOf { t ->
            val r = readingByKey[t.key]
            when {
                r == null -> 0.0
                r.completed -> 1.0
                else -> ((r.maxSeenIndex + 1).toDouble() / t.blockCount.coerceAtLeast(1)).coerceIn(0.0, 1.0)
            }
        } * 100.0 / study.size
        val mastered = stats.count { it.lastCorrect }
        HomeState(
            loading = false, document = doc, topics = topics, questionCount = count,
            readingPercent = readingPercent,
            masteryPercent = if (count == 0) 0.0 else (mastered.coerceAtMost(count) * 100.0 / count),
            topicsCompleted = study.count { readingByKey[it.key]?.completed == true },
            studyTopics = study.size,
            lastRead = reading.maxByOrNull { it.updatedAt },
        )
    }

    val state: StateFlow<HomeState> = combine(
        base, c.study.submittedExams, c.study.practiceInProgress, c.study.examInProgress,
    ) { s, exams, practice, exam ->
        s.copy(
            recentExams = exams.take(3), practiceInProgress = practice, examInProgress = exam,
            lastReadTitle = s.lastRead?.let { r -> s.topics.firstOrNull { it.key == r.topicKey }?.title },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())
}

@Composable
fun HomeScreen(nav: NavHostController) {
    val vm = appViewModel { HomeViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()

    AppScaffold(
        title = stringResource(R.string.app_name),
        tabScreen = true,
        actions = {
            IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Icon(Icons.Outlined.Settings, stringResource(R.string.settings)) }
        },
    ) { padding ->
        when {
            s.loading -> LoadingBox(Modifier.padding(padding))
            else -> HomeContent(s, nav, Modifier.padding(padding))
        }
    }
}

@Composable
private fun HomeContent(s: HomeState, nav: NavHostController, modifier: Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ProgressHeader(s) }
        resumeItems(s, nav)
        item { SectionTitle(stringResource(R.string.home_modes)) }
        item {
            ModeCard(
                Icons.AutoMirrored.Outlined.MenuBook, stringResource(R.string.mode_study_title), stringResource(R.string.mode_study_desc),
                MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer,
            ) { nav.navigateTab(Routes.TOPICS) }
        }
        item {
            ModeCard(
                Icons.Outlined.Quiz, stringResource(R.string.mode_practice_title), stringResource(R.string.mode_practice_desc),
                MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer,
            ) { nav.navigateTab(Routes.practiceSetup(emptyList())) }
        }
        item {
            ModeCard(
                Icons.Outlined.TaskAlt, stringResource(R.string.mode_exam_title), stringResource(R.string.mode_exam_desc),
                MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer,
            ) { nav.navigateTab(Routes.EXAM_SETUP) }
        }
        item {
            SectionTitle(stringResource(R.string.home_recent_exams)) {
                TextButton(onClick = { nav.navigateTab(Routes.PROGRESS) }) {
                    Icon(Icons.Outlined.History, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text(stringResource(R.string.see_history))
                }
            }
        }
        if (s.recentExams.isEmpty()) {
            item { Text(stringResource(R.string.home_no_exams), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(s.recentExams, key = { it.id }) { e -> RecentExamRow(e) { nav.navigate(Routes.examResult(e.id)) } }
        }
    }
}

@Composable
private fun ProgressHeader(s: HomeState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) {
            Column(Modifier.padding(20.dp).fillMaxWidth()) {
                Text(stringResource(R.string.home_overall_progress), style = MaterialTheme.typography.labelLarge)
                val overall = (s.readingPercent + s.masteryPercent) / 2
                Text(formatPercent(Math.round(overall).toDouble()), style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (overall / 100).toFloat() }, modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = MaterialTheme.colorScheme.onPrimary, trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f),
                    drawStopIndicator = {},
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.home_progress_detail, formatPercent(Math.round(s.readingPercent).toDouble()), formatPercent(Math.round(s.masteryPercent).toDouble())),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(stringResource(R.string.home_questions_available), s.questionCount.toString(), Modifier.weight(1f))
            StatTile(stringResource(R.string.home_topics_completed), "${s.topicsCompleted}/${s.studyTopics}", Modifier.weight(1f))
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.resumeItems(s: HomeState, nav: NavHostController) {
    s.examInProgress?.let { e ->
        item(key = "exam-resume") {
            ResumeCard(stringResource(R.string.resume_exam), stringResource(R.string.resume_exam_detail, e.currentIndex + 1, e.questionCount)) {
                nav.navigate(Routes.examSession(e.id))
            }
        }
    }
    s.practiceInProgress?.let { p ->
        item(key = "practice-resume") {
            ResumeCard(stringResource(R.string.resume_practice), stringResource(R.string.resume_practice_detail, (p.currentIndex + 1).coerceAtMost(p.total), p.total)) {
                nav.navigate(Routes.practiceSession(p.id))
            }
        }
    }
    val last = s.lastRead
    if (last != null && s.lastReadTitle != null && !last.completed) {
        item(key = "read-resume") {
            ResumeCard(stringResource(R.string.resume_reading), s.lastReadTitle) { nav.navigate(Routes.reader(last.topicKey)) }
        }
    }
}

@Composable
private fun ResumeCard(title: String, detail: String, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.PlayCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ModeCard(icon: ImageVector, title: String, desc: String, container: Color, content: Color, onClick: () -> Unit) {
    Card(
        onClick = onClick, modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(40.dp))
            Column(Modifier.padding(start = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(desc, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun RecentExamRow(e: ExamAttemptEntity, onClick: () -> Unit) {
    val fb = LocalFeedbackColors.current
    Card(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(formatDateTime(e.submittedAt ?: e.startedAt), style = MaterialTheme.typography.titleSmall)
                Text(
                    pluralStringResource(R.plurals.exam_row_detail, e.questionCount, e.correct, e.questionCount),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatPercent(Math.round(e.percent * 10) / 10.0), style = MaterialTheme.typography.titleMedium)
                e.passed?.let { passed ->
                    Text(
                        stringResource(if (passed) R.string.passed else R.string.failed),
                        style = MaterialTheme.typography.labelMedium, color = if (passed) fb.correct else fb.wrong,
                    )
                }
            }
        }
    }
}
