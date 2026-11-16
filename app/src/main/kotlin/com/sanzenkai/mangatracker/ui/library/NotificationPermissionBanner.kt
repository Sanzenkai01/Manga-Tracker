package com.sanzenkai.mangatracker.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sanzenkai.mangatracker.R

@Composable
internal fun NotificationPermissionBanner(
    hasMonitoredManga: Boolean,
    notificationsAvailable: Boolean,
    onOpenNotificationSettings: () -> Unit,
) {
    if (!hasMonitoredManga || notificationsAvailable) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.library_notification_warning),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(
                onClick = onOpenNotificationSettings,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.library_notification_settings_action))
            }
        }
    }
}
