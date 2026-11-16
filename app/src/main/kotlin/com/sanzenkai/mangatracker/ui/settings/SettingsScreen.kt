package com.sanzenkai.mangatracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.sanzenkai.mangatracker.R
import com.sanzenkai.mangatracker.core.domain.model.AppThemeMode
import com.sanzenkai.mangatracker.core.domain.model.LibraryLayout
import com.sanzenkai.mangatracker.core.domain.model.MonitoringInterval

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    notificationsEnabled: Boolean,
    onBack: () -> Unit,
    onLayoutChanged: (LibraryLayout) -> Unit,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    onMonitoringIntervalChanged: (MonitoringInterval) -> Unit,
    onEnableNotifications: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onConfigureSite: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
            state.isLoading -> SettingsCenteredContent(contentPadding) {
                CircularProgressIndicator()
            }
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                state.error?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
                SettingsPreferenceCard(
                    title = stringResource(R.string.settings_layout_title),
                    description = stringResource(R.string.settings_layout_description),
                ) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = state.libraryLayout == LibraryLayout.LIST,
                            onClick = { onLayoutChanged(LibraryLayout.LIST) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        ) {
                            Text(stringResource(R.string.layout_list))
                        }
                        SegmentedButton(
                            selected = state.libraryLayout == LibraryLayout.GRID,
                            onClick = { onLayoutChanged(LibraryLayout.GRID) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        ) {
                            Text(stringResource(R.string.layout_grid))
                        }
                    }
                }
                SettingsPreferenceCard(
                    title = stringResource(R.string.settings_theme_title),
                    description = stringResource(R.string.settings_theme_description),
                ) {
                    Column(Modifier.selectableGroup()) {
                        SettingsRadioOption(
                            title = stringResource(R.string.settings_theme_system),
                            description = stringResource(R.string.settings_theme_system_description),
                            selected = state.themeMode == AppThemeMode.SYSTEM,
                            onClick = { onThemeModeChanged(AppThemeMode.SYSTEM) },
                        )
                        HorizontalDivider()
                        SettingsRadioOption(
                            title = stringResource(R.string.settings_theme_light),
                            selected = state.themeMode == AppThemeMode.LIGHT,
                            onClick = { onThemeModeChanged(AppThemeMode.LIGHT) },
                        )
                        HorizontalDivider()
                        SettingsRadioOption(
                            title = stringResource(R.string.settings_theme_dark),
                            selected = state.themeMode == AppThemeMode.DARK,
                            onClick = { onThemeModeChanged(AppThemeMode.DARK) },
                        )
                    }
                }
                SettingsPreferenceCard(
                    title = stringResource(R.string.settings_monitoring_interval_title),
                    description = stringResource(R.string.settings_monitoring_limitation),
                ) {
                    Column(Modifier.selectableGroup()) {
                        MonitoringInterval.values().forEachIndexed { index, interval ->
                            if (index > 0) HorizontalDivider()
                            SettingsRadioOption(
                                title = stringResource(interval.labelResource()),
                                selected = state.monitoringInterval == interval,
                                onClick = { onMonitoringIntervalChanged(interval) },
                            )
                        }
                    }
                }
                SettingsPreferenceCard(
                    title = stringResource(R.string.settings_backup_title),
                    description = stringResource(R.string.settings_backup_description),
                ) {
                    Button(
                        onClick = onExportBackup,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isBackupInProgress,
                    ) {
                        if (state.isBackupInProgress && !state.isBackupImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            stringResource(
                                if (state.isBackupInProgress && !state.isBackupImporting) {
                                    R.string.settings_backup_in_progress
                                } else {
                                    R.string.settings_backup_action
                                },
                            ),
                        )
                    }
                    OutlinedButton(
                        onClick = onImportBackup,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isBackupInProgress,
                    ) {
                        if (state.isBackupImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            stringResource(
                                if (state.isBackupImporting) {
                                    R.string.settings_backup_import_in_progress
                                } else {
                                    R.string.settings_backup_import_action
                                },
                            ),
                        )
                    }
                    state.backupMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.isBackupError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = stringResource(R.string.settings_language_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.settings_language_follow_system),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.settings_local_storage),
                    style = MaterialTheme.typography.bodyMedium,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = stringResource(R.string.settings_notifications_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(
                        if (notificationsEnabled) R.string.settings_notifications_enabled
                        else R.string.settings_notifications_disabled,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.settings_notification_permission_help),
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = onEnableNotifications,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !notificationsEnabled,
                ) {
                    Text(stringResource(R.string.settings_enable_notifications))
                }
                TextButton(
                    onClick = onOpenNotificationSettings,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_open_notification_settings))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Button(
                    onClick = onConfigureSite,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_configure_site))
                }
            }
        }
    }
}

private fun MonitoringInterval.labelResource(): Int =
    when (this) {
        MonitoringInterval.ONE_HOUR -> R.string.settings_monitoring_interval_1h
        MonitoringInterval.THREE_HOURS -> R.string.settings_monitoring_interval_3h
        MonitoringInterval.SIX_HOURS -> R.string.settings_monitoring_interval_6h
        MonitoringInterval.TWELVE_HOURS -> R.string.settings_monitoring_interval_12h
        MonitoringInterval.TWENTY_FOUR_HOURS -> R.string.settings_monitoring_interval_24h
    }

@Composable
private fun SettingsPreferenceCard(
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                content()
            },
        )
    }
}

@Composable
private fun SettingsRadioOption(
    title: String,
    description: String? = null,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        RadioButton(selected = selected, onClick = null)
    }
}

@Composable
private fun SettingsCenteredContent(
    contentPadding: PaddingValues,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
