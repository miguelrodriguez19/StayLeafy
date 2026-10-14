package com.miguelrodriguez19.stayleafy.ui.reminder

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.miguelrodriguez19.stayleafy.R
import com.miguelrodriguez19.stayleafy.model.AppNotice
import com.miguelrodriguez19.stayleafy.model.CareType
import com.miguelrodriguez19.stayleafy.model.NoticeDeliveryState
import com.miguelrodriguez19.stayleafy.model.ReminderScheduleType
import com.miguelrodriguez19.stayleafy.ui.plant.formatEpochDay
import com.miguelrodriguez19.stayleafy.ui.plant.formatTimestamp
import com.miguelrodriguez19.stayleafy.ui.plant.PlantPhotoBox
import com.miguelrodriguez19.stayleafy.ui.components.LeafyCareIcon
import com.miguelrodriguez19.stayleafy.ui.components.LeafyEmptyState
import com.miguelrodriguez19.stayleafy.ui.components.LeafyPrimaryButton
import com.miguelrodriguez19.stayleafy.ui.components.LeafyStateContent
import com.miguelrodriguez19.stayleafy.ui.theme.StayLeafyTheme
import com.miguelrodriguez19.stayleafy.ui.theme.leafyColors

@Composable
fun NoticeListRoute(
    onNoticeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NoticeListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val warning by viewModel.notificationWarning.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshNotificationWarning()
        onPauseOrDispose {}
    }
    NoticeListScreen(
        state = state,
        notificationWarning = warning,
        onNoticeClick = onNoticeClick,
        onRetry = viewModel::retry,
        onMarkAllRead = viewModel::markAllRead,
        onOpenNotificationSettings = {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            } else {
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                )
            }
            context.startActivity(intent)
        },
        modifier = modifier,
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NoticeListScreen(
    state: NoticeListUiState,
    notificationWarning: Boolean,
    onNoticeClick: (String) -> Unit,
    onRetry: () -> Unit,
    onMarkAllRead: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unread = (state as? NoticeListUiState.Content)?.notices?.count { !it.isRead } ?: 0
    var showOptions by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.notices_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                },
                actions = {
                    IconButton(onClick = { showOptions = true }) {
                        Icon(
                            painterResource(R.drawable.ic_more),
                            contentDescription = stringResource(R.string.more_options),
                        )
                    }
                    DropdownMenu(
                        expanded = showOptions,
                        onDismissRequest = { showOptions = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.mark_all_read)) },
                            enabled = unread > 0,
                            onClick = {
                                showOptions = false
                                onMarkAllRead()
                            },
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (notificationWarning) {
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.notifications_blocked_message),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onOpenNotificationSettings) {
                            Text(stringResource(R.string.open_settings))
                        }
                    }
                }
            }
            when (state) {
                NoticeListUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                NoticeListUiState.Error -> NoticeMessage(
                    message = stringResource(R.string.load_notices_error),
                    action = stringResource(R.string.retry),
                    onAction = onRetry,
                )
                NoticeListUiState.Empty -> LeafyEmptyState(
                    title = stringResource(R.string.no_notices),
                    supporting = stringResource(R.string.no_notices_hint),
                    modifier = Modifier.fillMaxSize(),
                )
                is NoticeListUiState.Content -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.notices, key = AppNotice::id) { notice ->
                        NoticeRow(notice = notice, onClick = { onNoticeClick(notice.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeRow(notice: AppNotice, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (notice.isRead) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            PlantPhotoBox(
                relativePath = notice.plantPhotoPath,
                modifier = Modifier.size(54.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(
                            if (notice.scheduleType == ReminderScheduleType.SEASONAL) R.string.season_notice_row_title
                            else R.string.notice_row_title,
                            notice.careType.localizedName(),
                            notice.plantName,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (!notice.isRead) {
                        Box(
                            Modifier.size(9.dp).background(MaterialTheme.leafyColors.accent, CircleShape),
                        )
                    }
                }
                Text(
                    stringResource(
                        if (notice.scheduleType == ReminderScheduleType.SEASONAL) {
                            R.string.notice_season_start
                        } else {
                            R.string.notice_due_date
                        },
                        formatEpochDay(notice.dueEpochDay),
                    ),
                    color = MaterialTheme.leafyColors.accent,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    stringResource(R.string.notice_row_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun NoticeDetailRoute(
    noticeId: String,
    onBack: () -> Unit,
    onViewPlant: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: NoticeDetailViewModel = hiltViewModel(
        creationCallback = { factory: NoticeDetailViewModel.Factory -> factory.create(noticeId) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NoticeDetailScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onViewPlant = onViewPlant,
        modifier = modifier,
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NoticeDetailScreen(
    state: NoticeDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onViewPlant: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notice_details)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_back),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (state is NoticeDetailUiState.Content) {
                LeafyPrimaryButton(
                    text = stringResource(R.string.view_plant),
                    onClick = { onViewPlant(state.notice.plantId) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        },
    ) { innerPadding ->
        when (state) {
            NoticeDetailUiState.Loading -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            NoticeDetailUiState.Error -> NoticeMessage(
                message = stringResource(R.string.load_notice_error),
                action = stringResource(R.string.retry),
                onAction = onRetry,
                modifier = Modifier.padding(innerPadding),
            )
            NoticeDetailUiState.NotFound -> NoticeMessage(
                message = stringResource(R.string.notice_not_found),
                action = stringResource(R.string.back_to_notices),
                onAction = onBack,
                modifier = Modifier.padding(innerPadding),
            )
            is NoticeDetailUiState.Content -> Column(
                Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NoticeHero(state.notice)
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(Modifier.padding(horizontal = 14.dp)) {
                        NoticeDetailField(R.string.care_type, state.notice.careType.localizedName())
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        NoticeDetailField(
                            R.string.schedule_type,
                            stringResource(
                                if (state.notice.scheduleType == ReminderScheduleType.SEASONAL) {
                                    R.string.schedule_seasonal
                                } else {
                                    R.string.schedule_interval
                                },
                            ),
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        NoticeDetailField(R.string.scheduled_for, formatEpochDay(state.notice.dueEpochDay))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        NoticeDetailField(R.string.notice_created, formatTimestamp(state.notice.createdAt))
                    }
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NoticeHero(notice: AppNotice) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlantPhotoBox(
                relativePath = notice.plantPhotoPath,
                modifier = Modifier.size(56.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(notice.plantName, style = MaterialTheme.typography.titleMedium)
                Text(notice.careType.localizedName(), style = MaterialTheme.typography.bodySmall)
                Text(
                    stringResource(
                        if (notice.scheduleType == ReminderScheduleType.SEASONAL) {
                            R.string.notice_season_start
                        } else {
                            R.string.notice_due_date
                        },
                        formatEpochDay(notice.dueEpochDay),
                    ),
                    color = MaterialTheme.leafyColors.accent,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun NoticeDetailField(label: Int, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun NoticeMessage(
    message: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    action: String? = null,
    onAction: () -> Unit = {},
) = LeafyStateContent(message, modifier, supporting, action, onAction)

private val previewNotice = AppNotice(
    id = "notice",
    plantId = "plant",
    plantName = "Monstera",
    careType = CareType.WATERING,
    scheduleType = ReminderScheduleType.SEASONAL,
    dueEpochDay = 21_000,
    createdAt = 1_800_000_000_000,
    readAt = null,
    ruleId = "rule",
    deliveryState = NoticeDeliveryState.DELIVERED,
)

@Preview(name = "Notices - Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Notices - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NoticeListPreview() {
    StayLeafyTheme(dynamicColor = false) {
        NoticeListScreen(
            state = NoticeListUiState.Content(listOf(previewNotice)),
            notificationWarning = true,
            onNoticeClick = {}, onRetry = {}, onMarkAllRead = {}, onOpenNotificationSettings = {},
        )
    }
}

@Preview(name = "Notice empty - Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Notice empty - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NoticeEmptyPreview() {
    StayLeafyTheme(dynamicColor = false) {
        NoticeListScreen(
            state = NoticeListUiState.Empty,
            notificationWarning = false,
            onNoticeClick = {}, onRetry = {}, onMarkAllRead = {}, onOpenNotificationSettings = {},
        )
    }
}

@Preview(name = "Notices error - Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Notices error - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NoticeErrorPreview() {
    StayLeafyTheme(dynamicColor = false) {
        NoticeListScreen(
            state = NoticeListUiState.Error,
            notificationWarning = false,
            onNoticeClick = {}, onRetry = {}, onMarkAllRead = {}, onOpenNotificationSettings = {},
        )
    }
}

@Preview(name = "Notices loading - Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Notices loading - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NoticeLoadingPreview() {
    StayLeafyTheme(dynamicColor = false) {
        NoticeListScreen(
            state = NoticeListUiState.Loading,
            notificationWarning = false,
            onNoticeClick = {}, onRetry = {}, onMarkAllRead = {}, onOpenNotificationSettings = {},
        )
    }
}

@Preview(name = "Notice detail - Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Notice detail - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NoticeDetailPreview() {
    StayLeafyTheme(dynamicColor = false) {
        NoticeDetailScreen(
            state = NoticeDetailUiState.Content(previewNotice),
            onBack = {}, onRetry = {}, onViewPlant = {},
        )
    }
}

@Preview(name = "Notice not found - Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Notice not found - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NoticeNotFoundPreview() {
    StayLeafyTheme(dynamicColor = false) {
        NoticeDetailScreen(
            state = NoticeDetailUiState.NotFound,
            onBack = {}, onRetry = {}, onViewPlant = {},
        )
    }
}
