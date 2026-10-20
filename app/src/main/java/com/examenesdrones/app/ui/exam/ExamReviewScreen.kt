package com.examenesdrones.app.ui.exam

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.examenesdrones.app.data.StudyRepository
import com.examenesdrones.app.data.db.ExamResponseEntity
import com.examenesdrones.app.ui.components.AppScaffold
import com.examenesdrones.app.ui.components.ExplanationCard
import com.examenesdrones.app.ui.components.LoadingBox
import com.examenesdrones.app.ui.components.OptionCard
import com.examenesdrones.app.ui.components.OptionState
import com.examenesdrones.app.ui.components.RelatedTheoryCard
import com.examenesdrones.app.ui.components.appViewModel
import com.examenesdrones.app.ui.components.optionLetter
import com.examenesdrones.app.ui.navigation.Routes
import com.examenesdrones.app.ui.theme.LocalFeedbackColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReviewItem(
    val response: ExamResponseEntity,
    val options: List<String>,
    val theory: List<String>,
    /** Whether the topic still exists in the current study material (for the theory shortcut). */
    val topicAvailable: Boolean,
)

class ExamReviewViewModel(private val c: AppContainer, private val examId: Long) : ViewModel() {
    private val _items = MutableStateFlow<List<ReviewItem>?>(null)
    val items = _items.asStateFlow()

    init {
        viewModelScope.launch {
            val exam = c.study.exam(examId)
            // Answers are only revealed for submitted exams.
            if (exam?.status != StudyRepository.STATUS_SUBMITTED) { _items.value = emptyList(); return@launch }
            val topics = c.content.topicsOnce().map { it.key }.toSet()
            _items.value = c.study.examResponses(examId).map { r ->
                ReviewItem(r, c.study.decodeOptions(r), c.study.decodeTheory(r), r.topicKey in topics)
            }
        }
    }
}

enum class ReviewFilter { ALL, WRONG, CORRECT, UNANSWERED }

@Composable
fun ExamReviewScreen(nav: NavHostController, examId: Long, initialFilter: String) {
    val vm = appViewModel(key = "review-$examId") { ExamReviewViewModel(it, examId) }
    val items by vm.items.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(runCatching { ReviewFilter.valueOf(initialFilter) }.getOrDefault(ReviewFilter.ALL)) }
    // Kept across navigation to the reader, so the user returns to the same place.
    val listState = rememberLazyListState()

    AppScaffold(title = stringResource(R.string.exam_review), onBack = { nav.popBackStack() }) { padding ->
        val all = items
        if (all == null) { LoadingBox(Modifier.padding(padding)); return@AppScaffold }
        val shown = all.filter {
            when (filter) {
                ReviewFilter.ALL -> true
                ReviewFilter.WRONG -> it.response.isCorrect != true
                ReviewFilter.CORRECT -> it.response.isCorrect == true
                ReviewFilter.UNANSWERED -> it.response.selectedDisplayIndex == null
            }
        }
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(), state = listState, contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(filter == ReviewFilter.ALL, { filter = ReviewFilter.ALL }, { Text(stringResource(R.string.filter_all_count, all.size)) })
                    FilterChip(filter == ReviewFilter.WRONG, { filter = ReviewFilter.WRONG }, {
                        Text(stringResource(R.string.filter_wrong_count, all.count { it.response.isCorrect != true }))
                    })
                    FilterChip(filter == ReviewFilter.CORRECT, { filter = ReviewFilter.CORRECT }, {
                        Text(stringResource(R.string.filter_correct_count, all.count { it.response.isCorrect == true }))
                    })
                    FilterChip(filter == ReviewFilter.UNANSWERED, { filter = ReviewFilter.UNANSWERED }, {
                        Text(stringResource(R.string.filter_unanswered_count, all.count { it.response.selectedDisplayIndex == null }))
                    })
                }
            }
            if (shown.isEmpty()) item { Text(stringResource(R.string.review_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(shown, key = { it.response.id }) { item ->
                ReviewCard(item) {
                    nav.navigate(Routes.reader(item.response.topicKey, item.response.relatedBlockPosition ?: -1))
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(item: ReviewItem, onTheory: () -> Unit) {
    val r = item.response
    val fb = LocalFeedbackColors.current
    Card(
        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (icon, color, label) = when (r.isCorrect) {
                    true -> Triple(Icons.Filled.CheckCircle, fb.correct, stringResource(R.string.correct_one))
                    false -> Triple(Icons.Filled.Cancel, fb.wrong, stringResource(R.string.incorrect_one))
                    null -> Triple(Icons.Outlined.RemoveCircleOutline, MaterialTheme.colorScheme.onSurfaceVariant, stringResource(R.string.unanswered_one))
                }
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                Text("${r.position + 1}. $label · ${r.topicTitle}", style = MaterialTheme.typography.labelLarge, color = color, modifier = Modifier.padding(start = 6.dp))
            }
            Text(r.questionText, style = MaterialTheme.typography.titleMedium)
            item.options.forEachIndexed { i, o ->
                val state = when {
                    i == r.correctDisplayIndex && i == r.selectedDisplayIndex -> OptionState.CORRECT
                    i == r.correctDisplayIndex -> OptionState.CORRECT_NOT_SELECTED
                    i == r.selectedDisplayIndex -> OptionState.WRONG_SELECTED
                    else -> OptionState.DISABLED
                }
                OptionCard(optionLetter(i), o, state, onClick = {}, enabled = false)
            }
            ExplanationCard(r.explanation)
            RelatedTheoryCard(item.theory, onOpenTheory = if (item.topicAvailable) onTheory else null)
        }
    }
}
