package com.sanzenkai.mangatracker.ui.components

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanzenkai.mangatracker.R
import com.sanzenkai.mangatracker.ui.ReadingStatus

@Composable
internal fun ReadingStatusBadge(
    status: ReadingStatus,
    modifier: Modifier = Modifier,
) {
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val (label, tint) = when (status) {
        ReadingStatus.READ -> R.string.chapter_status_read to
            if (isDarkTheme) Color(0xFF90CAF9) else Color(0xFF1565C0)
        ReadingStatus.NEW -> R.string.chapter_status_new to
            if (isDarkTheme) Color(0xFFA5D6A7) else Color(0xFF1B5E20)
        ReadingStatus.UNREAD -> R.string.chapter_status_unread to
            if (isDarkTheme) Color(0xFFFFB4AB) else Color(0xFFB3261E)
    }

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = tint.copy(alpha = 0.14f),
        contentColor = tint,
    ) {
        Text(
            text = stringResource(label),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}
