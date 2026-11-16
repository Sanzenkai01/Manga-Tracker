package com.sanzenkai.mangatracker.ui.discovery

import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sanzenkai.mangatracker.R
import com.sanzenkai.mangatracker.core.parser.api.ParsedSitePage
import com.sanzenkai.mangatracker.core.parser.api.SiteParseIssue
import com.sanzenkai.mangatracker.core.parser.api.SiteParseResult
import com.sanzenkai.mangatracker.core.parser.engine.ExtractionRule
import com.sanzenkai.mangatracker.core.parser.engine.ManualSiteConfiguration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteDiscoveryScreen(
    state: SiteDiscoveryUiState,
    onUrlChanged: (String) -> Unit,
    onAnalyze: () -> Unit,
    onConfigurationChanged: (ManualSiteConfiguration) -> Unit,
    onManualVisibilityChanged: (Boolean) -> Unit,
    onTestConfiguration: () -> Unit,
    onSaveConfiguration: () -> Unit,
    onRegisterManga: () -> Unit,
    showNotificationPermissionRationale: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onDismissNotificationPermissionRationale: () -> Unit,
    onMangaRegistered: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(state.registeredMangaId) {
        state.registeredMangaId?.let(onMangaRegistered)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.site_discovery_title)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.site_discovery_prompt),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    OutlinedTextField(
                        value = state.url,
                        onValueChange = onUrlChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.site_url_label)) },
                        placeholder = { Text(stringResource(R.string.site_url_placeholder)) },
                        trailingIcon = {
                            TextButton(
                                onClick = {
                                    val clipboardText = context
                                        .getSystemService(ClipboardManager::class.java)
                                        ?.primaryClip
                                        ?.takeIf { it.itemCount > 0 }
                                        ?.getItemAt(0)
                                        ?.coerceToText(context)
                                        ?.toString()
                                        ?.trim()
                                    if (!clipboardText.isNullOrEmpty()) {
                                        onUrlChanged(clipboardText)
                                    }
                                },
                                enabled = !state.isLoading,
                            ) {
                                Text(stringResource(R.string.site_paste_button))
                            }
                        },
                        minLines = 2,
                        maxLines = 3,
                        enabled = !state.isLoading,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    Button(
                        onClick = onAnalyze,
                        enabled = !state.isLoading && state.url.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(stringResource(R.string.site_analyze_button))
                        }
                    }
                    if (showNotificationPermissionRationale) {
                        AlertDialog(
                            onDismissRequest = onDismissNotificationPermissionRationale,
                            title = {
                                Text(stringResource(R.string.notification_permission_rationale_title))
                            },
                            text = {
                                Text(stringResource(R.string.notification_permission_rationale_message))
                            },
                            confirmButton = {
                                TextButton(onClick = onRequestNotificationPermission) {
                                    Text(stringResource(R.string.notification_permission_rationale_continue))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = onDismissNotificationPermissionRationale) {
                                    Text(stringResource(R.string.notification_permission_rationale_not_now))
                                }
                            },
                        )
                    }
                }
            }

            state.error?.let {
                DiscoveryMessageCard(
                    message = it,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            state.message?.let {
                DiscoveryMessageCard(
                    message = it,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            state.result?.let { result ->
                val registrationCandidate = state.registrationCandidate
                state.previewPage?.let { page -> DiscoveryResultPreview(page) }
                    ?: DiscoveryMessageCard(
                        message = when (result) {
                            is SiteParseResult.Success,
                            is SiteParseResult.RequiresConfiguration -> stringResource(
                                R.string.site_result_not_identified,
                            )
                            is SiteParseResult.Failure -> result.reason
                        },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                val issue = (result as? SiteParseResult.RequiresConfiguration)?.issue
                when {
                    issue == SiteParseIssue.UNSUPPORTED_DYNAMIC_CONTENT &&
                        registrationCandidate == null -> {
                        DiscoveryMessageCard(
                            message = stringResource(R.string.site_dynamic_series_page_hint),
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                    issue == SiteParseIssue.UNSUPPORTED_DYNAMIC_CONTENT -> {
                        DiscoveryMessageCard(
                            message = stringResource(R.string.site_dynamic_chapter_note),
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                    registrationCandidate?.isLatestChapterKnown == true -> {
                        DiscoveryMessageCard(
                            message = stringResource(R.string.site_latest_only_registration_note),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    registrationCandidate != null && !registrationCandidate.isChapterListComplete -> {
                        DiscoveryMessageCard(
                            message = stringResource(R.string.site_incomplete_list_registration_note),
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }

                if (registrationCandidate != null) {
                    Button(
                        onClick = onRegisterManga,
                        enabled = !state.isRegisteringManga,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        if (state.isRegisteringManga) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(stringResource(R.string.site_add_to_library))
                        }
                    }
                }
            }

            val configuration = state.manualConfiguration
            val isDynamicPage = (state.result as? SiteParseResult.RequiresConfiguration)
                ?.issue == SiteParseIssue.UNSUPPORTED_DYNAMIC_CONTENT
            if (state.result != null && configuration != null && !isDynamicPage &&
                !state.isManualConfigurationVisible
            ) {
                TextButton(onClick = { onManualVisibilityChanged(true) }) {
                    Text(stringResource(R.string.site_open_manual_configuration))
                }
            }
            if (state.result != null && configuration != null && !isDynamicPage &&
                state.isManualConfigurationVisible
            ) {
                Text(
                    text = stringResource(
                        R.string.site_manual_configuration_for,
                        configuration.normalizedHost,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.site_manual_configuration_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                RuleEditor(
                    title = stringResource(R.string.site_title_field),
                    rule = configuration.titleRule,
                    onChange = { onConfigurationChanged(configuration.copy(titleRule = it)) },
                )
                RuleEditor(
                    title = stringResource(R.string.site_cover_field),
                    rule = configuration.coverRule,
                    onChange = { onConfigurationChanged(configuration.copy(coverRule = it)) },
                )
                RuleEditor(
                    title = stringResource(R.string.site_series_field),
                    rule = configuration.seriesUrlRule,
                    onChange = { onConfigurationChanged(configuration.copy(seriesUrlRule = it)) },
                )
                RuleEditor(
                    title = stringResource(R.string.site_chapter_number_field),
                    rule = configuration.chapterNumberRule,
                    onChange = { onConfigurationChanged(configuration.copy(chapterNumberRule = it)) },
                )
                RuleEditor(
                    title = stringResource(R.string.site_publication_date_field),
                    rule = configuration.publishedAtRule,
                    onChange = { onConfigurationChanged(configuration.copy(publishedAtRule = it)) },
                )
                RuleEditor(
                    title = stringResource(R.string.site_chapter_link_field),
                    rule = configuration.chapterLinkRule,
                    onChange = { onConfigurationChanged(configuration.copy(chapterLinkRule = it)) },
                )
                OutlinedTextField(
                    value = configuration.chapterListItemSelector,
                    onValueChange = {
                        onConfigurationChanged(configuration.copy(chapterListItemSelector = it))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.site_chapter_list_selector)) },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onTestConfiguration,
                        enabled = !state.isTestingConfiguration && !state.isLoading,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.isTestingConfiguration) {
                            CircularProgressIndicator()
                        } else {
                            Text(stringResource(R.string.site_test_configuration))
                        }
                    }
                    Button(
                        onClick = onSaveConfiguration,
                        enabled = state.manualTestPassed && !state.isSavingConfiguration,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            if (state.isSavingConfiguration) {
                                stringResource(R.string.site_saving_configuration)
                            } else {
                                stringResource(R.string.site_save_configuration)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleEditor(
    title: String,
    rule: ExtractionRule,
    onChange: (ExtractionRule) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = rule.selector,
            onValueChange = { onChange(rule.copy(selector = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.site_css_selector)) },
            singleLine = true,
        )
        OutlinedTextField(
            value = rule.attribute,
            onValueChange = { onChange(rule.copy(attribute = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.site_attribute_name)) },
            singleLine = true,
        )
        OutlinedTextField(
            value = rule.regex,
            onValueChange = { onChange(rule.copy(regex = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.site_regex_optional)) },
            singleLine = true,
        )
    }
}

@Composable
private fun DiscoveryResultPreview(
    page: ParsedSitePage,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (page.originalCoverUrl != null) {
                AsyncImage(
                    model = page.originalCoverUrl,
                    contentDescription = page.title,
                    modifier = Modifier
                        .size(width = 76.dp, height = 104.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(width = 76.dp, height = 104.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.site_cover_unavailable),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = page.title?.takeIf(String::isNotBlank)
                        ?: stringResource(R.string.site_result_found),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 3,
                )
                page.currentChapter?.label?.let { label ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoveryMessageCard(
    message: String,
    containerColor: Color,
    contentColor: Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
        )
    }
}
