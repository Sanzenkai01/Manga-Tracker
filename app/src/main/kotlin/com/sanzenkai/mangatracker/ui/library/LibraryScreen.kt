package com.sanzenkai.mangatracker.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sanzenkai.mangatracker.R
import com.sanzenkai.mangatracker.core.domain.model.LibraryLayout
import com.sanzenkai.mangatracker.core.domain.model.MangaSummary
import com.sanzenkai.mangatracker.ui.components.ReadingStatusBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onDiscoverSites: () -> Unit,
    onOpenSettings: () -> Unit,
    onLayoutChanged: (LibraryLayout) -> Unit,
    onReadFilterChanged: (LibraryReadFilter) -> Unit,
    onMangaClick: (String) -> Unit,
    onMangaLongPress: (String) -> Unit,
    onMangaSelectionToggle: (String) -> Unit,
    onClearSelection: () -> Unit,
    onMarkSelectedAsRead: () -> Unit,
    onMarkSelectedAsUnread: () -> Unit,
    onRemoveSelected: () -> Unit,
) {
    var showRemoveConfirmation by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (state.isSelectionMode) {
                        Text(
                            pluralStringResource(
                                R.plurals.library_selection_count,
                                state.selectedMangaIds.size,
                                state.selectedMangaIds.size,
                            ),
                        )
                    } else {
                        Text(stringResource(R.string.library_title))
                    }
                },
                actions = {
                    if (state.isSelectionMode) {
                        TextButton(
                            onClick = onClearSelection,
                            enabled = !state.isActionInProgress,
                        ) {
                            Text(stringResource(R.string.library_cancel_selection))
                        }
                    } else {
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.settings_action),
                            )
                        }
                        IconButton(onClick = onDiscoverSites) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = stringResource(R.string.site_discovery_action),
                            )
                        }
                    }
                },
            )
        },
    ) { contentPadding ->
        when {
            state.isLoading -> CenteredContent(contentPadding) {
                CircularProgressIndicator()
            }

            state.error != null -> CenteredContent(contentPadding) {
                Text(
                    text = state.error,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            state.mangas.isEmpty() -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.library_empty_title),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.library_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onDiscoverSites) {
                    Text(stringResource(R.string.site_discovery_action))
                }
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            ) {
                val filters = LibraryReadFilter.values()
                val visibleMangas = state.visibleMangas
                if (state.isSelectionMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = onMarkSelectedAsRead,
                            enabled = !state.isActionInProgress,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.library_mark_selected_read))
                        }
                        TextButton(
                            onClick = onMarkSelectedAsUnread,
                            enabled = !state.isActionInProgress,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.library_mark_selected_unread))
                        }
                        TextButton(
                            onClick = { showRemoveConfirmation = true },
                            enabled = !state.isActionInProgress,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.library_remove_selected))
                        }
                        if (state.isActionInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                } else {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        filters.forEachIndexed { index, filter ->
                            SegmentedButton(
                                selected = state.readFilter == filter,
                                onClick = { onReadFilterChanged(filter) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = filters.size,
                                ),
                            ) {
                                Text(
                                    stringResource(
                                        when (filter) {
                                            LibraryReadFilter.ALL -> R.string.library_filter_all
                                            LibraryReadFilter.READ -> R.string.library_filter_read
                                            LibraryReadFilter.UNREAD -> R.string.library_filter_unread
                                        },
                                    ),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = { onLayoutChanged(LibraryLayout.LIST) },
                            enabled = state.layout != LibraryLayout.LIST,
                        ) {
                            Text(stringResource(R.string.layout_list))
                        }
                        TextButton(
                            onClick = { onLayoutChanged(LibraryLayout.GRID) },
                            enabled = state.layout != LibraryLayout.GRID,
                        ) {
                            Text(stringResource(R.string.layout_grid))
                        }
                    }
                }
                state.actionError?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (visibleMangas.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(
                                if (state.readFilter == LibraryReadFilter.READ) {
                                    R.string.library_filter_empty_read
                                } else {
                                    R.string.library_filter_empty_unread
                                },
                            ),
                            modifier = Modifier.padding(24.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                } else if (state.layout == LibraryLayout.LIST) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(visibleMangas, key = MangaSummary::id) { manga ->
                            MangaRow(
                                manga = manga,
                                selectionMode = state.isSelectionMode,
                                isSelected = manga.id in state.selectedMangaIds,
                                onClick = { onMangaClick(manga.id) },
                                onLongPress = { onMangaLongPress(manga.id) },
                                onSelectionToggle = { onMangaSelectionToggle(manga.id) },
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 144.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(visibleMangas, key = MangaSummary::id) { manga ->
                            MangaTile(
                                manga = manga,
                                selectionMode = state.isSelectionMode,
                                isSelected = manga.id in state.selectedMangaIds,
                                onClick = { onMangaClick(manga.id) },
                                onLongPress = { onMangaLongPress(manga.id) },
                                onSelectionToggle = { onMangaSelectionToggle(manga.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRemoveConfirmation) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirmation = false },
            title = { Text(stringResource(R.string.library_remove_confirm_title)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.library_remove_confirm_message,
                        state.selectedMangaIds.size,
                        state.selectedMangaIds.size,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemoveConfirmation = false
                        onRemoveSelected()
                    },
                    enabled = !state.isActionInProgress,
                ) {
                    Text(stringResource(R.string.library_remove_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirmation = false }) {
                    Text(stringResource(R.string.library_remove_cancel_action))
                }
            },
        )
    }
}

@Composable
private fun CenteredContent(
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

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun MangaRow(
    manga: MangaSummary,
    selectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onSelectionToggle: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (selectionMode) onSelectionToggle() else onClick() },
                onLongClick = onLongPress,
            ),
        colors = if (isSelected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        },
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverImage(
                manga = manga,
                modifier = Modifier
                    .size(width = 64.dp, height = 88.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            MangaSummaryContent(
                manga = manga,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            )
            if (selectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelectionToggle() },
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun MangaTile(
    manga: MangaSummary,
    selectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onSelectionToggle: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (selectionMode) onSelectionToggle() else onClick() },
                onLongClick = onLongPress,
            ),
        colors = if (isSelected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        },
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        Column {
            CoverImage(
                manga = manga,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(196.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
            )
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MangaSummaryContent(
                    manga = manga,
                    modifier = Modifier.weight(1f),
                )
                if (selectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onSelectionToggle() },
                    )
                }
            }
        }
    }
}

@Composable
private fun CoverImage(
    manga: MangaSummary,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
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
}

@Composable
private fun MangaSummaryContent(
    manga: MangaSummary,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = manga.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        manga.readingStatus()?.let { status ->
            ReadingStatusBadge(status)
        }
        if (manga.latestChapterLabel != null || manga.unreadChapterCount > 0) {
            val unreadCount = pluralStringResource(
                R.plurals.library_card_unread_count,
                manga.unreadChapterCount,
                manga.unreadChapterCount,
            )
            Text(
                text = manga.latestChapterLabel?.let { chapter ->
                    stringResource(R.string.library_card_summary, chapter, unreadCount)
                } ?: unreadCount,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
