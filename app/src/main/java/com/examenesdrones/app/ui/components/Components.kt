package com.examenesdrones.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.examenesdrones.app.R
import com.examenesdrones.app.parser.StyledText
import com.examenesdrones.app.ui.theme.LocalFeedbackColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

// ------------------------------------------------------------------------------------------
// Formatting
// ------------------------------------------------------------------------------------------

private val dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

fun formatDateTime(millis: Long): String =
    dateTimeFormatter.withLocale(Locale.getDefault()).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

fun formatDate(millis: Long): String =
    dateFormatter.withLocale(Locale.getDefault()).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

fun formatDuration(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

fun formatPercent(p: Double): String = if (p % 1.0 == 0.0) "${p.toInt()} %" else "%.1f %%".format(p)

fun formatScore(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else "%.2f".format(v)

// ------------------------------------------------------------------------------------------
// Text
// ------------------------------------------------------------------------------------------

/** Renders source text keeping the bold key concepts from the PDF. */
fun styledText(text: String, bold: String?, highlight: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    StyledText.decodeBold(text, bold).forEach { r ->
        addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = highlight), r.first, r.last + 1)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

// ------------------------------------------------------------------------------------------
// States
// ------------------------------------------------------------------------------------------

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(72.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(36.dp))
            }
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = 8.dp)) { Text(actionLabel) }
        }
    }
}

@Composable
fun ErrorCard(message: String, modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
    ) { Text(message, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) }
}

/** Informative note, e.g. about where the questions come from. */
@Composable
fun NoticeCard(message: String, modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
    ) { Text(message, Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall) }
}

// ------------------------------------------------------------------------------------------
// Stats
// ------------------------------------------------------------------------------------------

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, supporting: String? = null) {
    Card(
        modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            Text(value, style = MaterialTheme.typography.headlineSmall)
            if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PercentBar(label: String, percent: Double, detail: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2)
            Text(detail, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (percent / 100.0).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            color = color, trackColor = MaterialTheme.colorScheme.surfaceVariant, drawStopIndicator = {},
        )
    }
}

@Composable
fun ScoreRing(percent: Double, center: String, caption: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.size(150.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val stroke = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
            drawArc(track, -90f, 360f, false, style = stroke)
            drawArc(color, -90f, (360f * (percent / 100.0).toFloat()).coerceIn(0f, 360f), false, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(center, style = MaterialTheme.typography.headlineMedium)
            Text(caption, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Simple line chart of percentages (0-100), oldest first. */
@Composable
fun TrendChart(values: List<Double>, modifier: Modifier = Modifier, threshold: Double? = null) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val passColor = MaterialTheme.colorScheme.secondary
    val description = stringResource(R.string.cd_trend_chart, values.joinToString(", ") { formatPercent(it) })
    Canvas(modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = description }) {
        val w = size.width
        val h = size.height
        listOf(0.0, 50.0, 100.0).forEach { g ->
            val y = h - (g / 100.0 * h).toFloat()
            drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }
        threshold?.let {
            val y = h - (it / 100.0 * h).toFloat()
            drawLine(passColor, Offset(0f, y), Offset(w, y), strokeWidth = 2f,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
        }
        if (values.isEmpty()) return@Canvas
        val step = if (values.size == 1) 0f else w / (values.size - 1)
        val points = values.mapIndexed { i, v -> Offset(if (values.size == 1) w / 2 else i * step, h - (v / 100.0 * h).toFloat()) }
        for (i in 1 until points.size) drawLine(line, points[i - 1], points[i], strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        points.forEach { drawCircle(line, radius = 4.dp.toPx(), center = it) }
    }
}

// ------------------------------------------------------------------------------------------
// Answer options
// ------------------------------------------------------------------------------------------

enum class OptionState { IDLE, SELECTED, CORRECT, WRONG_SELECTED, CORRECT_NOT_SELECTED, DISABLED }

@Composable
fun OptionCard(
    letter: String,
    text: String,
    state: OptionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val fb = LocalFeedbackColors.current
    val scheme = MaterialTheme.colorScheme
    val (container, content, border) = when (state) {
        OptionState.IDLE, OptionState.DISABLED -> Triple(scheme.surfaceContainerLow, scheme.onSurface, scheme.outlineVariant)
        OptionState.SELECTED -> Triple(scheme.tertiaryContainer, scheme.onTertiaryContainer, scheme.tertiary)
        OptionState.CORRECT, OptionState.CORRECT_NOT_SELECTED -> Triple(fb.correctContainer, fb.onCorrectContainer, fb.correct)
        OptionState.WRONG_SELECTED -> Triple(fb.wrongContainer, fb.onWrongContainer, fb.wrong)
    }
    val stateLabel = when (state) {
        OptionState.SELECTED -> stringResource(R.string.option_selected)
        OptionState.CORRECT -> stringResource(R.string.option_your_correct)
        OptionState.CORRECT_NOT_SELECTED -> stringResource(R.string.option_correct)
        OptionState.WRONG_SELECTED -> stringResource(R.string.option_your_wrong)
        else -> null
    }
    OutlinedCard(
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp)
            .selectable(selected = state == OptionState.SELECTED, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        colors = CardDefaults.outlinedCardColors(containerColor = container, contentColor = content),
        border = BorderStroke(if (state == OptionState.IDLE || state == OptionState.DISABLED) 1.dp else 2.dp, border),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = if (state == OptionState.IDLE || state == OptionState.DISABLED) scheme.surfaceVariant else border,
                contentColor = if (state == OptionState.IDLE || state == OptionState.DISABLED) scheme.onSurfaceVariant else scheme.surface,
                modifier = Modifier.size(32.dp),
            ) {
                Box(contentAlignment = Alignment.Center) { Text(letter, style = MaterialTheme.typography.titleSmall) }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(text, style = MaterialTheme.typography.bodyLarge)
                if (stateLabel != null) Text(stateLabel, style = MaterialTheme.typography.labelMedium, color = border)
            }
            when (state) {
                OptionState.CORRECT, OptionState.CORRECT_NOT_SELECTED ->
                    Icon(Icons.Filled.CheckCircle, null, tint = fb.correct, modifier = Modifier.padding(start = 8.dp))
                OptionState.WRONG_SELECTED -> Icon(Icons.Filled.Cancel, null, tint = fb.wrong, modifier = Modifier.padding(start = 8.dp))
                else -> {}
            }
        }
    }
}

fun optionLetter(i: Int): String = ('A' + i).toString()

// ------------------------------------------------------------------------------------------
// Theory
// ------------------------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TheoryBlockView(
    type: String,
    text: String,
    bold: String?,
    level: Int,
    imageFile: File?,
    imageAspect: Float,
    style: TextStyle,
    modifier: Modifier = Modifier,
    bookmarked: Boolean = false,
    highlighted: Boolean = false,
    onLongPress: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val bg = if (highlighted) scheme.secondaryContainer.copy(alpha = 0.6f) else Color.Transparent
    val m = modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(bg)
        .let { if (onLongPress != null) it.combinedClickable(onClick = {}, onLongClick = onLongPress) else it }
        .padding(horizontal = 4.dp, vertical = 2.dp)
    Row(m) {
        Box(Modifier.weight(1f)) {
            when (type) {
                "HEADING" -> Text(
                    text.trimEnd(':'),
                    style = when (level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                    color = if (level == 1) scheme.primary else scheme.onSurface,
                    modifier = Modifier.padding(top = if (level == 1) 20.dp else 12.dp, bottom = 4.dp).semantics { },
                )
                "BULLET" -> Row(Modifier.padding(vertical = 3.dp)) {
                    if (level == 0) Text("•", style = style, color = scheme.primary, modifier = Modifier.width(20.dp))
                    else Spacer(Modifier.width(8.dp))
                    Text(styledText(text, bold, scheme.primary), style = style)
                }
                "IMAGE" -> if (imageFile != null) LocalImage(imageFile, imageAspect, Modifier.padding(vertical = 8.dp))
                else -> Text(styledText(text, bold, scheme.primary), style = style, modifier = Modifier.padding(vertical = 5.dp))
            }
        }
        if (bookmarked) {
            Icon(
                Icons.Filled.Bookmark, stringResource(R.string.cd_bookmarked),
                tint = scheme.secondary, modifier = Modifier.padding(start = 4.dp, top = 6.dp).size(18.dp),
            )
        }
    }
}

@Composable
fun LocalImage(file: File, aspect: Float, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, opts)
                var sample = 1
                while (opts.outWidth / sample > 1400) sample *= 2
                BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }.getOrNull()
        }
    }
    val ratio = if (aspect > 0.1f) aspect else 1.5f
    Box(
        modifier.fillMaxWidth().widthIn(max = 600.dp).aspectRatio(ratio).clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) Image(b, stringResource(R.string.cd_pdf_image), Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
}

/** Card showing excerpts of the syllabus related to a question. */
@Composable
fun RelatedTheoryCard(excerpts: List<String>, onOpenTheory: (() -> Unit)?, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.related_theory), style = MaterialTheme.typography.titleSmall)
            if (excerpts.isEmpty()) {
                Text(stringResource(R.string.related_theory_none), style = MaterialTheme.typography.bodyMedium)
            } else {
                excerpts.forEach { Text("“$it”", style = MaterialTheme.typography.bodyMedium) }
                Text(stringResource(R.string.related_theory_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onOpenTheory != null) {
                androidx.compose.material3.FilledTonalButton(onClick = onOpenTheory) { Text(stringResource(R.string.read_full_theory)) }
            }
        }
    }
}

/** Shows the literal sentence of the PDF that supports the correct answer; nothing if there is none. */
@Composable
fun ExplanationCard(explanation: String?, modifier: Modifier = Modifier) {
    if (explanation.isNullOrBlank()) return
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.explanation), style = MaterialTheme.typography.titleSmall)
            Text(explanation, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Large result banner after answering a question. */
@Composable
fun FeedbackBanner(correct: Boolean, correctAnswer: String, modifier: Modifier = Modifier) {
    val fb = LocalFeedbackColors.current
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (correct) fb.correctContainer else fb.wrongContainer,
            contentColor = if (correct) fb.onCorrectContainer else fb.onWrongContainer,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (correct) Icons.Filled.CheckCircle else Icons.Filled.Cancel, null, tint = if (correct) fb.correct else fb.wrong, modifier = Modifier.size(32.dp))
            Column(Modifier.padding(start = 12.dp)) {
                Text(
                    stringResource(if (correct) R.string.feedback_correct else R.string.feedback_wrong),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(stringResource(R.string.feedback_correct_answer, correctAnswer), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun rememberNow(): Long = remember { System.currentTimeMillis() }
