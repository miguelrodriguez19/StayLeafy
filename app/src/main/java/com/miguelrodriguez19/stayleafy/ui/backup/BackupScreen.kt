package com.miguelrodriguez19.stayleafy.ui.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miguelrodriguez19.stayleafy.R
import com.miguelrodriguez19.stayleafy.data.backup.BackupCounts
import com.miguelrodriguez19.stayleafy.data.backup.PreparedRestore
import com.miguelrodriguez19.stayleafy.ui.components.LeafyPrimaryButton
import com.miguelrodriguez19.stayleafy.ui.components.leafyTopAppBarColors
import com.miguelrodriguez19.stayleafy.ui.theme.leafyLayoutTokens
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun BackupRoute(
    onNavigateBack: () -> Unit,
    onRestoreCompleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri: Uri? -> uri?.let(viewModel::exportBackup) }
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> uri?.let(viewModel::prepareRestore) }

    LaunchedEffect(state.restoreCompleted) {
        if (state.restoreCompleted) {
            viewModel.acknowledgeRestoreCompleted()
            onRestoreCompleted()
        }
    }

    BackupScreen(
        state = state,
        onNavigateBack = onNavigateBack,
        onExportClick = {
            createDocumentLauncher.launch("StayLeafy-backup.zip")
        },
        onRestoreClick = {
            openDocumentLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
        },
        onConfirmRestore = viewModel::confirmRestore,
        onDiscardRestore = viewModel::discardPreparedRestore,
        onCancelOperation = viewModel::cancelOperation,
        onDismissError = viewModel::clearError,
        onDismissExportMessage = viewModel::acknowledgeExportCompleted,
        modifier = modifier,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun BackupScreen(
    state: BackupUiState,
    onNavigateBack: () -> Unit,
    onExportClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onConfirmRestore: () -> Unit,
    onDiscardRestore: () -> Unit,
    onDismissError: () -> Unit,
    onDismissExportMessage: () -> Unit,
    modifier: Modifier = Modifier,
    onCancelOperation: () -> Unit = {},
) {
    var showRestoreConfirmation by remember { mutableStateOf(false) }
    val backDescription = stringResource(R.string.backup_back)
    val resourceLocale = LocalConfiguration.current.locales[0]

    if (showRestoreConfirmation && state.preparedRestore != null) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirmation = false },
            title = { Text(stringResource(R.string.backup_confirm_title)) },
            text = { Text(stringResource(R.string.backup_confirm_message)) },
            dismissButton = {
                TextButton(
                    onClick = { showRestoreConfirmation = false },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.backup_cancel))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreConfirmation = false
                        onConfirmRestore()
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.backup_confirm_restore))
                }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                colors = leafyTopAppBarColors(),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        enabled = state.operation != BackupOperation.RESTORE,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = backDescription,
                        )
                    }
                },
                title = { Text(stringResource(R.string.backup_title)) },
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = leafyLayoutTokens.spacingLg,
                vertical = leafyLayoutTokens.spacingMd,
            ),
            verticalArrangement = Arrangement.spacedBy(leafyLayoutTokens.spacingMd),
        ) {
            item {
                Text(
                    text = stringResource(R.string.backup_privacy_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                BackupActionCard(
                    title = stringResource(R.string.backup_export_title),
                    message = stringResource(R.string.backup_export_message),
                    button = stringResource(R.string.backup_export_action),
                    enabled = !state.isBusy,
                    onClick = onExportClick,
                )
            }
            item {
                BackupActionCard(
                    title = stringResource(R.string.backup_restore_title),
                    message = stringResource(R.string.backup_restore_message),
                    button = stringResource(R.string.backup_restore_action),
                    enabled = !state.isBusy,
                    onClick = onRestoreClick,
                )
            }
            state.error?.let { error ->
                item {
                    BackupMessage(
                        message = stringResource(
                            when (error) {
                                BackupError.EXPORT -> R.string.backup_export_error
                                BackupError.INVALID_BACKUP -> R.string.backup_invalid_error
                                BackupError.RESTORE -> R.string.backup_restore_error
                            },
                        ),
                        action = stringResource(R.string.backup_dismiss),
                        onAction = onDismissError,
                        isError = true,
                    )
                }
            }
            if (state.exportCompleted) {
                item {
                    BackupMessage(
                        message = stringResource(R.string.backup_export_complete),
                        action = stringResource(R.string.backup_dismiss),
                        onAction = onDismissExportMessage,
                    )
                }
            }
            state.preparedRestore?.let { prepared ->
                item {
                    RestorePreview(
                        prepared = prepared,
                        resourceLocale = resourceLocale,
                        enabled = !state.isBusy,
                        onConfirm = { showRestoreConfirmation = true },
                        onCancel = onDiscardRestore,
                    )
                }
            }
            if (state.isBusy) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(
                                when (state.operation) {
                                    BackupOperation.EXPORT -> R.string.backup_exporting
                                    BackupOperation.PREPARE_RESTORE -> R.string.backup_preparing
                                    BackupOperation.RESTORE -> R.string.backup_restoring
                                    null -> R.string.backup_working
                                },
                            ),
                            modifier = Modifier.padding(start = leafyLayoutTokens.spacingSm),
                        )
                        if (state.operation == BackupOperation.EXPORT ||
                            state.operation == BackupOperation.PREPARE_RESTORE
                        ) {
                            TextButton(
                                onClick = onCancelOperation,
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                Text(stringResource(R.string.backup_cancel))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupActionCard(
    title: String,
    message: String,
    button: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(leafyLayoutTokens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(leafyLayoutTokens.spacingSm),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LeafyPrimaryButton(
                text = button,
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RestorePreview(
    prepared: PreparedRestore,
    resourceLocale: java.util.Locale,
    enabled: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(leafyLayoutTokens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(leafyLayoutTokens.spacingSm),
        ) {
            Text(
                text = stringResource(R.string.backup_preview_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(
                    R.string.backup_preview_source,
                    prepared.sourceVersionName,
                    formatBackupDate(prepared.exportedAtEpochMillis, resourceLocale),
                ),
            )
            BackupCountsSummary(prepared.counts)
            Text(
                text = stringResource(R.string.backup_replace_warning),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(leafyLayoutTokens.spacingSm),
            ) {
                TextButton(
                    onClick = onCancel,
                    enabled = enabled,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.backup_cancel))
                }
                LeafyPrimaryButton(
                    text = stringResource(R.string.backup_review_restore),
                    onClick = onConfirm,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BackupCountsSummary(counts: BackupCounts) {
    val values = listOf(
        stringResource(R.string.backup_count_plants, counts.plants),
        stringResource(R.string.backup_count_notes, counts.notes),
        stringResource(R.string.backup_count_care_events, counts.careEvents),
        stringResource(R.string.backup_count_rules, counts.reminderRules),
        stringResource(R.string.backup_count_notices, counts.notices),
        stringResource(R.string.backup_count_photos, counts.photos),
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        values.forEach { value ->
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun BackupMessage(
    message: String,
    action: String,
    onAction: () -> Unit,
    isError: Boolean = false,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer,
            )
            TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(action)
            }
        }
    }
}

private fun formatBackupDate(epochMillis: Long, resourceLocale: java.util.Locale): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withLocale(resourceLocale)
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMillis))
