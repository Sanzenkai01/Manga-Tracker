package com.sanzenkai.mangatracker.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sanzenkai.mangatracker.R
import com.sanzenkai.mangatracker.core.domain.model.Manga
import com.sanzenkai.mangatracker.core.domain.model.MonitoringSettings
import com.sanzenkai.mangatracker.core.domain.model.TrackedChapter
import com.sanzenkai.mangatracker.monitoring.MANGA_CHECK_OUTCOME_INCOMPLETE_LIST
import com.sanzenkai.mangatracker.ui.components.ReadingStatusBadge
import com.sanzenkai.mangatracker.ui.readingStatusAt
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaDetailScreen(
    state: MangaDetailUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onMonitoringChanged: (Boolean) -> Unit,
    onChapterReadChanged: (String, Boolean) -> Unit,
    onOpenChapter: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.manga?.title ?: stringResource(R.string.manga_details_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_action),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        when {
            state.isLoading -> CenteredDetailContent(contentPadding) {
                CircularProgressIndicator()
            }
            state.manga == null -> CenteredDetailContent(contentPadding) {
                Text(
                    text = state.error ?: stringResource(R.string.manga_details_unavailable),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            else -> MangaDetailsContent(
                manga = state.manga,
                chapters = state.chapters,
                monitoringSettings = state.monitoringSettings,
                state = state,
                contentPadding = contentPadding,
                onRefresh = onRefresh,
                onMonitoringChanged = onMonitoringChanged,
                onChapterReadChanged = onChapterReadChanged,
                onOpenChapter = onOpenChapter,
            )
        }
    }
}

@Composable
private fun MangaDetailsContent(
    manga: Manga,
    chapters: List<TrackedChapter>,
    monitoringSettings: MonitoringSettings?,
    state: MangaDetailUiState,
    contentPadding: PaddingValues,
    onRefresh: () -> Unit,
    onMonitoringChanged: (Boolean) -> Unit,
    onChapterReadChanged: (String, Boolean) -> Unit,
    onOpenChapter: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MangaDetailsHeader(manga, chapters, monitoringSettings)
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onRefresh,
                    enabled = !state.isRefreshing,
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        Text(stringResource(R.string.refresh_now))
                    }
                }
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        stringResource(R.string.monitoring_switch),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Switch(
                        checked = monitoringSettings?.isEnabled == true,
                        onCheckedChange = onMonitoringChanged,
                    )
                }
            }
        }
        state.error?.let { error ->
            item {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        state.message?.let { message ->
            item {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item {
            Text(
                text = stringResource(R.string.chapter_list_title),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        if (chapters.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.chapter_list_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            items(chapters, key = TrackedChapter::id) { chapter ->
                ChapterRow(
                    chapter = chapter,
                    lastSuccessfulCheckAtEpochMillis = monitoringSettings?.lastSuccessAtEpochMillis,
                    onOpen = { onOpenChapter(chapter.url) },
                    onReadChanged = { onChapterReadChanged(chapter.id, it) },
                )
            }
        }
    }
}

@Composable
private fun MangaDetailsHeader(
    manga: Manga,
    chapters: List<TrackedChapter>,
    monitoringSettings: MonitoringSettings?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 112.dp, height = 152.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            manga.originalCoverUrl?.let { coverUrl ->
                AsyncImage(
                    model = coverUrl,
                    contentDescription = manga.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(R.string.unread_chapters, chapters.count { !it.isRead }),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(
                    if (monitoringSettings?.isEnabled == true) {
                        R.string.monitoring_enabled
                    } else {
                        R.string.monitoring_paused
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            monitoringSettings?.lastAttemptAtEpochMillis?.let { timestamp ->
                Text(
                    stringResource(R.string.last_checked, formatEpoch(timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            monitoringSettings?.lastSuccessAtEpochMillis?.let { timestamp ->
                Text(
                    stringResource(R.string.last_successful_check, formatEpoch(timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            monitoringSettings?.lastOutcome?.let { outcome ->
                val outcomeLabel = when (outcome) {
                    "SUCCESS" -> stringResource(R.string.last_check_succeeded)
                    "FAILURE" -> stringResource(R.string.last_check_failed)
                    MANGA_CHECK_OUTCOME_INCOMPLETE_LIST ->
                        stringResource(R.string.last_check_incomplete)
                    else -> stringResource(R.string.last_check_outcome, outcome)
                }
                Text(
                    text = outcomeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (outcome) {
                        "FAILURE" -> MaterialTheme.colorScheme.error
                        MANGA_CHECK_OUTCOME_INCOMPLETE_LIST -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.secondary
                    },
                )
            }
        }
    }
}

@Composable
private fun ChapterRow(
    chapter: TrackedChapter,
    lastSuccessfulCheckAtEpochMillis: Long?,
    onOpen: () -> Unit,
    onReadChanged: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val status = chapter.readingStatusAt(lastSuccessfulCheckAtEpochMillis)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = chapter.label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                )
                ReadingStatusBadge(status)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onOpen, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.open_chapter))
                }
                FilledTonalButton(
                    onClick = { onReadChanged(!chapter.isRead) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(
                        stringResource(
                            if (chapter.isRead) R.string.mark_unread else R.string.mark_read,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredDetailContent(
    contentPadding: PaddingValues,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private fun formatEpoch(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                .withLocale(Locale.getDefault()),
        )
